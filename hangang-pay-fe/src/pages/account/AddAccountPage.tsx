import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  AppShell,
  Button,
  PageHeader,
  SelectField,
  TextField,
  Toast,
  type ToastState,
} from '@/components/common'
import { addAccount } from '@/api/accounts'
import { ApiError } from '@/api/client'
import { apiErrorMessages, isApiErrorCode } from '@/api/errorCodes'
import { useCurrentUser } from '@/auth/useCurrentUser'
import { resolveBackDestination } from '@/lib/navigation'

const BANK_OPTIONS = [
  { label: '우리은행', value: 'WR', institutionId: 2 },
  { label: '신한은행', value: 'SH', institutionId: 3 },
  { label: '하나은행', value: 'HN', institutionId: 4 },
]

const SECTION_TITLE_CLASS = 'mb-3 text-base font-bold text-foreground'
const HIDDEN_FIELD_LABEL_CLASS = 'space-y-0 [&>span:first-child]:sr-only'

interface AddAccountSubmitPayload {
  bank: string
  accountNumber: string
}

interface AddAccountPageProps {
  onBack?: () => void
  onSubmit?: (payload: AddAccountSubmitPayload) => void
}

function buildErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code && isApiErrorCode(error.code)) return apiErrorMessages[error.code]
    return error.message
  }

  if (error instanceof Error) return error.message

  return '요청에 실패했습니다. 네트워크 연결을 확인해 주세요.'
}

function onlyDigits(value: string) {
  return value.replace(/\D/g, '')
}

export function AddAccountPage({ onBack, onSubmit }: AddAccountPageProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const { isAuthenticated, isLoading: isAuthLoading } = useCurrentUser()
  const [selectedBank, setSelectedBank] = useState('')
  const [accountNumber, setAccountNumber] = useState('')
  const [toast, setToast] = useState<ToastState | null>(null)

  const selectedBankOption = BANK_OPTIONS.find((option) => option.value === selectedBank)

  const canSubmit =
    isAuthenticated && !isAuthLoading && selectedBank.length > 0 && accountNumber.length > 0

  const addAccountMutation = useMutation({
    mutationFn: async () => {
      if (!selectedBankOption) throw new Error('은행을 선택해주세요.')

      return await addAccount({
        institutionCode: selectedBankOption.value,
        accountNumber,
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      navigate('/mypage/accounts', { replace: true })
    },
    onError: (error) => {
      setToast({ message: buildErrorMessage(error), variant: 'error' })
    },
  })

  const isSubmitting = isAuthLoading || addAccountMutation.isPending

  useEffect(() => {
    if (!toast) return

    const timeoutId = window.setTimeout(() => {
      setToast(null)
    }, 3000)

    return () => window.clearTimeout(timeoutId)
  }, [toast])

  const handleBack = () => {
    if (onBack) {
      onBack()
      return
    }

    navigate(resolveBackDestination(location.pathname, location.state), { replace: true })
  }

  const handleAccountNumberChange = (value: string) => {
    setAccountNumber(onlyDigits(value))
  }

  const handleBankChange = (value: string) => {
    setSelectedBank(value)
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!canSubmit || !selectedBankOption) return

    onSubmit?.({
      bank: selectedBankOption.label,
      accountNumber,
    })

    addAccountMutation.mutate()
  }

  return (
    <AppShell>
      <form className="flex min-h-0 flex-1 flex-col" onSubmit={handleSubmit}>
        <PageHeader title="계좌 추가" onBack={handleBack} />

        <div className="min-h-0 flex-1 overflow-y-auto overflow-x-hidden">
          <section className="py-5" aria-labelledby="bank-selection-title">
            <h2 id="bank-selection-title" className={SECTION_TITLE_CLASS}>
              은행 선택
            </h2>
            <SelectField
              label="은행 선택"
              value={selectedBank}
              options={BANK_OPTIONS}
              onChange={handleBankChange}
              placeholder="은행을 선택해주세요"
              className={HIDDEN_FIELD_LABEL_CLASS}
            />
          </section>

          <section className="py-6" aria-labelledby="account-info-title">
            <h2 id="account-info-title" className={SECTION_TITLE_CLASS}>
              계좌 정보 입력
            </h2>
            <TextField
              label="계좌번호"
              value={accountNumber}
              onChange={handleAccountNumberChange}
              type="tel"
              placeholder="'-' 없이 숫자만 입력해주세요"
              className={HIDDEN_FIELD_LABEL_CLASS}
            />
          </section>
        </div>

        <footer className="shrink-0 pt-3 pb-[calc(env(safe-area-inset-bottom)+0.25rem)]">
          <Button type="submit" size="lg" disabled={!canSubmit || isSubmitting}>
            {addAccountMutation.isPending ? '등록 중' : '확인'}
          </Button>
        </footer>
      </form>

      <Toast open={toast !== null} message={toast?.message ?? ''} variant={toast?.variant} />
    </AppShell>
  )
}
