# REST API

모든 API 경로 앞에는 `/api/v1` prefix를 붙인다. 아래 표의 path는 prefix를 제외한 경로다.  
단, `MERCHANT-009`는 예외로 `/api/v2`를 사용한다.

## Common Rules

- 인증 방식은 세션 기반이다. JWT로 변경하지 않는다.
- `Auth` 값이 `O`이면 로그인 세션이 필요하다.
- `Auth` 값이 `X`이면 비로그인 호출이 가능하다.
- 모든 응답은 공통 래퍼를 사용한다.
- 모든 엔드포인트에는 SpringDoc 어노테이션을 작성한다.
- request/response JSON은 아직 확정하지 않는다. DTO 설계 시 사람이 직접 검토한다.
- 결제 승인번호 형식은 `APV-YYYY-NNNNNNNN`이다.
- 승인번호는 `transaction.id`를 8자리 zero padding해서 생성한다. 예: `transaction.id=25` -> `APV-2026-00000025`
- 승인번호 생성은 `transaction` 저장으로 id를 확보한 뒤 수행한다.

```json
{
  "isSuccess": true,
  "status": 200,
  "code": "PAYMENT_EXECUTED",
  "message": "결제가 완료되었습니다.",
  "result": {}
}
```

## Role Rules

| Role | Meaning |
| --- | --- |
| `PUBLIC` | 비로그인 가능 |
| `USER` | 소비자 세션 필요 |
| `MERCHANT` | 가맹점 세션 필요 |
| `USER \| MERCHANT` | 소비자와 가맹점 모두 가능 |

계좌와 지갑 잔액 API는 소비자/가맹점 모두 사용한다. 서비스가 분리되어 있더라도 현재 세션의 `partyId`를 기준으로 자신의 데이터만 조회·변경한다.

## Payment Flow

QR에는 가맹점 id가 들어있다. 소비자가 QR을 스캔하면 가맹점 정보를 조회하고, 금액 입력 화면으로 전환한 뒤 결제를 실행한다.
결제 의도 생성은 서버가 소비자·가맹점·금액 fingerprint 기준으로 30초 dedup을 적용한다. 같은 fingerprint가 30초 안에 다시 들어오면 새 거래를 만들지 않고 기존 `transactionUuid`를 반환한다.
실행되지 않은 `PENDING` 결제 의도는 10분 TTL 경과 후 5분 주기 스케줄러가 `EXPIRED`로 닫는다.

```mermaid
sequenceDiagram
  participant U as User App
  participant API as Backend API
  participant Bank as Bank API

  U->>U: QR scan
  U->>API: GET /api/v1/merchant/{merchantId}
  API-->>U: merchant payment target
  U->>API: POST /api/v1/payment/intents
  API-->>U: transactionUuid
  U->>API: POST /api/v1/payment/execute
  API->>Bank: payment/transfer
  API-->>U: payment result
  Note over U,API: 실패 시 POST /api/v1/payment/{transactionUuid}/recover
```

결제 취소는 시간 제한 없이 가능하다. 요청 주체는 가맹점이다.

### 결제·취소 실행 결과와 재시도

결제 실행(`PAY-003`)과 결제 취소(`MERCHANT-004`)는 Bank 호출 결과에 따라 세 가지로 분기한다.

| 결과 | HTTP | 응답 형태 | 의미 |
| --- | --- | --- | --- |
| `SUCCESS` | `200` | 공통 래퍼 `result`에 거래 결과 | 정상 완료 |
| `UNKNOWN` | `200` | `result.status = UNKNOWN` | 은행 처리 확정 불가. 복구 API(결제 `PAY-004` · 취소 `MERCHANT-004-R`)와 스케줄러가 이후 정산 |
| `FAILED` | `4xx` | `BusinessException` 에러 응답(`isSuccess=false`) | 은행이 결정적으로 거부. 성공 응답이 아니라 에러로 내려간다 |

