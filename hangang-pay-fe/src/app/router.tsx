/* eslint-disable react-refresh/only-export-components */
import {
  createBrowserRouter,
  Navigate,
  useLocation,
  useNavigate,
  useRouteError,
} from 'react-router-dom'
import { useEffect } from 'react'
import { useCurrentUser } from '@/auth/useCurrentUser'
import { FullscreenLayout, MainLayout } from '@/routes/layouts'
import { RequireAuth, RequireRole, RedirectIfAuth } from '@/routes/guards'
import { LoginPage } from '@/pages/auth/LoginPage'
import { RegisterFormPage } from '@/pages/register/RegisterFormPage'
import { RegisterPinPage } from '@/pages/register/RegisterPinPage'
import { MerchantRegisterBusinessPage } from '@/pages/register/MerchantRegisterBusinessPage'
import { UserHomePage } from '@/pages/user/UserHomePage'
import { UserMyPage } from '@/pages/user/UserMyPage'
import { UserHistoryPage } from '@/pages/history/UserHistoryPage'
import { UserHistoryDetailPage } from '@/pages/history/UserHistoryDetailPage'
import { UserPayScanPage } from '@/pages/payment/UserPayScanPage'
import { PayConfirmPage } from '@/pages/payment/PayConfirmPage'
import { PinPage } from '@/pages/shared/PinPage'
import { ProcessingPage } from '@/pages/shared/ProcessingPage'
import { CompletePage } from '@/pages/shared/CompletePage'
import { ChargeAmountPage } from '@/pages/charge/ChargeAmountPage'
import { RefundCheckPage } from '@/pages/refund/RefundCheckPage'
import { AccountManagementPage } from '@/pages/account/AccountManagementPage'
import { AddAccountPage } from '@/pages/account/AddAccountPage'
import { MerchantHomePage } from '@/pages/merchant/MerchantHomePage'
import { MerchantQrPage } from '@/pages/merchant/MerchantQrPage'
import { MerchantPaymentsPage } from '@/pages/merchant/MerchantPaymentsPage'
import { MerchantPaymentDetailPage } from '@/pages/merchant/MerchantPaymentDetailPage'
import { MerchantMyPage } from '@/pages/merchant/MerchantMyPage'
import { MerchantSettlementPage } from '@/pages/merchant/MerchantSettlementPage'
import { AppShell } from '@/components/common'
import { LandingPage } from '@/pages/landing/LandingPage'
import { MerchantSettlementHistoryPage } from '@/pages/merchant/MerchantSettlementHistoryPage'

// 미등록 경로 접근 시 경로 기반으로 해당 영역 홈으로 교체
function GoBack() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  useEffect(() => {
    const home = pathname.startsWith('/merchant/') ? '/merchant/home' : '/home'
    navigate(home, { replace: true })
  }, [pathname, navigate])
  return null
}

// 진입점(/) 에서 역할에 맞는 홈으로 리다이렉트, 미인증 시 시작화면 표시
function RoleRedirect() {
  const { role, isLoading, isAuthenticated } = useCurrentUser()
  if (isLoading) return null
  if (!isAuthenticated) return <LandingPage />
  return <Navigate to={role === 'MERCHANT' ? '/merchant/home' : '/home'} replace />
}

