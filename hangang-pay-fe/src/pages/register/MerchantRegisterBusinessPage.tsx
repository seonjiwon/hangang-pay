import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { getBusinessInfo } from '@/api/auth'
import { ApiError } from '@/api/client'
import {
  AppShell,
  BackTitleHeader,
  Button,
  TextField,
  Toast,
  type ToastState,
} from '@/components/common'

function stripHyphens(value: string) {
  return value.replace(/-/g, '')
}

export function MerchantRegisterBusinessPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as Record<string, unknown> | null

  const [businessNumber, setBusinessNumber] = useState('')
  const [verified, setVerified] = useState<{
    merchantName: string
    ownerName: string
    address: string
    businessType: string
  } | null>(null)
  const [toast, setToast] = useState<ToastState | null>(null)

  const checkMutation = useMutation({
    mutationFn: () => getBusinessInfo(stripHyphens(businessNumber)),
    onSuccess: (data) => {
      setVerified({
        merchantName: data.merchantName,
        ownerName: data.ownerName,
        address: data.address,
        businessType: data.businessType,
      })
      setToast({ message: '사업자 정보가 확인되었습니다.', variant: 'success' })
    },
    onError: (err) => {
      setVerified(null)
      setToast({
        message: err instanceof ApiError ? err.message : '사업자 정보를 찾을 수 없습니다.',
        variant: 'error',
      })
    },
  })

  function handleBusinessNumberChange(value: string) {
    setBusinessNumber(value)
    setVerified(null)
  }

  function handleNext() {
    if (!verified) return
    navigate('/merchant/register/form', {
      state: { ...state, businessNumber: stripHyphens(businessNumber) },
    })
  }

  const infoFields = [
    { label: '상호명', value: verified?.merchantName ?? '' },
    { label: '대표자명', value: verified?.ownerName ?? '' },
    { label: '사업장 주소', value: verified?.address ?? '' },
    { label: '업종', value: verified?.businessType ?? '' },
  ]

  return (
    <AppShell>
      <div className="flex h-full flex-col">
        <BackTitleHeader title="가맹점 회원가입" onBack={() => navigate('/')} />

        <div className="flex-1 overflow-y-auto">
          <div className="mb-6">
            <h2 className="text-2xl font-bold text-foreground">사업자 정보를 입력해주세요</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              사업자등록증에 기재된 정보를 정확히 입력해주세요.
            </p>
          </div>

          <div className="space-y-4">
            <div>
              <span className="mb-1.5 block text-sm font-semibold text-foreground">
                사업자등록번호
              </span>
              <div className="flex gap-2">
                <TextField
                  label="사업자등록번호"
                  value={businessNumber}
                  onChange={handleBusinessNumberChange}
                  placeholder="000-00-00000"
                  className="flex-1 space-y-0 [&>span:first-child]:sr-only"
                />
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => checkMutation.mutate()}
                  disabled={!businessNumber.trim() || checkMutation.isPending}
                  className="h-12 w-auto shrink-0 whitespace-nowrap self-start px-4"
                >
                  {checkMutation.isPending ? '조회 중' : '사업자 확인'}
                </Button>
              </div>
              <p className="mt-1 text-xs text-muted-foreground">
                사업자등록증에 기재된 번호를 입력하세요.
              </p>
            </div>

            {infoFields.map(({ label, value }) => (
              <TextField
                key={label}
                label={label}
                value={value}
                onChange={() => {}}
                placeholder="사업자 확인 후 자동 입력됩니다"
                disabled={!verified}
              />
            ))}
          </div>
        </div>

        <div className="pt-3 pb-[calc(env(safe-area-inset-bottom)+0.25rem)]">
          <Button size="lg" disabled={!verified} onClick={handleNext}>
            다음
          </Button>
        </div>
      </div>

      <Toast open={toast !== null} message={toast?.message ?? ''} variant={toast?.variant} />
    </AppShell>
  )
}