Bank 호출은 일시적 오류(연결/타임아웃, `5xx`, `409 DUPLICATE_PROCESSING`)에 한해 짧은 지연 후 **1회 동기 재시도**한다. 재시도 후에도 미해결이면 `UNKNOWN`으로 저장하고 복구 경로에 위임한다. 결정적 실패(`4xx` 등)는 재시도 없이 즉시 `FAILED`로 확정하고 정규화된 에러 코드를 던진다. Bank가 `transactionUuid`로 멱등 처리하므로 재시도가 이중 결제를 일으키지 않는다.

FAILED 시 내려가는 정규화 에러 코드:

| code | HTTP | 매핑 원천(Bank) |
| --- | --- | --- |
| `PAYMENT_INSUFFICIENT_BALANCE` | `400` | `TRANSACTION_INSUFFICIENT_BALANCE` |
| `PAYMENT_ALREADY_FAILED` | `422` | `TRANSACTION_ALREADY_FAILED` |
| `PAYMENT_FAILED` | `502` | 매핑되지 않은 그 외 Bank 실패(폴백) |
| `CANCEL_ALREADY_FAILED` | `422` | `TRANSACTION_ALREADY_FAILED` |
| `CANCEL_FAILED` | `502` | 매핑되지 않은 그 외 Bank 취소 실패(폴백) |

### 상태 복구(Recovery)

`UNKNOWN`/`PROCESSING`으로 남은 거래는 복구 API로 은행 상태를 재조회해 종단 상태(`SUCCESS`/`FAILED`)로 수렴시킨다. 재실행이 아니라 **조회(`getStatus`) 기반 확정**이다.

| ID | 대상 | Method | Path | Role |
| --- | --- | --- | --- | --- |
| `PAY-004` | 결제 복구 | `POST` | `/payment/{transactionUuid}/recover` | `USER` |
| `MERCHANT-004-R` | 결제 취소 복구 | `POST` | `/merchant/payments/{transactionId}/cancel/recover` | `MERCHANT` |

복구 결과 처리:

- **응답은 항상 `200`**이며 `result.status`로 결과를 전달한다. 실행(`execute`)과 달리 `FAILED`여도 예외(`4xx`)가 아니라 상태값으로 내려간다.
- 정상 조회: 은행 상태(`SUCCESS`/`FAILED`/`PROCESSING`)를 그대로 반영한다.
- 은행 `404`(원장에 기록 없음 = 은행 도달 전 중단, 플랫폼 책임): `FAILED`로 확정한다.
- 은행 `5xx` 등 일시 오류: 확정하지 않고 다음 복구 주기에 재시도한다.
- 종단 확정 시 Redis 멱등 레코드를 정리한다(`SUCCESS`→완료 스냅샷, `FAILED`→실패 마킹). 아직 `PROCESSING`이면 `reconcile_attempt_count`만 올린다(예산 10회).
- 스케줄러가 매분 `UNKNOWN` 전체와 5분 이상 정체된 `PROCESSING`을 같은 복구 경로로 자동 수행하며, 예산을 소진하면 `EXPIRED`로 종료하고 alert를 남긴다.

## Settlement and Exchange

정산은 가맹점이 보유한 토큰을 1:1 비율로 계좌 환전 신청한 기록을 의미한다.

`/merchant/settlements`는 별도 정산 테이블 조회가 아니라 `transaction` 테이블에서 현재 가맹점의 환전 거래를 조회하는 API다. 조회 대상은 `from_party_id`가 현재 가맹점의 `partyId`이고 `transaction_type`이 `EXCHANGE`인 거래다.

서비스 용어는 `exchange`와 `환전`을 사용한다.

환전(소비자 `EXCHANGE-002/003`, 가맹점 `MERCHANT-007/008`)은 결제와 동일하게 **2단계**로 처리한다.

1. **의도 생성**(`/intents`): PIN·게이트 없이 자격 검증(소비자만) 후 `PENDING` `transaction`을 먼저 커밋한다. `transactionUuid`는 **서버(BE)가 발급**하며 응답에 포함한다(FE는 보내지 않는다 — 신뢰 경계 밖). 짧은 창의 중복 의도 생성은 best-effort 가드가 `429 INTENT_DUPLICATE_REQUEST`로 컷한다.
2. **실행**(`/{transactionUuid}/execute`): PIN 검증 → Redis 멱등 게이트 → `PENDING→PROCESSING` 선점 → bank 동기 호출 → `SUCCESS`/`FAILED`/`UNKNOWN` 확정.

