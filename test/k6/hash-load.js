// 해시 CPU 측정용 부하 테스트.
//
// [대상] 충전 intent 생성 (POST /api/v1/charge/intents)
//   - 이 경로는 요청 해시(IntentCreationGuard의 SHA-256)를 계산하지만 bank/besu는 호출하지 않는다(BE-local).
//   - 따라서 besu(qemu) 지연에 오염되지 않고 "해시 계산이 CPU를 얼마나 먹나"만 순수하게 뽑을 수 있다.
//
// [관찰] be는 request.hash Timer를 노출한다. Grafana에서:
//   rate(request_hash_seconds_sum[1m])  = 초당 해시에 쓴 시간(≈ 코어 점유) -> "해시 CPU"의 답
//   rate(request_hash_seconds_count[1m]) = 초당 해시 호출 수
//   이걸 process_cpu_usage(전체 CPU)와 나란히 보면 비율이 나온다.
//
// [실행] 엣지/WAF를 우회해 be에 직접 쏜다. k6는 대상 밖(노트북 등)에서 실행한다
//        (같은 EC2에서 돌리면 측정 대상 CPU를 뺏는다):
//   k6 run -e BASE_URL=http://<tailscale-ip>:8080 hash-load.js
//
// [사전준비] data/users.json 에 시드 유저를 채운다 (users.example.json 참고, README 참고).
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { BASE_URL, ENDPOINTS, JSON_HEADERS } from './config.js';
import { loginUser } from './lib/auth.js';

// 1. 시드 유저 로드 (init 단계. SharedArray는 모든 VU가 공유하는 읽기전용 배열이라 메모리 절약)
const USERS = new SharedArray('users', function () {
  return JSON.parse(open('./data/users.json'));
});

// 2. 부하 프로파일: 도착률(arrival-rate) 고정 방식.
//    closed(VU 고정)와 달리 "초당 몇 건"을 명시적으로 고정하고, 그때 CPU가 얼마인지를 관찰한다.
//    -> 독립변수(RPS)를 고정해야 "N rps에서 해시 CPU가 얼마"처럼 해석이 깔끔하다.
//    서버가 목표 rate를 못 따라가면 k6가 dropped_iterations로 알려준다(= 포화 신호).
//    rate/VU 수는 대상 인스턴스 사양에 맞춰 조절한다.
export const options = {
  scenarios: {
    hash: {
      executor: 'ramping-arrival-rate',
      startRate: 100, // 초당 100건에서 시작
      timeUnit: '1s', // rate 단위 = 초당
      preAllocatedVUs: 50, // 요청 처리용 VU 풀 미리 확보
      maxVUs: 200, // 서버가 느리면 여기까지 늘려 목표 도착률 유지
      stages: [
        { target: 200, duration: '1m' }, // 100 -> 200 rps 램프업 (JIT 예열)
        { target: 500, duration: '2m' }, // 500 rps 고정 유지 <- 이 구간을 Grafana로 관찰
        { target: 0, duration: '30s' }, // 감소
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'], // 실패율 1% 미만이면 정상
    http_req_duration: ['p(95)<500'], // 참고용(해시 CPU 자체는 Grafana Timer로 본다)
  },
};

// 3. VU 로컬 상태. k6는 VU마다 독립 JS 인스턴스라 이 변수들은 VU별로 따로 존재한다.
let loggedIn = false;
let me = null;

export default function () {
  // 4. VU당 최초 1회만 로그인 (BCrypt라 비싸므로 루프마다 하지 않는다)
  if (!loggedIn) {
    me = USERS[(__VU - 1) % USERS.length]; // VU 번호로 시드 유저 배정
    loginUser(me);
    loggedIn = true;
  }

  // 5. 충전 intent 생성 -> 요청 해시 계산 지점.
  //    IntentCreationGuard(txType+partyId+amount+accountId 3초 중복차단)에 안 걸리게 금액을 유일하게 만든다.
  //    시드 유저가 하나(=partyId 하나)라도 __VU까지 섞어 전역 유일하게. (유저 여러 명이면 이 트릭 불필요)
  const amount = 1000 + __VU * 1000000 + __ITER;

  const res = http.post(
    `${BASE_URL}${ENDPOINTS.chargeIntents}`,
    JSON.stringify({ institutionId: me.institutionId, accountId: me.accountId, amount }),
    JSON_HEADERS
  );

  // 6. 2xx 확인 (429가 뜨면 dedup 가드에 막힌 것 -> 금액 다양화 로직 점검)
  check(res, { '충전 intent 2xx': (r) => r.status >= 200 && r.status < 300 });
}
