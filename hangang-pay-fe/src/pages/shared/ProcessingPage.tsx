import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { executePayment, recoverPayment, type PaymentResult } from '@/api/payment'
import { createChargeIntent, executeCharge } from '@/api/charge'
import { createExchangeIntent, executeExchange } from '@/api/exchange'
import { registerUser, registerMerchant } from '@/api/auth'
import { ApiError } from '@/api/client'
import { ApiErrorCode } from '@/api/errorCodes'
import { ProcessingView, RetryState } from '@/components/common'

type FlowState = Record<string, unknown>

interface FlowConfig {
  title: string
  caption?: (state: FlowState) => string | undefined
  completePath: string
  pinPath?: string
  errorPath: string | ((state: FlowState) => string)
  defaultError: string
  run: (state: FlowState) => Promise<unknown>
  // 결과 status를 추출하는 flow만 UNKNOWN(미확정) 재시도 단계를 지원한다.
  resolveStatus?: (result: unknown) => string
  // UNKNOWN일 때 "다시 확인"이 호출하는 복구(recover) API.
  recover?: (state: FlowState) => Promise<unknown>
  buildErrorState?: (state: FlowState, message: string) => Record<string, unknown>
  onComplete?: (queryClient: QueryClient) => void
}

function invalidateUserTransactionQueries(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: ['wallet', 'balance'] })
  void queryClient.invalidateQueries({ queryKey: ['users', 'recent-histories'] })
  void queryClient.invalidateQueries({ queryKey: ['users', 'histories'] })
}

function isPinRetryableError(code?: string) {
  return code === ApiErrorCode.INVALID_PAYMENT_PIN || code === ApiErrorCode.INVALID_PIN_NUMBER
}

const FLOWS: Record<string, FlowConfig> = {
  '/pay/processing': {
    title: '결제를 처리하고 있어요',
    caption: (s) => s.merchantName as string | undefined,
    completePath: '/pay/complete',
    pinPath: '/pay/pin',
    errorPath: (s) => (s.merchantId ? `/pay/amount/${s.merchantId}` : '/pay/confirm'),
    defaultError: '결제 처리 중 오류가 발생했습니다.',
    run: (state) => executePayment(state.transactionUuid as string, state.pin as string),
    // 200 UNKNOWN(미확정)은 throw되지 않으므로 status로 분기한다. FAILED는 BE가 4xx로 throw → catch 처리.
    resolveStatus: (result) => (result as PaymentResult).status,
    recover: (state) => recoverPayment(state.transactionUuid as string),
    onComplete: (queryClient) => {
      invalidateUserTransactionQueries(queryClient)
    },
  },
  '/charge/processing': {
    title: '충전을 처리하고 있어요',
    completePath: '/charge/complete',
    pinPath: '/charge/pin',
    errorPath: '/charge/amount',
    defaultError: '충전 처리 중 오류가 발생했습니다.',
    async run(state) {
      // 1) intent 생성(PENDING 커밋, PIN 없음) → 2) 실행(PIN). bank 실패 시 intent가 남아 복구된다.
      // transactionUuid는 서버가 intent 응답으로 발급한 값을 그대로 execute에 사용한다.
      const intent = await createChargeIntent({
        institutionId: state.institutionId as number,
        accountId: state.accountId as number,
        amount: state.amount as number,
      })
      return executeCharge(intent.transactionUuid, state.pin as string)
    },
    onComplete: (queryClient) => {
      invalidateUserTransactionQueries(queryClient)
      void queryClient.invalidateQueries({ queryKey: ['charge', 'init'] })
    },
  },
  '/refund/processing': {
    title: '환불을 신청하고 있어요',
    completePath: '/refund/complete',
    pinPath: '/refund/pin',
    errorPath: '/refund/check',
    defaultError: '환불 처리 중 오류가 발생했습니다.',
    async run(state) {
      // 1) intent 생성(PENDING 커밋, PIN 없음) → 2) 실행(PIN). bank 실패 시 intent가 남아 복구된다.
      // transactionUuid는 서버가 intent 응답으로 발급한 값을 그대로 execute에 사용한다.
      const intent = await createExchangeIntent({
        amount: state.amount as number,
      })
      return executeExchange(intent.transactionUuid, state.pin as string)
    },
    onComplete: (queryClient) => {
      invalidateUserTransactionQueries(queryClient)
    },
  },
  '/register/processing': {
    title: '회원가입을 처리하고 있어요',
    completePath: '/register/complete',
    errorPath: '/register',
    defaultError: '회원가입 처리 중 오류가 발생했습니다.',
    run: (state) =>
      registerUser({
        name: state.name as string,
        phoneNumber: state.phoneNumber as string,
        password: state.password as string,
        paymentPin: state.paymentPin as string,
        institutionId: state.institutionId as number,
        accountNumber: state.accountNumber as string,
      }),
    buildErrorState: (state, message) => ({ ...state, error: message }),
  },
  '/merchant/register/processing': {
    title: '회원가입을 처리하고 있어요',
    completePath: '/merchant/register/complete',
    errorPath: '/merchant/register/form',
    defaultError: '회원가입 처리 중 오류가 발생했습니다.',
    run: (state) =>
      registerMerchant({
        businessNumber: state.businessNumber as string,
        username: state.phoneNumber as string,
        password: state.password as string,
        paymentPin: state.paymentPin as string,
        institutionId: state.institutionId as number,
        accountNumber: state.accountNumber as string,
        phoneNumber: state.phoneNumber as string,
      }),
    buildErrorState: (state, message) => ({ ...state, error: message }),
  },
}

