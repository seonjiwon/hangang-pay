// DB knee 측정 — BCrypt 없는 DB 경로(intent)를 "정해진 rate"로 램프하며 DB가 언제 꺾이는지 본다.
//
// [왜 intent인가] createPaymentIntent는 BCrypt가 없고 DB를 ~6쿼리(read5+write1) 때린다.
//   -> 앱 CPU(BCrypt) 혼입 없이 "DB가 초당 몇 요청까지 빠른지"를 격리 측정.
// [왜 arrival-rate인가] VU가 아니라 "초당 요청 수"를 직접 램프(5->10->20->40->60->80->120/s).
//   각 단계에서 DB 쿼리 지연(spring_data)이 언제 오르기 시작하는지 = knee.
//
// 실행: k6 run -e BASE_URL=http://<hp-be>:8080 test/k6/db-knee.js
import http from 'k6/http';
import { Trend, Counter } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USER_COUNT = Number(__ENV.USER_COUNT || 300);
const USERS = Array.from({ length: USER_COUNT }, (_, i) => ({
    phoneNumber: '0155' + String(i + 1).padStart(7, '0'),
    password: 'password',
}));
const MERCHANT_PARTY_IDS = (__ENV.MERCHANTS || '2,306,307').split(',').map(Number);

const intentMs = new Trend('intent_ms');
const intentOk = new Counter('intent_ok');
const intentErr = new Counter('intent_err');

export const options = {
    scenarios: {
        knee: {
            executor: 'ramping-arrival-rate',
            startRate: 5,
            timeUnit: '1s',
            preAllocatedVUs: 200,
            maxVUs: 400,
            stages: [
                { duration: '30s', target: 5 },
                { duration: '30s', target: 10 },
                { duration: '30s', target: 20 },
                { duration: '30s', target: 40 },
                { duration: '30s', target: 60 },
                { duration: '30s', target: 80 },
                { duration: '30s', target: 120 },
                { duration: '20s', target: 0 },
            ],
        },
    },
    thresholds: {}, // 목적이 한계 관측이라 threshold 없음
};

let session = null;

export default function () {
    if (session === null) {
        const me = USERS[(__VU - 1) % USERS.length];
        const login = http.post(
            `${BASE_URL}/api/v1/auth/users/login`,
            JSON.stringify({ phoneNumber: me.phoneNumber, password: me.password }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        const sc = login.cookies['SESSION'];
        if (sc && sc.length > 0) session = sc[0].value;
        else {
            intentErr.add(1);
            return;
        }
    }
    http.cookieJar().set(BASE_URL, 'SESSION', session);

    const merchantPartyId = MERCHANT_PARTY_IDS[Math.floor(Math.random() * MERCHANT_PARTY_IDS.length)];
    const amount = 1000 + exec.scenario.iterationInTest; // 전역 유일 -> dedup 회피, 매번 새 PENDING save
    const res = http.post(
        `${BASE_URL}/api/v1/payment/intents`,
        JSON.stringify({ merchantPartyId, amount, itemName: 'knee' }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    intentMs.add(res.timings.duration);
    if (res.status >= 200 && res.status < 300) intentOk.add(1);
    else intentErr.add(1);
}
