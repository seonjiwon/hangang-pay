import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { PinEntry } from '@/components/common'

const PIN_LENGTH = 6

type Step = 'enter' | 'confirm'

export function RegisterPinPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const isMerchant = location.pathname.startsWith('/merchant/')
  const state = location.state as Record<string, unknown> | null

  const [step, setStep] = useState<Step>('enter')
  const [firstPin, setFirstPin] = useState('')
  const [confirmPin, setConfirmPin] = useState('')
  const [errorMessage, setErrorMessage] = useState('')

  function handleFirstPinChange(pin: string) {
    setFirstPin(pin)
    if (pin.length === PIN_LENGTH) {
      setStep('confirm')
      setConfirmPin('')
      setErrorMessage('')
    }
  }

  function handleConfirmPinChange(pin: string) {
    setConfirmPin(pin)
    if (pin.length === PIN_LENGTH) {
      if (pin === firstPin) {
        const nextPath = isMerchant ? '/merchant/register/processing' : '/register/processing'
        navigate(nextPath, { state: { ...state, paymentPin: pin } })
      } else {
        setErrorMessage('PIN 번호가 일치하지 않습니다. 다시 입력해주세요.')
        setStep('enter')
        setFirstPin('')
        setConfirmPin('')
      }
    }
  }

  function handleBack() {
    if (step === 'confirm') {
      setStep('enter')
      setFirstPin('')
      setConfirmPin('')
      setErrorMessage('')
    } else {
      const backPath = isMerchant ? '/merchant/register/form' : '/register'
      navigate(backPath, { state })
    }
  }

  const currentPin = step === 'enter' ? firstPin : confirmPin

  return (
    <div className="flex h-dvh flex-col bg-background">
      <div className="px-5 pt-2">
        <div className="flex items-center gap-2 pb-3">
          <button
            type="button"
            aria-label="뒤로가기"
            onClick={handleBack}
            className="-ml-2 flex h-9 w-9 items-center justify-center rounded-lg text-foreground hover:bg-muted"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="h-6 w-6"
              aria-hidden
            >
              <path d="m15 18-6-6 6-6" />
            </svg>
          </button>
          <h1 className="text-2xl font-bold text-foreground">
            {isMerchant ? '가맹점 회원가입' : '사용자 회원가입'}
          </h1>
        </div>
      </div>

      {errorMessage && (
        <p className="px-5 py-2 text-center text-sm font-medium text-destructive">{errorMessage}</p>
      )}

      <PinEntry
        pin={currentPin}
        length={PIN_LENGTH}
        onChange={step === 'enter' ? handleFirstPinChange : handleConfirmPinChange}
        title={step === 'enter' ? 'PIN 번호를 입력해주세요' : 'PIN 번호를 다시 입력해주세요'}
        subtitle={
          step === 'enter'
            ? '결제 및 민감 작업에 사용할 PIN을 설정해주세요.'
            : '앞서 입력한 PIN과 동일하게 입력해주세요.'
        }
      />
    </div>
  )
}