// 라우트 레벨 에러 fallback (예상치 못한 에러 전체 포착)
function RootErrorElement() {
  const error = useRouteError()
  const message = error instanceof Error ? error.message : '알 수 없는 오류가 발생했습니다.'
  return (
    <AppShell>
      <section className="flex h-full flex-col items-center justify-center gap-4 text-center">
        <p className="text-sm font-semibold text-destructive">{message}</p>
        <button
          type="button"
          className="text-sm font-semibold text-primary"
          onClick={() => window.location.replace('/')}
        >
          홈으로 돌아가기
        </button>
      </section>
    </AppShell>
  )
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RoleRedirect />,
    errorElement: <RootErrorElement />,
  },
  {
    element: <RedirectIfAuth />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterFormPage /> },
      { path: '/register/pin', element: <RegisterPinPage /> },
      { path: '/register/processing', element: <ProcessingPage /> },
      { path: '/register/complete', element: <CompletePage /> },
      { path: '/merchant/register/business', element: <MerchantRegisterBusinessPage /> },
      { path: '/merchant/register/form', element: <RegisterFormPage /> },
      { path: '/merchant/register/pin', element: <RegisterPinPage /> },
      { path: '/merchant/register/processing', element: <ProcessingPage /> },
      { path: '/merchant/register/complete', element: <CompletePage /> },
    ],
  },
  // /merchant 단축 진입점 (홈으로 리다이렉트)
  {
    path: '/merchant',
    element: <Navigate to="/merchant/home" replace />,
  },
  {
    element: <RequireAuth />,
    children: [
      // 소비자 화면 (USER 권한)
      {
        element: <RequireRole roles={['USER']} />,
        children: [
          // 하단 네비 있는 메인 레이아웃
          {
            element: <MainLayout navType="user" />,
            children: [
              { path: '/home', element: <UserHomePage /> },
              { path: '/mypage/payments', element: <UserHistoryPage /> },
              { path: '/mypage', element: <UserMyPage /> },
            ],
          },
          { path: '/mypage/accounts', element: <AccountManagementPage /> },
          { path: '/mypage/accounts/add', element: <AddAccountPage /> },
          // 결제 플로우 (하단 네비 없음)
          {
            element: <FullscreenLayout fullBleed />,
            children: [{ path: '/pay/scan', element: <UserPayScanPage /> }],
          },
          {
            element: <FullscreenLayout />,
            children: [
              { path: '/pay/amount/:merchantId', element: <PayConfirmPage /> },
              { path: '/pay/confirm', element: <PayConfirmPage /> },
              { path: '/pay/pin', element: <PinPage /> },
              { path: '/pay/processing', element: <ProcessingPage /> },
              { path: '/pay/complete', element: <CompletePage /> },
              { path: '/charge/amount', element: <ChargeAmountPage /> },
              { path: '/charge/pin', element: <PinPage /> },
              { path: '/charge/processing', element: <ProcessingPage /> },
              { path: '/charge/complete', element: <CompletePage /> },
              { path: '/mypage/history/charges/:id', element: <UserHistoryDetailPage /> },
              { path: '/mypage/history/exchanges/:id', element: <UserHistoryDetailPage /> },
              { path: '/mypage/history/payments/:id', element: <UserHistoryDetailPage /> },
              { path: '/refund/check', element: <RefundCheckPage /> },
              { path: '/refund/pin', element: <PinPage /> },
              { path: '/refund/processing', element: <ProcessingPage /> },
              { path: '/refund/complete', element: <CompletePage /> },
            ],
          },
        ],
      },
      // 가맹점 화면 (MERCHANT 권한)
      {
        element: <RequireRole roles={['MERCHANT']} />,
        children: [
          {
            element: <MainLayout navType="merchant" />,
            children: [
              { path: '/merchant/home', element: <MerchantHomePage /> },
              { path: '/merchant/qr', element: <MerchantQrPage /> },
              { path: '/merchant/payments', element: <MerchantPaymentsPage /> },
              { path: '/merchant/mypage', element: <MerchantMyPage /> },
              {
                path: '/merchant/settlements',
                element: <MerchantSettlementHistoryPage />,
              },
            ],
          },
          {
            element: <FullscreenLayout />,
            children: [
              {
                path: '/merchant/payments/:transactionId',
                element: <MerchantPaymentDetailPage />,
              },
              { path: '/merchant/settlement', element: <MerchantSettlementPage /> },
              { path: '/merchant/settlement/complete', element: <CompletePage /> },
              { path: '/merchant/payments/cancel/complete', element: <CompletePage /> },
            ],
          },
        ],
      },
    ],
  },
  // 미매칭 경로 → 이전 페이지 유지
  {
    path: '*',
    element: <GoBack />,
  },
])
