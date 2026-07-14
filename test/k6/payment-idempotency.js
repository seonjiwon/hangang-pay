// 결제 멱등성 테스트 (가벼운 부하).
//
// [검증 대상] "같은 결제(transactionUuid)를 동시에 여러 번 실행해도 결제는 한 번만 처리되는가."
//   payer wallet NOWAIT 락으로 동시 중복 실행을 직렬화하고, 완료된 요청은 멱등 스토어의
//   snapshot으로 되돌려준다. 이 두 성질을 HTTP 레벨에서 확인한다.
//
// [관측 신호] 승인번호(approvalNumber)는 transaction.id로 결정되는 값이라 재처리해도 동일하다.
//   즉 "승인번호가 여러 개"로는 이중 처리를 잡을 수 없다. 대신 직렬화의 관측 가능한 증거는
//   "동시 중복 요청이 409(PAYMENT_ALREADY_PROCESSING)로 거절되는가"이다.
//   락/멱등 가드가 빠지면 동시 요청이 모두 200이 되어(전부 처리) double_processed가 폭증한다.
//   최종적인 "정확히 1회 처리"는 DB로 확증한다:
//     SELECT status, count(*) FROM idempotency GROUP BY status;   -- uuid당 1행
//     SELECT count(*) FROM transaction WHERE transaction_uuid=... AND status='SUCCESS';  -- 1건
//
// [흐름] iteration당
//   1) POST /api/v1/payment/intents            결제 의도 1건 생성 -> transactionUuid
//   2) 같은 uuid를 http.batch로 DUP(=5)번 동시 execute
//        기대: 1건 200(선점 승자) + 나머지 409(NOWAIT fail-fast 거절)
//   3) 같은 uuid를 한 번 더 순차 execute (완료 후 재요청)
//        기대: 200 + 승자와 동일한 승인번호 (멱등 스토어 snapshot 재사용, 은행 재호출 없음)
//
// [전제] bank 호출은 WireMock으로 끊어둔다(BANK_BASE_URL=http://wiremock:8080, 즉시 SUCCESS).
//   data/payment.json 을 채운다(payment.example.json 참고, gitignored):
//     { phoneNumber, password, paymentPin, merchantPartyId }
//
// [실행] 엣지/WAF 우회해 be에 직접:
//   k6 run -e BASE_URL=http://<tailscale-ip>:8080 payment-idempotency.js
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { BASE_URL, ENDPOINTS, JSON_HEADERS } from './config.js';
import { loginUser } from './lib/auth.js';

// 1. 하나의 uuid에 동시에 쏘는 중복 실행 수.
const DUP = 5;

// 2. 시드 로드 (init 단계. 모든 VU가 공유하는 읽기전용 배열).
const USERS = new SharedArray('payment-users', function () {
  return JSON.parse(open('./data/payment.json'));
});

// 3. 멱등성 판정용 커스텀 지표.
//    - idem_2xx           : 처리(200) 응답 수
//    - idem_409           : 중복 거절(409) 수 = NOWAIT/멱등 가드가 동작한 증거
//    - idem_unexpected    : 200/409 외 예상 못한 응답 (설정/시드 문제 신호)
//    - idem_double_processed : 한 uuid의 동시 버스트에서 200이 2건 이상 나온 초과분(이중 처리 의심). 0이어야 통과.
const idem2xx = new Counter('idem_2xx');
const idem409 = new Counter('idem_409');
const idemUnexpected = new Counter('idem_unexpected');
const idemDoubleProcessed = new Counter('idem_double_processed');

// 4. 가벼운 부하. VU 5개가 각자 20회 반복(= intent 100건), 반복마다 DUP개의 동시 중복을 batch로 쏜다.
export const options = {
  scenarios: {
    idempotency: {
      executor: 'per-vu-iterations',
      vus: 5,
      iterations: 20,
      maxDuration: '2m',
    },
  },
  thresholds: {
    idem_double_processed: ['count==0'], // 이중 처리가 한 건도 없어야 통과 (핵심)
    idem_unexpected: ['count==0'], // 예상 못한 응답이 없어야 통과
    checks: ['rate>0.99'],
  },
};

// 5. VU 로컬 상태 (k6는 VU마다 독립 JS 인스턴스).
let loggedIn = false;
let me = null;

export default function () {
  // 6. VU당 최초 1회만 로그인 (BCrypt라 비싸므로 루프마다 하지 않는다).
  if (!loggedIn) {
    me = USERS[(__VU - 1) % USERS.length];
    loginUser(me);
    loggedIn = true;
  }

  // 7. 금액을 전역 유일하게 만들어 findLivePendingPayment(같은 from/to/amount 10분내 재사용)에 안 걸리게 한다.
  //    (금액 상한 검증에 걸리면 승수 1000000을 줄인다.)
  const amount = 1000 + __VU * 1000000 + __ITER;

  // 8. 결제 intent 1건 생성.
  const intentRes = http.post(
    `${BASE_URL}${ENDPOINTS.paymentIntents}`,
    JSON.stringify({ merchantPartyId: me.merchantPartyId, amount, itemName: 'idempotency-test' }),
    JSON_HEADERS
  );
  const intentOk = check(intentRes, {
    '결제 intent 2xx': (r) => r.status >= 200 && r.status < 300,
  });
  if (!intentOk) return; // intent 실패면 시드/가맹점 설정 점검

  const transactionUuid = intentRes.json('result.transactionUuid');
  if (!transactionUuid) return;

  // 9. 같은 uuid를 DUP번 동시에 execute (http.batch = 병렬 발사).
  const executeUrl = `${BASE_URL}/api/v1/payment/${transactionUuid}/execute`;
  const executeBody = JSON.stringify({ paymentPin: me.paymentPin });
  const batchReqs = [];
  for (let i = 0; i < DUP; i++) {
    batchReqs.push(['POST', executeUrl, executeBody, JSON_HEADERS]);
  }
  const responses = http.batch(batchReqs);

  // 10. 동시 버스트 결과 집계.
  let count2xx = 0;
  let count409 = 0;
  let countOther = 0;
  let winnerApproval = null;
  for (const res of responses) {
    if (res.status >= 200 && res.status < 300) {
      count2xx++;
      if (winnerApproval === null) {
        winnerApproval = res.json('result.approvalNumber');
      }
    } else if (res.status === 409) {
      count409++;
    } else {
      countOther++;
    }
  }
  idem2xx.add(count2xx);
  idem409.add(count409);
  idemUnexpected.add(countOther);

  // 11. 초과 성공(2건 이상)은 이중 처리 의심 -> 지표에 초과분을 기록(0이어야 정상).
  const doubleProcessed = count2xx > 1 ? count2xx - 1 : 0;
  idemDoubleProcessed.add(doubleProcessed);

  check(null, {
    '동시 중복 중 처리 1건만': () => count2xx === 1,
    '나머지는 409로 거절': () => count2xx + count409 === DUP,
    '예상외 응답 없음': () => countOther === 0,
  });

  // 12. 완료 후 같은 uuid 재요청 -> 저장된 snapshot을 그대로 돌려준다(은행 재호출 없음).
  //     기대: 200 + 승자와 동일한 승인번호.
  const retryRes = http.post(executeUrl, executeBody, JSON_HEADERS);
  check(retryRes, {
    '재요청 200(snapshot 재사용)': (r) => r.status === 200,
    '재요청 승인번호가 최초와 동일': (r) =>
      winnerApproval !== null && r.json('result.approvalNumber') === winnerApproval,
  });
}
