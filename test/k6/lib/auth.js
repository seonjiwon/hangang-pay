// 로그인 헬퍼.
//
// 로그인은 서버에서 BCrypt 비밀번호 검증이 돌아 비싸다. 그래서 부하 루프마다 하지 않고
// VU당 최초 1회만 호출한다. 성공하면 이 VU의 쿠키 jar에 세션이 저장되고,
// 이후 같은 VU의 요청은 k6가 세션 쿠키를 자동으로 실어 보낸다.
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, ENDPOINTS, JSON_HEADERS } from '../config.js';

// 소비자 로그인. user = { phoneNumber, password }
export function loginUser(user) {
  // 1. phoneNumber + password 로 로그인 요청
  const res = http.post(
    `${BASE_URL}${ENDPOINTS.loginUser}`,
    JSON.stringify({ phoneNumber: user.phoneNumber, password: user.password }),
    JSON_HEADERS
  );

  // 2. 200 확인 (실패하면 시드 계정이 없거나 BASE_URL/비밀번호가 틀린 것)
  check(res, { '로그인 200': (r) => r.status === 200 });

  // 3. 세션 쿠키(SESSION)는 서버가 Secure로 내린다(운영 프로파일). k6는 plain http라
  //    Secure 쿠키를 자동 재요청에 안 실어준다 -> 값을 꺼내 jar에 non-secure로 다시 심어 이후 요청에 실리게 한다.
  const session = res.cookies['SESSION'];
  if (session && session.length > 0) {
    http.cookieJar().set(BASE_URL, 'SESSION', session[0].value);
  }

  return res;
}
