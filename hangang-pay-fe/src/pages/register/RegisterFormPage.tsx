import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { AppShell, BackTitleHeader, Button, SelectField, TextField } from '@/components/common'

const BANK_OPTIONS = [
  { label: '우리은행', value: 'WR', institutionId: 2 },
  { label: '신한은행', value: 'SH', institutionId: 3 },
  { label: '하나은행', value: 'HN', institutionId: 4 },
]

const PASSWORD_RULES = [
  { label: '영문 포함', test: (pw: string) => /[a-zA-Z]/.test(pw) },
  { label: '숫자 포함', test: (pw: string) => /\d/.test(pw) },
  { label: '특수문자 포함 (!@#$%^&*)', test: (pw: string) => /[!@#$%^&*]/.test(pw) },
  { label: '8자 이상', test: (pw: string) => pw.length >= 8 },
]

function onlyDigits(value: string) {
  return value.replace(/\D/g, '')
}

// 소비자/가맹점 공통 단일 회원가입 폼.
// 로그인 정보(이름·전화·비밀번호)와 연결 계좌(은행·계좌번호)를 한 화면에서 입력한다.
// 가맹점은 앞선 사업자번호 조회에서 넘어온 state에 정보를 얹고, username은 전화번호를 사용한다.
export function RegisterFormPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const isMerchant = location.pathname.startsWith('/merchant/')
  const state = location.state as Record<string, unknown> | null

  const [name, setName] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [selectedBank, setSelectedBank] = useState('')
  const [accountNumber, setAccountNumber] = useState('')

  const selectedBankOption = BANK_OPTIONS.find((o) => o.value === selectedBank)
  const allRulesMet = PASSWORD_RULES.every((r) => r.test(password))
  const confirmError =
    confirm.length > 0 && password !== confirm ? '비밀번호가 일치하지 않습니다.' : ''
  const phoneError =
    phoneNumber.length > 0 && phoneNumber.length < 10 ? '올바른 휴대폰 번호를 입력해주세요' : ''

  const canNext =
    (isMerchant || name.trim().length > 0) &&
    phoneNumber.length >= 10 &&
    allRulesMet &&
    confirm.length > 0 &&
    confirmError === '' &&
    selectedBank.length > 0 &&
    accountNumber.length > 0

  function handleNext() {
    if (!canNext || !selectedBankOption) return
    const nextPath = isMerchant ? '/merchant/register/pin' : '/register/pin'
    navigate(nextPath, {
      state: {
        ...state,
        ...(isMerchant ? {} : { name: name.trim() }),
        phoneNumber,
        password,
        institutionId: selectedBankOption.institutionId,
        accountNumber,
      },
    })
  }

  return (
    <AppShell>
      <div className="flex h-full flex-col">
        <BackTitleHeader
          title={isMerchant ? '가맹점 회원가입' : '사용자 회원가입'}
          onBack={() => navigate(isMerchant ? '/merchant/register/business' : '/', { state })}
        />

        <div className="min-h-0 flex-1 overflow-y-auto">
          <div className="mb-6">
            <h2 className="text-2xl font-bold text-foreground">회원 정보 입력</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {isMerchant
                ? '로그인 정보와 정산 계좌를 입력해주세요.'
                : '로그인 정보와 충전/환불에 사용할 계좌를 입력해주세요.'}
            </p>
          </div>

          <div className="space-y-4">
            {!isMerchant && (
              <TextField
                label="이름"
                value={name}
                onChange={setName}
                placeholder="이름을 입력하세요"
              />
            )}

            <TextField
              label="휴대폰 번호"
              value={phoneNumber}
              onChange={(v) => setPhoneNumber(onlyDigits(v))}
              type="tel"
              placeholder="01012345678"
              error={phoneError || undefined}
            />

            <TextField
              label="비밀번호"
              value={password}
              onChange={setPassword}
              type="password"
              placeholder="영문, 숫자, 특수문자 포함 8자 이상"
            />
            <TextField
              label="비밀번호 확인"
              value={confirm}
              onChange={setConfirm}
              type="password"
              placeholder="비밀번호를 다시 입력하세요"
              error={confirmError || undefined}
            />

            <div className="rounded-xl bg-muted px-4 py-3">
              <p className="mb-2 text-sm font-semibold text-foreground">비밀번호 규칙</p>
              <ul className="space-y-1">
                {PASSWORD_RULES.map((rule) => (
                  <li
                    key={rule.label}
                    className={`flex items-center gap-2 text-sm ${password.length > 0 && rule.test(password) ? 'text-primary' : 'text-muted-foreground'}`}
                  >
                    <span className="inline-block h-1.5 w-1.5 rounded-full bg-current" />
                    {rule.label}
                  </li>
                ))}
              </ul>
            </div>

            <SelectField
              label="은행"
              value={selectedBank}
              options={BANK_OPTIONS}
              onChange={setSelectedBank}
              placeholder="은행을 선택해주세요"
            />
            <TextField
              label="계좌번호"
              value={accountNumber}
              onChange={(v) => setAccountNumber(onlyDigits(v))}
              type="tel"
              placeholder="'-' 없이 숫자만 입력해주세요"
            />
          </div>
        </div>

        <div className="shrink-0 pt-3 pb-[calc(env(safe-area-inset-bottom)+0.25rem)]">
          <Button size="lg" disabled={!canNext} onClick={handleNext}>
            다음
          </Button>
        </div>
      </div>
    </AppShell>
  )
}