export function ProcessingPage() {
  const location = useLocation()
  const state = location.state as FlowState | null
  const pageKey =
    typeof state?.submitToken === 'string' && state.submitToken.length > 0
      ? state.submitToken
      : location.key

  return <ProcessingPageContent key={pageKey} />
}

function ProcessingPageContent() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const state = location.state as FlowState | null
  const calledRef = useRef(false)
  const flow = FLOWS[location.pathname]
  // UNKNOWN(미확정) 결과를 화면 내에 머무르게 하기 위한 상태
  const [unknownResult, setUnknownResult] = useState<unknown>(null)
  const [isRecovering, setIsRecovering] = useState(false)

  // 실패/에러 시 직전 화면으로 복귀하며 실패 사유 전달
  const goError = useCallback(
    (message: string) => {
      if (!flow) return
      const errorState = flow.buildErrorState
        ? flow.buildErrorState(state ?? {}, message)
        : { error: message, amount: state?.amount }
      const errorPath =
        typeof flow.errorPath === 'function' ? flow.errorPath(state ?? {}) : flow.errorPath
      navigate(errorPath, { state: errorState, replace: true })
    },
    [flow, navigate, state]
  )

  // run/recover 결과를 status에 따라 분기 (resolveStatus 미지원 flow는 무조건 완료)
  const applyResult = useCallback(
    (result: unknown) => {
      if (!flow) return
      const status = flow.resolveStatus?.(result)
      if (!flow.resolveStatus || status === 'SUCCESS') {
        flow.onComplete?.(queryClient)
        navigate(flow.completePath, { state: result, replace: true })
        return
      }
      if (status === 'FAILED') {
        goError('결제가 실패로 확정되었습니다.')
        return
      }
      // UNKNOWN / PROCESSING → 재시도 단계 유지
      setUnknownResult(result)
    },
    [flow, navigate, queryClient, goError]
  )

  const handleError = useCallback(
    (err: unknown) => {
      const message =
        err instanceof ApiError ? err.message : (flow?.defaultError ?? '오류가 발생했습니다.')
      if (err instanceof ApiError && isPinRetryableError(err.code) && flow?.pinPath) {
        navigate(flow.pinPath, { state: { ...(state ?? {}), error: message }, replace: true })
        return
      }
      goError(message)
    },
    [flow, goError, navigate, state]
  )

  useEffect(() => {
    if (!state || !flow || calledRef.current) return
    calledRef.current = true
    flow.run(state).then(applyResult).catch(handleError)
  }, [state, flow, applyResult, handleError])

  // UNKNOWN 단계에서 "다시 확인" → recover 호출 후 동일 분기 재사용
  const handleRecover = () => {
    if (!flow?.recover || !state || isRecovering) return
    setIsRecovering(true)
    setUnknownResult(null)
    flow
      .recover(state)
      .then(applyResult)
      // recover 자체가 실패(은행 서버 응답 없음 등)하면 화면을 이동하지 않고
      // 재시도 화면(UNKNOWN)에 그대로 머무른다.
      .catch(() => setUnknownResult(true))
      .finally(() => setIsRecovering(false))
  }

  // recover 대기 중
  if (isRecovering) {
    return (
      <div className="h-dvh bg-background">
        <ProcessingView
          title="결제 상태를 확인하고 있어요"
          amount={state?.amount as number | undefined}
        />
      </div>
    )
  }

  // UNKNOWN(미확정) 재시도 화면
  if (unknownResult) {
    return (
      <div className="h-dvh bg-background px-5">
        <RetryState
          title="결제 상태를 확인 중이에요"
          description={'결과를 다시 확인해주세요.\n같은 결제가 중복 처리되지는 않습니다.'}
          primaryText="다시 확인"
          onPrimary={handleRecover}
          secondaryText="홈으로"
          onSecondary={() => navigate('/home', { replace: true })}
        />
      </div>
    )
  }

  return (
    <div className="h-dvh bg-background">
      <ProcessingView
        title={flow?.title ?? '처리하고 있어요'}
        amount={state?.amount as number | undefined}
        caption={state ? flow?.caption?.(state) : undefined}
      />
    </div>
  )
}
