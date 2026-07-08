// 스모크 테스트: VU 1명, 1회 반복.
//
// 목적은 "측정"이 아니라 "스크립트가 제대로 도는지" 확인이다.
// 로그인 -> 충전 intent 생성 -> 응답 200 + transactionUuid 오는지만 본다.
// 실제 부하(hash-load.js) 전에 로컬 또는 대상에 대고 먼저 돌려본다:
//   k6 run -e BASE_URL=http://localhost:8080 smoke.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, ENDPOINTS, JSON_HEADERS } from './config.js';
import { loginUser } from './lib/auth.js';

// 시드 유저 1명. 값은 환경변수로 덮어쓸 수 있다 (기본은 users.example.json 첫 유저).
const USER = {
  phoneNumber: __ENV.USER_PHONE || '01000000001',
  password: __ENV.USER_PW || 'test1234!',
};
const INSTITUTION_ID = Number(__ENV.INSTITUTION_ID || 2);
const ACCOUNT_ID = Number(__ENV.ACCOUNT_ID || 1);

export const options = { vus: 1, iterations: 1 };

export default function () {
  // 1. 로그인 (세션 쿠키 발급)
  loginUser(USER);

  // 2. 충전 intent 1건 생성 -> 여기서 요청 해시가 계산된다 (bank 미호출)
  const res = http.post(
    `${BASE_URL}${ENDPOINTS.chargeIntents}`,
    JSON.stringify({ institutionId: INSTITUTION_ID, accountId: ACCOUNT_ID, amount: 10000 }),
    JSON_HEADERS
  );

  // 3. 응답 확인 (공통 래퍼: { isSuccess, status, code, message, result })
  check(res, {
    '충전 intent 2xx': (r) => r.status >= 200 && r.status < 300,
    'transactionUuid 존재': (r) => r.json('result.transactionUuid') !== undefined,
  });

  // 4. 실패 시 원인 파악용으로 응답을 찍는다
  console.log(`status=${res.status} body=${res.body}`);
  sleep(1);
}
