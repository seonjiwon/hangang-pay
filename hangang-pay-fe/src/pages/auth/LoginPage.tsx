import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft } from 'lucide-react'
import { loginMerchant, loginUser } from '@/api/auth'
import { ApiError } from '@/api/client'
import { AppShell, Button, HangangPayLogo, TextField } from '@/components/common'
import { cn } from '@/lib/utils'

type Tab = 'user' | 'merchant'

export function LoginPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [tab, setTab] = useState<Tab>('user')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [businessNumber, setBusinessNumber] = useState('')
  const [password, setPassword] = useState('')
  const [errorMessage, setErrorMessage] = useState('')

  const mutation = useMutation({
    mutationFn: () =>
      tab === 'user' ? loginUser(phoneNumber, password) : loginMerchant(businessNumber, password),
    onSuccess: (data) => {
      localStorage.setItem('role', data.role)
      queryClient.setQueryData(['currentUser'], {
        id: data.principalId,
        name: '',
        role: data.role,
      })
      navigate(data.role === 'MERCHANT' ? '/merchant/home' : '/home', { replace: true })
    },
    onError: (err) => {
      setErrorMessage(err instanceof ApiError ? err.message : '로그인에 실패했습니다.')
    },
  })

  function handleSubmit() {
    setErrorMessage('')
    mutation.mutate()
  }

  function handleTabChange(next: Tab) {
    setTab(next)
    setErrorMessage('')
  }

  return (
    <AppShell>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          handleSubmit()
        }}
        className="flex h-full flex-col"
      >
        <button
          type="button"
          aria-label="뒤로가기"
          onClick={() => navigate('/')}
          className="-ml-2 flex h-9 w-9 items-center justify-center rounded-lg text-muted-foreground hover:bg-muted"
        >
          <ChevronLeft className="h-5 w-5" aria-hidden />
        </button>

        <div className="flex-1 overflow-y-auto">
          {/* 브랜드 영역: 로고 + 서비스명 + 슬로건 */}
          <div className="flex flex-col items-center gap-3 pb-10 pt-6">
            <div className="flex size-16 items-center justify-center rounded-2xl bg-primary/10 shadow-sm">
              <HangangPayLogo size={40} />
            </div>
            <div className="text-center">
              <h1 className="text-3xl font-bold tracking-tight text-foreground">한강페이</h1>
              <p className="mt-1 text-sm text-muted-foreground">더 스마트한 지역화폐</p>
            </div>
          </div>

          {/* 사용자 / 가맹점 세그먼트 탭 */}
          <div className="flex rounded-xl bg-muted p-1">
            {(['user', 'merchant'] as const).map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => handleTabChange(t)}
                className={cn(
                  'flex-1 rounded-lg py-2.5 text-sm font-semibold transition-colors',
                  tab === t ? 'bg-card text-primary' : 'text-muted-foreground'
                )}
              >
                {t === 'user' ? '사용자' : '가맹점'}
              </button>
            ))}
          </div>

          {/* 입력 영역 */}
          <div className="mt-6 space-y-4">
            {tab === 'user' ? (
              <div className="space-y-1">
                <TextField
                  label="휴대폰 번호"
                  value={phoneNumber}
                  onChange={setPhoneNumber}
                  type="tel"
                  placeholder="01012345678"
                  inputClassName="h-14 rounded-2xl"
                />
                <p className="text-xs text-muted-foreground">'-' 없이 숫자만 입력해주세요</p>
              </div>
            ) : (
              <div className="space-y-1">
                <TextField
                  label="사업자번호"
                  value={businessNumber}
                  onChange={setBusinessNumber}
                  type="tel"
                  placeholder="1234567890"
                  inputClassName="h-14 rounded-2xl"
                />
                <p className="text-xs text-muted-foreground">'-' 없이 숫자만 입력해주세요</p>
              </div>
            )}

            <TextField
              label="비밀번호"
              value={password}
              onChange={setPassword}
              type="password"
              placeholder="비밀번호를 입력하세요"
              inputClassName="h-14 rounded-2xl"
            />
          </div>

          {/* 비밀번호 찾기 */}
          <div className="mt-3 text-right">
            <button type="button" className="text-sm text-muted-foreground">
              비밀번호 찾기
            </button>
          </div>
        </div>

        <div className="pt-4">
          {errorMessage ? (
            <p className="mb-3 text-center text-sm font-medium text-destructive">{errorMessage}</p>
          ) : null}
          <Button
            type="submit"
            size="lg"
            className="h-14 rounded-2xl"
            disabled={mutation.isPending}
          >
            {mutation.isPending ? '로그인 중...' : '로그인'}
          </Button>

          <p className="mt-4 text-center text-sm text-muted-foreground">
            계정이 없으신가요?{' '}
            <button
              type="button"
              onClick={() => navigate(tab == 'user' ? '/register' : '/merchant/register/business')}
              className="font-semibold text-primary"
            >
              회원가입
            </button>
          </p>
        </div>
      </form>
    </AppShell>
  )
}
