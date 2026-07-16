// 결제 처리량 부하 테스트 — v0(NOWAIT 비관적 DB 락) vs v1(거래 단위 Redis 분산 락).
//
// [핵심 질문] 동시성 제어를 DB 행락에서 Redis 분산락으로 바꾸면 처리량이 어디서 얼마나 벌어지나.
//   - v0: execute 가 payer wallet 을 NOWAIT 비관락으로 잡는다 -> "같은 유저"의 동시 결제만 직렬화.
//   - v1: execute 가 거래(uuid) 단위 Redis 락만 잡는다 -> 서로 다른 결제는 애초에 경합 안 함.
//   그래서 두 시나리오로 나눠 본다:
//   - MODE=broad : 300 유저 x 50 가맹점 랜덤. 서로 다른 payer 라 v0 지갑락 경합이 흩어진다.
//                  => v0 ~= v1 로 비슷하고, 진짜 병목은 DB 커넥션풀(10)/Redis 세션 트래픽에서 드러난다.
//   - MODE=hot   : 소수 유저(기본 1명)에 집중. 같은 payer 라 v0 지갑락에 몰려 409 폭주.
//                  => v0 성공 처리량 낮음 / v1 성공 처리량 높음. Redis 락의 우위가 드러나는 지점.
//
// [전제] bank 는 WireMock 으로 끊어둔다(BANK_BASE_URL=http://wiremock:8080). besu 지연 제거 -> be 락만 순수 관측.
// [시드] test/k6/register-users.js 로 소비자 300명을 API 회원가입(WireMock 에 bank-accounts/bank-wallets 스텁 필요).
//        가맹점은 기존 시드 + API 등록분 사용(아래 MERCHANT_PARTY_IDS). v0 락은 payer 지갑 단위라 가맹점 수는 처리량에 무관.
//
// [실행] 엣지 우회, be 직결:
//   MODE=broad:  k6 run -e BASE_URL=http://<hp-be>:8080 -e MODE=broad test/k6/payment-throughput.js
//   MODE=hot:    k6 run -e BASE_URL=http://<hp-be>:8080 -e MODE=hot   test/k6/payment-throughput.js
//
// [해석] payment_success(초당=처리량), payment_rejected(409=락 경합), payment_execute_ms p95,
//        + Grafana app overview: HikariCP active/pending, Redis 명령률.
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import exec from 'k6/execution';

// 1. 대상 주소.
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 2. 시나리오: broad(다유저·다가맹점) | hot(소수 유저 집중).
const MODE = __ENV.MODE || 'broad';
const HOT_USERS = Number(__ENV.HOT_USERS || 1); // hot 모드에서 몰아칠 유저 수(기본 1 = 최대 경합).

// 3. register-users.js 와 동일한 공식으로 소비자 목록을 생성한다(phone 반드시 일치).
//    소비자 phone : '0155' + zero-pad(n,7)   n=1..300
const USER_COUNT = Number(__ENV.USER_COUNT || 300);
const USERS = Array.from({ length: USER_COUNT }, (_, i) => ({
    phoneNumber: '0155' + String(i + 1).padStart(7, '0'),
    password: 'password',
    paymentPin: '123456',
}));
// 결제 대상 가맹점 partyId. 기본 = 기존 시드(2) + API 등록분(306,307). 필요시 -e MERCHANTS=2,306,307 로 조정.
const MERCHANT_PARTY_IDS = (__ENV.MERCHANTS || '2,306,307').split(',').map(Number);

// 4. 커스텀 지표.
const paySuccess = new Counter('payment_success');   // 결제 성공(200) = 실제 처리량
const payRejected = new Counter('payment_rejected'); // 409 = 락 경합 거절
const payError = new Counter('payment_error');       // 그 외(설정/시드 문제 신호)
const execMs = new Trend('payment_execute_ms');      // execute 지연(ms)