DB intent가 게이트보다 먼저 커밋되므로 실행 중 어디서 실패해도 거래 레코드가 남아 복구된다. 상태/복구 규칙:

- 타임아웃·불확실 응답은 `FAILED`로 단정하지 않고 `UNKNOWN`으로 둔다.
- 배치가 `PROCESSING`/`UNKNOWN` 거래를 bank 재조회(`getStatus`)로 확정한다(재실행 아님, `retry_count` 예산 10회).
- 실행되지 않고 버려진 `PENDING` 의도는 만료 배치가 TTL(10분) 경과 시 `EXPIRED`로 정리한다.

## Hold Policy

| API ID | Status | Reason |
| --- | --- | --- |
| `AUTH-005` | 보류 | 비밀번호 재설정 |
| `WALLET-002` | 장기 보류 | 근처 가맹점 조회. 구현 복잡도 |

## API Catalog

| ID | Name | Method | Path | Auth | Role | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| `LOGIN-001` | 로그인 (소비자) | `POST` | `/auth/users/login` | `X` | `PUBLIC` | 세션 생성 |
| `LOGIN-002` | 로그인 (가맹점) | `POST` | `/auth/merchants/login` | `X` | `PUBLIC` | 세션 생성 |
| `LOGOUT-001` | 로그아웃 | `POST` | `/auth/logout` | `O` | `USER \| MERCHANT` | 현재 세션 삭제 |
| `REG-001` | 소비자 회원가입 | `POST` | `/auth/users/register` | `X` | `PUBLIC` | 회원/주계좌/지갑 생성 |
| `REG-002` | 사업자 정보 조회 | `GET` | `/auth/merchants/business-info` | `X` | `PUBLIC` | 쿼리 파라미터: `businessNumber` |
| `REG-003` | 가맹점 회원가입 | `POST` | `/auth/merchants/register` | `X` | `PUBLIC` | |
| `ACCOUNT-001` | 등록 계좌 목록 조회 | `GET` | `/accounts` | `O` | `USER \| MERCHANT` | 현재 세션의 `partyId` 기준 |
| `ACCOUNT-002` | 계좌 추가 | `POST` | `/accounts` | `O` | `USER \| MERCHANT` | 현재 세션의 `partyId` 기준 |
| `ACCOUNT-003` | 계좌 삭제 | `DELETE` | `/accounts/{accountId}` | `O` | `USER \| MERCHANT` | 본인 계좌만 삭제 |
| `ACCOUNT-004` | 주거래 계좌 변경 | `PATCH` | `/accounts/{accountId}/primary` | `O` | `USER \| MERCHANT` | 본인 계좌만 변경 |
| `PAY-001` | QR 가맹점 정보 조회 | `GET` | `/merchant/{merchantId}` | `O` | `USER` | QR 스캔 후 결제 플로우 진입 |
| `PAY-002` | 결제 의도 생성 | `POST` | `/payment/intents` | `O` | `USER` | 금액·가맹점 정보 전달; 서버 fingerprint 30초 dedup 후 transactionUuid 반환 |
| `PAY-003` | 결제 실행 | `POST` | `/payment/execute` | `O` | `USER` | 소비자 전용; 결과 SUCCESS/UNKNOWN=200, FAILED=4xx (상세는 Payment Flow) |
| `PAY-004` | 결제 상태 복구 | `POST` | `/payment/{transactionUuid}/recover` | `O` | `USER` | 결제 실패·중단 시 상태 복구 |
| `CHARGE-001` | 충전 정보 조회 | `GET` | `/charge/init` | `O` | `USER` | 충전 한도·할인 계산 (조회 전용) |
| `CHARGE-002` | 충전 의도 생성 | `POST` | `/charge/intents` | `O` | `USER` | 서버 발급 `transactionUuid`(응답 포함); 금액·출금 계좌 바인딩, PENDING 의도 생성. PIN 없음 |
| `CHARGE-003` | 충전 실행 | `POST` | `/charge/{transactionUuid}/execute` | `O` | `USER` | 소비자 전용; PIN 검증 → 충전 실행 |
| `EXCHANGE-001` | 환전 정보 조회 | `GET` | `/exchange/init` | `O` | `USER \| MERCHANT` | 환전 가능 여부·예정 금액 포함 |
| `EXCHANGE-002` | 환전 의도 생성 | `POST` | `/exchange/intents` | `O` | `USER` | 서버 발급 `transactionUuid`(응답 포함); 자격(60%) 검증 후 PENDING 의도 생성. PIN 없음 |
| `EXCHANGE-003` | 환전 실행 | `POST` | `/exchange/{transactionUuid}/execute` | `O` | `USER` | PIN 검증 → 1:1 계좌 환전 |
| `MERCHANT-001` | 가맹점 매출 요약 조회 | `GET` | `/merchant/dashboard` | `O` | `MERCHANT` | 가맹점 전용 |
| `MERCHANT-002` | 가맹점 결제 내역 조회 | `GET` | `/merchant/payments` | `O` | `MERCHANT` | 가맹점 전용; item의 `transactionId`를 상세조회 path에 사용 |
| `MERCHANT-003` | 가맹점 결제 상세 조회 | `GET` | `/merchant/payments/{transactionId}` | `O` | `MERCHANT` | `transactionId`는 `transaction.id`; 응답에 `PAYMENT`/`CANCEL` 타입 포함 |
| `MERCHANT-004` | 결제 취소 | `POST` | `/merchant/payments/{paymentId}/cancel` | `O` | `MERCHANT` | 시간 제한 없음; 결과 SUCCESS/UNKNOWN=200, FAILED=4xx (상세는 Payment Flow) |
| `MERCHANT-004-R` | 결제 취소 복구 | `POST` | `/merchant/payments/{transactionId}/cancel/recover` | `O` | `MERCHANT` | UNKNOWN/중단 취소 상태 복구; 결과는 항상 200 + `result.status` (상세는 Payment Flow) |
| `MERCHANT-005` | 가맹점 정산 내역 조회 | `GET` | `/merchant/settlements` | `O` | `MERCHANT` | 현재 가맹점의 `EXCHANGE` 거래 조회 (`transaction.from_party_id = partyId`) |
| `MERCHANT-006` | 가맹점 정산 신청 조회 | `GET` | `/merchant/redeem` | `O` | `MERCHANT` | 토큰→현금 잔액·계좌 조회 |
| `MERCHANT-007` | 가맹점 정산 의도 생성 | `POST` | `/merchant/redeem/intents` | `O` | `MERCHANT` | 서버 발급 `transactionUuid`(응답 포함); PENDING 의도 생성. PIN 없음 |
| `MERCHANT-008` | 가맹점 정산 실행 | `POST` | `/merchant/redeem/{transactionUuid}/execute` | `O` | `MERCHANT` | PIN 검증 → 1:1 계좌 환전 |
| `MERCHANT-009` | 가맹점 QR 생성/조회 | `GET` | `/merchant/qr` | `O` | `MERCHANT` | 결제용 QR 코드 (merchantId 포함) |
| `MERCHANT-010` | 가맹점 마이페이지 조회 | `GET` | `/merchant/mypage` | `O` | `MERCHANT` | |
| `MERCHANT-011` | 가맹점 계좌 변경 | `PATCH` | `/merchant/accounts` | `O` | `MERCHANT` | SETTLEMENT 계좌 upsert |
| `MY-001` | 사용자 마이페이지 조회 | `GET` | `/users/profile` | `O` | `USER` | 소비자 전용 |
| `MY-002` | 사용자 내역 조회 | `GET` | `/users/histories` | `O` | `USER` | 소비자 전용 |
| `MY-003` | 내역 상세 조회 | `GET` | `/users/histories/{historyId}` | `O` | `USER` | 소비자 전용 |
| `WALLET-001` | 잔액 조회 | `GET` | `/wallet/balance` | `O` | `USER \| MERCHANT` | 역할별 서비스/응답 분리 가능 |
