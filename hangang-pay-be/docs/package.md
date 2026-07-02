# Package Structure

Base package: `family.fisa.hangangpay`

## Overview

```mermaid
flowchart TD
  root["family.fisa.hangangpay"]
  root --> domain["domain"]
  root --> global["global"]
  root --> auth["auth<br/>세션 인증, 로그인/로그아웃, 회원가입, SMS/계좌 인증"]
  root --> client["client<br/>외부 시스템 연동"]

  domain --> party["party<br/>USER | MERCHANT 공통 상위 식별자"]
  domain --> user["user<br/>소비자 회원, 프로필, 이용 내역"]
  domain --> merchant["merchant<br/>가맹점 회원, 사업자 정보, QR, 대시보드, 정산 조회"]
  domain --> account["account<br/>소비자·가맹점 연결 은행 계좌"]
  domain --> wallet["wallet<br/>소비자·가맹점 서비스 월렛"]
  domain --> institution["institution<br/>참조 기관 목록과 기관 코드"]
  domain --> transaction["transaction<br/>충전, 환전, 결제, 결제 취소, 은행/블록체인 거래 식별자"]

  client --> bank["bank<br/>hangang-pay-bank API 호출 및 요청/응답 DTO"]

  auth --> authCode["code<br/>인증 성공/오류 코드"]
  auth --> authController["controller"]
  auth --> authDto["dto"]
  auth --> authService["service"]

  global --> config["config<br/>Security, JPA, OpenAPI, CORS"]
  global --> code["code<br/>공통 성공/오류 코드"]
  global --> entity["entity<br/>BaseEntity"]
  global --> exception["exception<br/>BusinessException, GlobalExceptionHandler"]
  global --> logging["logging<br/>MDC 요청 로그 컨텍스트(requestId, partyId, role)"]
  global --> pagination["pagination<br/>커서 페이지네이션"]
  global --> response["response<br/>공통 API 응답 래퍼"]
  global --> security["security<br/>세션 인증 필터"]
  global --> session["session<br/>세션 attribute 상수"]
```

각 도메인은 필요한 하위 패키지만 둔다. 현재 사용 중인 기본 하위 패키지:

```text
code/
controller/
dto/
entity/
repository/
repository/jpa/
scheduler/
service/
```

Repository는 Port & Adapter 패턴을 따른다. 도메인별 포트 인터페이스는 `domain/{domain}/repository`에, Spring Data JPA 인터페이스는 필요 시 `domain/{domain}/repository/jpa`에 둔다.

## DTO 패키지 규칙

- 각 도메인·`auth`·`client/bank`의 DTO는 `dto/request/`(요청)와 `dto/response/`(응답: `Response`·`Item`·`Detail`)로 나눈다.
- request도 response도 아닌 공용 타입(enum, 값 객체)은 `dto/` 루트에 둔다. 예: `UserHistoryType`, `MerchantQrPayload`, `client/bank/dto`의 `BankActResult`·`BankExchangeStatus`·`MerchantRedeemAccountInfo`.
- `transaction`은 관객이 여럿(user·bank)이라 `dto/user/{request,response}`와 `dto/bank/`로 관객을 먼저 구분한다. 단일 관객 도메인은 `dto/{request,response}` flat 구조를 쓴다.

## Code 패키지 규칙

- 도메인·`auth`·`client`의 응답 코드 enum은 `code/` 바로 아래에 둔다. 예: `domain/user/code/UserErrorCode`, `domain/user/code/UserSuccessCode`, `auth/code/AuthErrorCode`, `auth/code/AuthSuccessCode`.
- `code/error/`·`code/success/` 하위 패키지는 두지 않는다.
- 예외: `global/code`만 공통 베이스/공통 분류를 위해 `global/code/error`, `global/code/success`를 유지한다.

## Transaction 하위 패키지

`transaction` 도메인은 플로우(PAYMENT/CHARGE/EXCHANGE/CANCEL)가 공통 구조를 공유하므로 아래 추가 하위 패키지를 둔다.

```text
transaction/
  controller/
  service/
    payment/   PaymentCommandService, PaymentQueryService, PaymentReconcileService, PaymentStateWriter
    charge/    ChargeCommandService, ChargeQueryService, ChargeStateWriter
    exchange/  ExchangeCommandService, ExchangeQueryService, ExchangeReconcileService, ExchangeStateWriter
    cancel/    CancelCommandService, CancelReconcileService, CancelStateWriter   # 취소 조회는 결제 내역에 흡수 → QueryService 없음
    history/   HistoryQueryService                            # 전 플로우 통합 내역(getAllHistories)
    support/   BankCallExecutor                               # payment·cancel 공용 은행 재시도 엔진
    #  각 폴더: 인터페이스(루트) + v1/{이름}V1(현재 구현) + v0/(향후 대체 구현 예약)
  scheduler/
    ReconcileScheduler             # 결제·취소·환전 reconcile (대상 수집 + 반복 + EXPIRED sweep)
    IntentExpiryScheduler          # 결제·충전·환전 intent 만료
  dto/
    user/request/    # 사용자 → BE 요청
    user/response/   # BE → 사용자 응답
    bank/            # BE ↔ hangang-pay-bank 연동 보조 DTO (BankOutcome)
  internal/, infra/redis/, entity/, repository/, code/
```

