// 부하 테스트용 소비자 300명을 API 회원가입으로 생성한다.
//
// [전제] bank 호출(POST /api/v1/bank-accounts, /api/v1/bank-wallets)이 WireMock 으로 스텁돼 있어야 한다.
//        (없으면 502 BANK_CALL_FAILED). SMS/1원 인증은 코드에 없으므로 우회 불필요.
//
// 생성 규칙(payment-throughput.js 와 동일 공식):
//   phone         : '0155' + zero-pad(n,7)   n=1..COUNT   -> 01550000001 ..
//   accountNumber : '1002' + zero-pad(n,8)                 -> 100200000001 ..
//   password="password", paymentPin="123456", institutionId=2(우리은행, 시드됨)
//
// [실행] k6 run -e BASE_URL=http://<hp-be>:8080 -e COUNT=300 test/k6/register-users.js
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const COUNT = Number(__ENV.COUNT || 300);

const created = new Counter('reg_created');
const dup = new Counter('reg_duplicate');
const failed = new Counter('reg_failed');

export const options = {
    scenarios: {
        register: { executor: 'shared-iterations', vus: 10, iterations: COUNT, maxDuration: '3m' },
    },
};

export default function () {
    const n = exec.scenario.iterationInTest + 1; // 1..COUNT
    const phone = '0155' + String(n).padStart(7, '0');
    const account = '1002' + String(n).padStart(8, '0');

    const res = http.post(
        `${BASE_URL}/api/v1/auth/users/register`,
        JSON.stringify({
            name: 'loaduser' + n,
            phoneNumber: phone,
            password: 'password',
            paymentPin: '123456',
            institutionId: 2,
            accountNumber: account,
        }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    if (res.status >= 200 && res.status < 300) created.add(1);
    else if (res.status === 409) dup.add(1); // 이미 있음(재실행) — 정상
    else {
        failed.add(1);
        console.error(`reg fail n=${n} status=${res.status} body=${String(res.body).slice(0, 160)}`);
    }
    check(res, { 'register 2xx or dup(409)': (r) => (r.status >= 200 && r.status < 300) || r.status === 409 });
}