// 5. 부하 프로파일.
//    PROFILE=normal(기본): 처리량/병목 관측용 완만한 램프(최대 80 VU).
//    PROFILE=breakpoint  : 한계점 테스트 — VU를 공격적으로 올려 서버가 무너지는 지점(5xx/타임아웃)을 찾는다.
const STAGES = {
    normal: [
        { duration: '30s', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '1m', target: 80 },
        { duration: '30s', target: 0 },
    ],
    breakpoint: [
        { duration: '45s', target: 100 },
        { duration: '45s', target: 200 },
        { duration: '45s', target: 350 },
        { duration: '45s', target: 500 },
        { duration: '60s', target: 800 },
        { duration: '20s', target: 0 },
    ],
    // BCrypt/CPU 용량 측정: 낮은 VU를 계단식으로 유지하며 처리량이 코어 수에서 평탄해지는지 관찰.
    capacity: [
        { duration: '5s', target: 1 }, { duration: '40s', target: 1 },
        { duration: '5s', target: 2 }, { duration: '40s', target: 2 },
        { duration: '5s', target: 3 }, { duration: '40s', target: 3 },
        { duration: '5s', target: 4 }, { duration: '40s', target: 4 },
        { duration: '5s', target: 6 }, { duration: '40s', target: 6 },
        { duration: '5s', target: 8 }, { duration: '40s', target: 8 },
        { duration: '10s', target: 0 },
    ],
    // 붕괴 재현/방어 확인: 예전 붕괴를 부른 80VU를 오래 유지 + 120VU로 한 번 더 밀어 세마포어가 admission control로 막는지 본다.
    flood: [
        { duration: '15s', target: 80 }, { duration: '90s', target: 80 },
        { duration: '15s', target: 120 }, { duration: '60s', target: 120 },
        { duration: '15s', target: 0 },
    ],
};
export const options = {
    scenarios: {
        default: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: STAGES[__ENV.PROFILE] || STAGES.normal,
            gracefulRampDown: '5s',
            gracefulStop: '10s',
        },
    },
    // breakpoint는 실패가 목적이라 threshold를 두지 않는다(normal에서만 참고 지표).
    thresholds:
        (__ENV.PROFILE === 'breakpoint') ? {} : { payment_execute_ms: ['p(95)<5000'] },
};

// 6. VU 로컬 상태.
let me = null;
let session = null;

// VU 에 배정할 유저를 고른다. broad = VU마다 다른 유저(경합 분산), hot = 소수 유저에 집중.
function pickUser() {
    if (MODE === 'hot') {
        return USERS[(__VU - 1) % HOT_USERS];
    }
    return USERS[(__VU - 1) % USERS.length];
}

export default function () {
    // 7. VU 당 최초 1회 로그인. SESSION 쿠키는 서버가 Secure 로 내려 plain http 에 자동으로 안 실리므로 값만 보관.
    if (session === null) {
        me = pickUser();
        const login = http.post(
            `${BASE_URL}/api/v1/auth/users/login`,
            JSON.stringify({ phoneNumber: me.phoneNumber, password: me.password }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        const sc = login.cookies['SESSION'];
        if (sc && sc.length > 0) session = sc[0].value;
        else {
            payError.add(1);
            return; // 로그인 실패면 시드 미투입/자격 불일치
        }
    }
    // 7-1. 매 iteration 세션 쿠키를 non-secure 로 재고정(안 하면 응답의 Secure 쿠키가 덮어써 다음 요청 401).
    http.cookieJar().set(BASE_URL, 'SESSION', session);

    const headers = { headers: { 'Content-Type': 'application/json' } };

    // 8. 랜덤 가맹점 + 전역 유일 금액(findLivePendingPayment 재사용 회피)으로 결제 intent.
    const merchantPartyId = MERCHANT_PARTY_IDS[Math.floor(Math.random() * MERCHANT_PARTY_IDS.length)];
    const amount = 1000 + exec.scenario.iterationInTest;
    const intent = http.post(
        `${BASE_URL}/api/v1/payment/intents`,
        JSON.stringify({ merchantPartyId, amount, itemName: 'throughput' }),
        headers
    );
    if (!check(intent, { '결제 intent 2xx': (r) => r.status >= 200 && r.status < 300 })) {
        payError.add(1);
        return;
    }
    const uuid = intent.json('result.transactionUuid');
    if (!uuid) {
        payError.add(1);
        return;
    }

    // 9. 결제 execute — v0: payer wallet NOWAIT 비관락 / v1: 거래 단위 Redis 분산락.
    const res = http.post(
        `${BASE_URL}/api/v1/payment/${uuid}/execute`,
        JSON.stringify({ paymentPin: me.paymentPin }),
        headers
    );
    execMs.add(res.timings.duration);

    // 10. 결과 분류.
    if (res.status === 200) {
        paySuccess.add(1);
    } else if (res.status === 409) {
        payRejected.add(1); // 락 경합 거절 (v0 hot 에서 급증)
    } else {
        payError.add(1);
    }
    check(res, { '결제 execute 200/409': (r) => r.status === 200 || r.status === 409 });
}