- 서비스 컴포넌트(Command/Query/StateWriter, {Flow}ReconcileService(payment·cancel·exchange), BankCallExecutor)는 **인터페이스 + 버전 구현체**로 둔다. 인터페이스는 플로우 폴더 루트에 원래 이름으로, 현재 구현은 `v1/{이름}V1`에 두고 `@Service`/`@Component`를 붙인다. 호출처·상호 참조는 **인터페이스 타입**을 주입한다(단일 구현이라 `@Qualifier` 불필요, 대체 구현 추가 시 `@Primary`/`@Qualifier`). `v0/`는 향후 대체 구현용 예약 폴더(`.gitkeep`).
- 플로우별 폴더(payment/charge/exchange/cancel)에 Command·Query·StateWriter를 모으고 이름을 `{Flow}CommandService`/`{Flow}QueryService`/`{Flow}StateWriter`로 통일한다. 통합 내역은 `history/HistoryQueryService`, payment·cancel 공용 은행 재시도 엔진은 `support/BankCallExecutor`로 분리한다. (구 `TransactionCommandService`/`TransactionQueryService`/`service/writer/`는 제거됨.)
- 스케줄러는 관심사(복구 / intent 만료)별로 나눈다. `@SchedulerLock`의 `name`은 전역 고유해야 한다.

## Domain Ownership

| Domain | Responsibility |
| --- | --- |
| `party` | 소비자와 가맹점의 공통 상위 식별자 |
| `user` | 소비자 회원 정보, 소비자 프로필, 이용 내역, 마이페이지 메인 프로필 정보 |
| `merchant` | 가맹점 회원 정보, 사업자 정보, QR, 대시보드, 결제/정산 조회 |
| `account` | 소비자와 가맹점이 등록한 외부 은행 계좌 |
| `wallet` | 소비자와 가맹점의 서비스 월렛 |
| `institution` | BE에서 참조하는 기관 목록과 기관 코드/식별자 조회 |
| `transaction` | 충전, 환전, 결제, 결제 취소와 블록체인/은행 거래 식별자 |
| `auth` | 세션 인증, 로그인, 로그아웃, 회원가입, SMS/계좌 인증 |
| `client/bank` | `hangang-pay-bank` API 호출과 은행 API 요청/응답 DTO |

## Placement Rules

- 은행 원장 계좌, 은행 보유 지갑, 컨트랙트 주소와 배포 책임은 `hangang-pay-bank`에 둔다.
- BE에서 은행 기능이 필요하면 `client/bank`를 통해 `hangang-pay-bank` API를 호출한다.
- BE `institution` 도메인은 계좌/지갑/거래에서 참조할 기관 캐시만 담당한다.
- `account` 도메인은 소비자/가맹점이 등록한 연결 은행 계좌만 담당한다.
- `wallet` 도메인은 소비자/가맹점이 사용하는 서비스 월렛만 담당한다.
- 결제 취소 API는 가맹점 API에 노출하되, 원본 데이터는 `transaction` 도메인에서 관리한다.
- 환전은 소비자와 가맹점 모두 사용할 수 있으므로 공통 `transaction` 도메인 기능으로 둔다.
- 마이페이지 메인 화면의 사용자 정보는 별도 `mypage` 도메인을 만들지 않고 `user` 도메인에서 담당한다.
- 마이페이지 화면의 결제/충전/환전/계좌 메뉴는 각 도메인 API로 이동하는 진입점이며, 메인 조회 API 책임에 포함하지 않는다.

## Service Boundaries

- 계좌 관리는 소비자와 가맹점 모두 사용한다. API는 공통 `/accounts`를 쓰고, 현재 세션의 `partyId`로 자신의 계좌만 다룬다.
- 소비자와 가맹점 서비스가 분리되어 있더라도 계좌의 핵심 비즈니스 로직은 `account` 서비스 계층에 둔다.
- 역할별 응답 모양이 달라지면 controller 또는 DTO 계층에서 분기한다.
- 충전은 소비자 전용이다.
- 환전은 소비자와 가맹점 공통이다.
- 가맹점 매출/결제/정산 내역 조회는 가맹점 전용이다.

## Coding Rules

- 컨트롤러에 비즈니스 로직을 작성하지 않는다.
- 엔티티를 API 응답으로 직접 반환하지 않는다.
- DTO 변환을 명시적으로 수행한다.
- `@Autowired` 필드 주입을 사용하지 않는다.
- 생성자 주입과 Lombok `@RequiredArgsConstructor`를 사용한다.
- 기존 스타일을 우선한다.
