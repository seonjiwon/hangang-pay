// k6 공통 설정. 모든 스크립트가 여기서 BASE_URL과 엔드포인트를 가져온다.
//
// 대상 주소는 실행 시 환경변수로 주입한다. 측정할 땐 CloudFront/WAF를 우회해
// be(EC2)에 직접 쏜다 (엣지 rate-limit/변동성 제거):
//   k6 run -e BASE_URL=http://<tailscale-ip>:8080 hash-load.js
//
// 미지정 시 로컬 스모크용 기본값을 쓴다.
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 자주 쓰는 엔드포인트 경로 (be REST prefix: /api/v1)
export const ENDPOINTS = {
  // 1. 소비자 로그인 -> 성공 시 세션 쿠키 발급 (k6 쿠키 jar가 이후 요청에 자동 첨부)
  loginUser: '/api/v1/auth/users/login',
  // 2. 충전 intent 생성 -> 요청 해시(SHA-256)를 계산하는 지점. bank/besu는 호출하지 않음(BE-local)
  chargeIntents: '/api/v1/charge/intents',
  // 3. 결제 intent 생성 -> payer wallet 비관락(v0) / Redis 락(v1) 지점. 실행은 /api/v1/payment/{uuid}/execute (동적 경로)
  paymentIntents: '/api/v1/payment/intents',
};

// JSON 요청 공통 헤더
export const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };
