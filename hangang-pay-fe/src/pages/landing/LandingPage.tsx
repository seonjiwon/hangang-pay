import { useNavigate } from 'react-router-dom'
import { Button, HangangPayLogo } from '@/components/common'

function WalletIllustration() {
  return (
    <div style={{ animation: 'float 3.2s ease-in-out infinite' }}>
      <svg
        width="260"
        height="220"
        viewBox="0 0 260 220"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden="true"
      >
        <defs>
          <filter id="walletShadowFilter" x="-20%" y="-20%" width="140%" height="140%">
            <feDropShadow
              dx="0"
              dy="8"
              stdDeviation="12"
              floodColor="#1d4ed8"
              floodOpacity="0.18"
            />
          </filter>
          <filter id="cardShadowFilter" x="-20%" y="-20%" width="140%" height="140%">
            <feDropShadow dx="0" dy="6" stdDeviation="10" floodColor="#1d4ed8" floodOpacity="0.3" />
          </filter>
          <linearGradient id="cardGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#2563eb" />
            <stop offset="100%" stopColor="#1d4ed8" />
          </linearGradient>
          <linearGradient id="cardShine" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="white" stopOpacity="0" />
            <stop offset="50%" stopColor="white" stopOpacity="0.18" />
            <stop offset="100%" stopColor="white" stopOpacity="0" />
          </linearGradient>
          <linearGradient id="walletGrad" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stopColor="#ffffff" />
            <stop offset="100%" stopColor="#f0f4ff" />
          </linearGradient>
          <clipPath id="cardClip">
            <rect x="46" y="22" width="168" height="108" rx="14" />
          </clipPath>
          <clipPath id="walletClip">
            <rect x="18" y="100" width="224" height="104" rx="20" />
          </clipPath>
        </defs>

        {/* Ground shadow */}
        <ellipse cx="130" cy="212" rx="80" ry="8" fill="#1d4ed8" opacity="0.1" />

        {/* Wallet body */}
        <rect
          x="18"
          y="100"
          width="224"
          height="104"
          rx="20"
          fill="url(#walletGrad)"
          filter="url(#walletShadowFilter)"
        />

        {/* Wallet interior stripe */}
        <rect x="18" y="130" width="224" height="2" fill="#dbeafe" opacity="0.8" />

        {/* Wallet clasp button */}
        <circle cx="214" cy="152" r="16" fill="#e8eef8" />
        <circle cx="214" cy="152" r="10" fill="#dbeafe" />
        <circle cx="214" cy="152" r="5" fill="#bfdbfe" />

        {/* Card (sits behind wallet top edge, peeking out) */}
        <rect
          x="46"
          y="22"
          width="168"
          height="108"
          rx="14"
          fill="url(#cardGrad)"
          filter="url(#cardShadowFilter)"
        />

        {/* Card shine overlay */}
        <rect x="46" y="22" width="168" height="108" rx="14" fill="url(#cardShine)" />

        {/* Shimmer sweep */}
        <rect
          x="46"
          y="22"
          width="168"
          height="108"
          rx="14"
          fill="url(#cardShine)"
          clipPath="url(#cardClip)"
          style={{ animation: 'shimmer 2.8s ease-in-out infinite' }}
        />

        {/* Card chip */}
        <rect x="68" y="42" width="30" height="22" rx="4" fill="#60a5fa" opacity="0.9" />
        <rect x="68" y="42" width="30" height="22" rx="4" fill="url(#cardShine)" />

        {/* Card H logo */}
        {/* Left pillar */}
        <rect x="140" y="40" width="8" height="22" rx="4" fill="white" opacity="0.85" />
        {/* Right pillar */}
        <rect x="160" y="40" width="8" height="22" rx="4" fill="white" opacity="0.85" />
        {/* Wave 1 */}
        <path
          d="M138 54 C142 50 148 56 154 54 C160 52 166 56 170 54"
          stroke="white"
          strokeWidth="2.5"
          strokeLinecap="round"
          opacity="0.85"
        />
        {/* Wave 2 */}
        <path
          d="M138 60 C142 56 148 62 154 60 C160 58 166 62 170 60"
          stroke="white"
          strokeWidth="2"
          strokeLinecap="round"
          opacity="0.55"
        />

        {/* Card number dots */}
        <circle cx="68" cy="90" r="3" fill="white" opacity="0.5" />
        <circle cx="78" cy="90" r="3" fill="white" opacity="0.5" />
        <circle cx="88" cy="90" r="3" fill="white" opacity="0.5" />
        <circle cx="98" cy="90" r="3" fill="white" opacity="0.5" />
        <circle cx="112" cy="90" r="3" fill="white" opacity="0.5" />
        <circle cx="122" cy="90" r="3" fill="white" opacity="0.5" />
        <circle cx="132" cy="90" r="3" fill="white" opacity="0.5" />
        <circle cx="142" cy="90" r="3" fill="white" opacity="0.5" />

        {/* Wallet left accent */}
        <rect x="38" y="140" width="60" height="6" rx="3" fill="#dbeafe" />
        <rect x="38" y="153" width="40" height="5" rx="2.5" fill="#e8eef8" />
      </svg>
    </div>
  )
}

export function LandingPage() {
  const navigate = useNavigate()

  return (
    <div className="flex h-full flex-col items-center justify-between bg-background px-6 py-10">
      {/* 로고 + 앱명 */}
      <div className="flex flex-col items-center gap-3 pt-6">
        <div className="flex size-16 items-center justify-center rounded-2xl bg-primary/10 shadow-sm">
          <HangangPayLogo size={40} />
        </div>
        <div className="text-center">
          <h1 className="text-3xl font-bold tracking-tight text-foreground">한강페이</h1>
          <p className="mt-1 text-sm text-muted-foreground">더 스마트한 지역화폐</p>
        </div>
      </div>

      {/* 지갑 일러스트 */}
      <div className="flex flex-1 items-center justify-center">
        <WalletIllustration />
      </div>

      {/* 버튼 영역 */}
      <div className="flex w-full flex-col gap-3">
        <Button size="lg" variant="primary" onClick={() => navigate('/login')}>
          로그인
        </Button>
        <Button
          size="lg"
          variant="secondary"
          className="border border-primary/20 bg-card text-primary hover:bg-accent"
          onClick={() => navigate('/register')}
        >
          사용자 회원가입
        </Button>
        <button
          type="button"
          className="py-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
          onClick={() => navigate('/merchant/register/business')}
        >
          기맹점 회원가입
        </button>
      </div>
    </div>
  )
}
