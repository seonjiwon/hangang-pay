// 결제 부하 테스트 (payment v0 ↔ v1 대조용).
//
// [한 iteration = 결제 1건]
//   1) POST /api/v1/payment/intents         결제 의도 생성
//        - v0: payer wallet 비관적 락(findByParty_IdForUpdate)으로 직렬화되는 지점
//        - v1: 락 없음(IntentCreation 가드는 Redis)
//   2) POST /api/v1/payment/{uuid}/execute  PIN 검증 → 은행 결제 호출
//        - v0: 여기서도 payer wallet 비관락 / v1: Redis 분산락
//
// [전제] bank 호출은 WireMock으로 끊어둔다(BANK_BASE_URL=http://wiremock:8080).
//   그래야 besu(온프렘) 지연에 오염되지 않고 be 자체의 동시성(비관락 vs Redis락)만 순수하게 관측된다.
//   WireMock은 항상 SUCCESS를 즉시 반환하므로 UNKNOWN/재시도/reconcile 경로는 타지 않는다(의도).
//
// [관찰] app-overview 대시보드의 DB 커넥션(HikariCP):
//   v0(비관락) + 단일 시드유저 → 모든 요청이 같은 payer wallet 락에 직렬화 → active가 /10 천장 + pending 급증
//   v1(Redis락)              → DB 락 없음 → active 낮게 유지, pending ~0
//
// [실행] 엣지/WAF 우회해 be에 직접. k6는 대상 밖(노트북 등)에서 돌린다:
//   k6 run -e BASE_URL=http://<tailscale-ip>:8080 payment-load.js
//
// [사전준비] data/payment.json 을 채운다 (payment.example.json 참고, gitignored):
//   { phoneNumber, password, paymentPin, merchantPartyId }
//   - paymentPin      : 시드 유저의 결제 PIN (be가 BCrypt로 검증)
//   - merchantPartyId : 결제 대상 가맹점의 partyId
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { BASE_URL, ENDPOINTS, JSON_HEADERS } from './config.js';
import { loginUser } from './lib/auth.js';

// 1. 시드 로드 (init 단계. SharedArray는 모든 VU 공유 읽기전용이라 메모리 절약)
const USERS = new SharedArray('payment-users', function () {
  return JSON.parse(open('./data/payment.json'));
});

// 2. 부하 프로파일: 도착률(arrival-rate) 고정 방식.
//    "초당 결제 N건"을 명시적으로 고정하고, 그때 DB 커넥션/지연이 어떤지를 관찰한다.
//    서버가 목표 rate를 못 따라가면 k6가 dropped_iterations로 알려준다(= 포화 신호).
//    각 구간에서 http_req_failed가 ~0이면 건강, 오르기 시작하면 그 rps가 포화점.
export const options = {
  scenarios: {
    payment: {
      executor: 'ramping-arrival-rate',
      startRate: 10, // 초당 10건에서 시작
      timeUnit: '1s',
      preAllocatedVUs: 50, // 요청 처리용 VU 풀 미리 확보
      maxVUs: 200, // 서버가 느리면 여기까지 늘려 목표 도착률 유지
      stages: [
        { target: 30, duration: '1m' }, // 30 결제/초 (워밍업 + 건강 구간)
        { target: 60, duration: '1m' }, // 60 결제/초
        { target: 100, duration: '1m' }, // 100 결제/초 (여기서 실패율 오르면 포화 시작)
        { target: 0, duration: '30s' }, // 감소
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'], // 실패율 1% 미만이면 정상
    http_req_duration: ['p(95)<800'], // 참고용(핵심은 Grafana DB 커넥션 패널)
  },
};

// 3. VU 로컬 상태. k6는 VU마다 독립 JS 인스턴스라 이 변수들은 VU별로 따로 존재한다.
let loggedIn = false;
let me = null;

export default function () {
  // 4. VU당 최초 1회만 로그인 (BCrypt라 비싸므로 루프마다 하지 않는다).
  //    성공 시 세션 쿠키에 userId/partyId가 실려 이후 intent/execute가 인증된다.
  if (!loggedIn) {
    me = USERS[(__VU - 1) % USERS.length]; // VU 번호로 시드 유저 배정
    loginUser(me);
    loggedIn = true;
  }

  // 5. 금액을 전역 유일하게 만든다.
  //    findLivePendingPayment(같은 from/to/amount는 10분 내 PENDING 재사용)에 안 걸려
  //    매 요청이 새 PENDING을 만들게 = 비관락 경합을 정확히 유발한다.
  //    (결제 금액 상한 검증에 걸리면 승수(1000000)를 줄인다.)
  const amount = 1000 + __VU * 1000000 + __ITER;

  // 6. 결제 intent 생성
  const intentRes = http.post(
    `${BASE_URL}${ENDPOINTS.paymentIntents}`,
    JSON.stringify({ merchantPartyId: me.merchantPartyId, amount, itemName: 'load-test' }),
    JSON_HEADERS
  );
  const intentOk = check(intentRes, {
    '결제 intent 2xx': (r) => r.status >= 200 && r.status < 300,
  });
  if (!intentOk) return; // intent 실패면 execute 스킵 (시드/가맹점 설정 점검)

  // 7. intent 응답에서 transactionUuid 추출 (공통 응답 래퍼: result.transactionUuid)
  const transactionUuid = intentRes.json('result.transactionUuid');
  if (!transactionUuid) return;

  // 8. 결제 실행 (PIN) -> WireMock이 즉시 SUCCESS 반환 (besu 지연 없음)
  const executeRes = http.post(
    `${BASE_URL}/api/v1/payment/${transactionUuid}/execute`,
    JSON.stringify({ paymentPin: me.paymentPin }),
    JSON_HEADERS
  );
  check(executeRes, {
    '결제 execute 2xx': (r) => r.status >= 200 && r.status < 300,
  });
}
