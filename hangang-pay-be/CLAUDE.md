# Backend: hangang-pay-be

Spring Boot 3.5 REST API. 세션 기반 인증. 은행/블록체인 처리는 `hangang-pay-bank` API로 연동.

## Behavioral Guidelines

LLM 코딩 실수를 줄이기 위한 작업 원칙. 프로젝트별 지침과 함께 적용한다.

**Tradeoff:** 속도보다 신중함에 무게를 둔다. 단순한 작업에서는 상황에 맞게 판단한다.

```mermaid
flowchart TD
  start["작업 요청 확인"] --> think["1. 코딩 전에 생각하기"]
  think --> simple["2. 단순함 우선"]
  simple --> surgical["3. 외과적 변경"]
  surgical --> goal["4. 목표 기반 실행"]
  goal --> verify["검증 후 완료"]

  think --> assumptions["가정 명시<br/>불확실하면 질문<br/>해석이 여러 개면 드러내기"]
  simple --> minimum["요청을 해결하는 최소 코드<br/>추측성 기능 금지<br/>불필요한 추상화 금지"]
  surgical --> scoped["필요한 파일과 라인만 수정<br/>기존 스타일 유지<br/>관련 없는 정리 금지"]
  goal --> criteria["성공 기준 정의<br/>테스트/빌드/재현으로 확인<br/>통과할 때까지 반복"]
```

### 1. Think Before Coding

**가정하지 않는다. 헷갈림을 숨기지 않는다. 트레이드오프를 드러낸다.**

- 가정을 명시한다. 확실하지 않으면 질문한다.
- 해석이 여러 개라면 조용히 하나를 고르지 말고 선택지를 제시한다.
- 더 단순한 접근이 있으면 말한다. 필요하면 요구사항에도 이견을 낸다.
- 불명확한 점이 있으면 멈춘다. 무엇이 헷갈리는지 말하고 질문한다.

### 2. Simplicity First

**문제를 해결하는 최소 코드. 추측성 구현은 하지 않는다.**

- 요청받지 않은 기능은 추가하지 않는다.
- 한 번만 쓰는 코드에 추상화를 만들지 않는다.
- 요구되지 않은 유연성이나 설정 가능성을 넣지 않는다.
- 불가능한 시나리오를 위한 에러 처리를 만들지 않는다.
- 200줄로 쓴 코드가 50줄로 가능하다면 다시 단순화한다.

### 3. Surgical Changes

**꼭 필요한 곳만 건드린다. 내가 만든 흔적만 정리한다.**

- 주변 코드, 주석, 포맷을 괜히 개선하지 않는다.
- 고장 나지 않은 코드를 리팩터링하지 않는다.
- 다르게 하고 싶어도 기존 스타일을 따른다.
- 관련 없는 죽은 코드를 발견하면 언급만 하고 삭제하지 않는다.
- 내가 만든 변경 때문에 unused가 된 import, 변수, 함수만 제거한다.

판단 기준: 바뀐 모든 줄은 사용자의 요청과 직접 연결되어야 한다.

### 4. Goal-Driven Execution

**성공 기준을 정하고, 검증될 때까지 반복한다.**

```text
1. [단계] -> 검증: [확인 방법]
2. [단계] -> 검증: [확인 방법]
3. [단계] -> 검증: [확인 방법]
```

성공 기준이 강하면 독립적으로 반복할 수 있다. "동작하게 만들기"처럼 약한 기준은 계속 확인이 필요하다.

## Project Docs

- ERD 상세: `docs/erd.md`
- 패키지/도메인 책임: `docs/package.md`
- REST API 목록과 권한 규칙: `docs/rest_api.md`
- 관측성(Sentry/Grafana Cloud/Alloy): `docs/observability.md`

## Definition of Done

- 요청 시작 전에 목표 1줄과 DoD를 먼저 확정한다.
- 작업 범위를 모듈/기능 단위로 제한한다.
- 충돌 시 문서 우선순위를 따른다.
- 검증 명령을 실행한다. 기본은 `./gradlew test`다.
- 최종 응답은 고정 포맷으로 보고한다.

## Document Priority

1. 가장 가까운 경로의 `AGENTS.md`
2. 루트 `AGENTS.md`
3. `CLAUDE.md`
4. 변경 성격별 상세 문서
5. 실제 코드

현재 루트 `AGENTS.md`는 `@CLAUDE.md`만 포함한다. Claude와 Codex가 같은 규칙을 보도록 `CLAUDE.md`를 공통 원본으로 사용한다. 하위 경로에 더 가까운 `AGENTS.md`가 생기면 해당 파일이 국소 규칙으로 우선한다.

변경 성격별 상세 문서 우선순위:

- API 경로/요청/응답/권한 충돌: `docs/rest_api.md`
- 엔티티/테이블/enum/관계 충돌: `docs/erd.md`
- 패키지 위치/도메인 책임 충돌: `docs/package.md`

문서와 코드가 충돌하면 근거 문서를 기준으로 정렬하고, 결과를 보고서에 명시한다.

## Mandatory Work Intake

모든 코드 작업 요청은 아래를 먼저 고정한다.

1. 목표 1줄 + DoD
2. 작업 단위: 모듈/기능
3. 변경 권한 범위
4. 타깃 테스트 식별자
5. 검증 명령
6. 응답 포맷

변경 권한 범위에는 수정 가능 파일/디렉터리, 수정 금지 파일/디렉터리, 리네이밍 허용 여부를 포함한다. 리네이밍은 명시적 승인 없으면 금지한다.

## Default Change Permission Policy

요청에 별도 지정이 없으면 아래 기본 정책을 적용한다.

- 수정 가능: 작업 대상 모듈 + 직접 연관 테스트 + 직접 연관 문서
- 수정 금지: 무관 모듈, 인프라/배포 파일, 광범위 리네이밍
- 리네이밍: 명시적 승인 없으면 금지
- 프론트엔드 또는 상위 디렉터리 작업은 명시적 요청 없으면 금지

## Fixed Verification Command

1. 타깃 테스트 실행: `./gradlew test --tests <target>`
2. 전체 테스트 실행: `./gradlew test`

실패 시 실패 원인과 영향 범위를 보고한다. 테스트 타깃을 특정할 수 없는 문서 전용 변경은 테스트 실행 대신 변경 파일과 검토한 근거를 보고한다.

## Fixed Response Format

최종 응답은 아래 순서로 보고한다.

1. 변경 요약 3줄
2. 파일 목록
3. 리스크
4. 다음 액션

## Service Naming Convention

- 도메인 서비스는 읽기/쓰기를 분리한다.
  - `XxxQueryService` — 조회 전용. `@Transactional(readOnly = true)` 적용.
  - `XxxCommandService` — 쓰기 전용. `@Transactional` 적용.
- 신규 서비스는 단일 서비스(`XxxService`)로 만들지 않는다.
- 과도기 단일 서비스였던 `AccountService`/`WalletService`는 제거되었다. 계좌 도메인은 `AccountQueryService`/`AccountCommandService`로 분리한다.
- `auth`·`merchant`의 기능 단위 서비스(`AuthService`, `VerificationService`, `UserRegistrationService`, `MerchantRegistrationService`, `MerchantQrService`)는 도메인 CRUD 서비스가 아니라 의도된 기능 서비스이므로 Query/Command 분리 대상이 아니다.

## Method Naming Convention

- 조회는 repository에서 `find*`, service에서 `get*`를 쓴다.
- 없으면 예외를 던지는 필수 존재 조회는 `getBy{Key}` 관용구를 쓴다. 예: `getByPartyId`, `getByCode`, `getById`.
- 신규 저장은 `create*`. 단, 회원 온보딩은 도메인 용어 `register*`를 유지한다(`registerUser`, `registerMerchant`).
- 수정은 `update*`(`change*`/`modify*` 금지), 삭제는 `delete*`(`remove*` 금지).

## DTO Naming Convention

- 커서 페이지네이션 목록의 원소 DTO는 `Item` suffix를 사용한다. 예: `PaymentHistoryItem`, `ChargeHistoryItem`, `ExchangeHistoryItem`
- `CursorPageResponse<XxxItem>` 형태로 감싸서 반환한다.
- `Response` suffix는 단일 객체 응답 DTO에만 사용한다. 예: `UserProfileResponse`
- `Item`은 `CursorItem` 인터페이스를 구현하고 `getCursorCreatedAt()` / `getCursorId()`를 제공한다.
- `Item` DTO는 Java `record`를 기본으로 한다.
- `Item` DTO에는 Lombok `@Builder`를 붙이지 않는다. record canonical constructor 또는 정적 팩토리로 생성한다.
- 모든 요청/응답 DTO는 Java `record`를 기본으로 한다. Lombok `@Getter`/`@Builder` 클래스로 새로 만들지 않는다.
- 요청 DTO는 `{Noun}{Verb}Request` 어순을 쓴다. 예: `AccountCreateRequest`, `PaymentIntentCreateRequest`, `BankAccountCreateRequest`.
- 플로우 실행 응답은 `{Flow}ExecuteResponse`로 통일한다. 예: `ChargeExecuteResponse`, `ExchangeExecuteResponse`, `PaymentExecuteResponse`.
- 단일 상세 응답은 `Response`를 기본으로 하되, 다른 도메인 DTO와 이름이 겹치면 `Detail` suffix를 허용한다. 예: `MerchantPaymentDetail`, `UserChargeHistoryDetail`.

## Session Attribute Convention

- 로그인 시 세션에 소비자는 `userId`, 가맹점은 `merchantId`, 공통으로 `partyId`와 `role`을 저장한다.
- 컨트롤러에서 세션 값은 `@SessionAttribute`로 꺼낸다. `HttpSession`을 직접 파라미터로 받지 않는다.
- 컨트롤러 생성 시 세션 attribute 이름은 문자열 리터럴 대신 `SessionAttributeNames` 상수를 사용한다.
- 예: `@SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId`, `@SessionAttribute(SessionAttributeNames.USER_ID) Long userId`
- `@RequestParam`으로 인증 정보를 받지 않는다. 인증된 사용자 식별자는 반드시 세션에서 추출한다.
- `AccountController`는 `@SessionAttribute(name = SessionAttributeNames.PARTY_ID, required = false)`로 세션에서 `partyId`를 꺼낸다. 로그인 세션이 항상 보장되기 전까지 임시로 `required = false`를 둔다.

## Root-Level Architecture Rules

- API는 `/api/v1` prefix와 `docs/rest_api.md`의 경로/권한 매핑을 따른다.
- 공통 응답은 `global/response`의 공통 API 응답 래퍼를 유지한다.
- 비즈니스 예외는 `global/exception/BusinessException` + `ErrorCode`를 사용한다.
- DB 변경 시 엔티티/JPA와 `docs/erd.md`를 함께 갱신한다.
- API 변경 시 controller/dto와 `docs/rest_api.md`를 함께 갱신한다.
- 패키지 위치나 도메인 책임 변경 시 `docs/package.md`를 함께 갱신한다.
- 세션 인증을 JWT로 변경하지 않는다.
- Repository는 Port & Adapter 패턴을 사용한다. 도메인 루트에 포트 인터페이스(`domain/xxx/repository/XxxRepository`), `jpa/` 하위에 JPA 인터페이스(`XxxJpaRepository`)와 어댑터 구현체(`XxxRepositoryImpl`)를 둔다.

## Logging Policy

- 비즈니스 상태 전환, 외부 연동, 인증, 결제, 충전, 환전, 블록체인 처리에는 적절한 로그를 남긴다.
- 단순 DTO, 매핑, 검증 코드에는 불필요한 로그를 추가하지 않는다.
- SLF4J와 Lombok `@Slf4j` 사용을 기본으로 한다.
- `ERROR`: 비즈니스 처리 실패, 외부 연동 실패, 예외 catch 후 복구 불가 상황. 스택트레이스 포함.
- `WARN`: 재시도/폴백 동작, 검증 실패 등 운영자가 인지해야 하는 비정상 상황.
- `INFO`: 주요 비즈니스 이벤트, 상태 전환, 외부 호출 시작/종료, 인증 결과.
- `DEBUG`: 개발/트러블슈팅용 상세 정보. 프로덕션 기본 비활성.
- 필수 컨텍스트: 식별자(`userId`, `merchantId`, `partyId`, `paymentId`, `txHash`, `requestId` 등), 행위, 결과/에러코드.
- 비밀번호, 토큰, 개인정보, 계좌번호 전체, private key 평문 로깅 금지.
- `e.printStackTrace()` 및 `System.out/err` 사용 금지.
- 문자열 결합 대신 SLF4J 파라미터 바인딩을 사용한다. 예: `log.info("paymentId={}", paymentId)`
- 외부 연동 호출은 진입/종료/실패 3지점 로그를 남긴다.
- 예외 처리 시 catch 후 무음 처리하지 않는다. 최소 `WARN` 이상으로 기록한다.

## Docs Sync Rule

다음 변경이 있으면 `docs`도 같이 갱신한다.

- API 경로/요청/응답/권한
- 엔티티/테이블/인덱스/제약/enum
- 모듈 구조/도메인 책임/패키지 위치
- 운영 환경변수
- 외부 연동 방식

## Reference Documents

- `CLAUDE.md`
- `docs/rest_api.md`
- `docs/erd.md`
- `docs/package.md`

## Tech Stack

- Java 17, Spring Boot 3.5.14, Gradle
- Spring Security (세션 인증, JWT 아님)
- Spring Data JPA + MySQL
- SpringDoc OpenAPI 2.8.16 -> `/swagger-ui/index.html`
- Actuator + Micrometer Prometheus -> `/actuator/prometheus`
- RestClient 기반 `hangang-pay-bank` 연동
- Lombok: `@RequiredArgsConstructor` 사용, `@Autowired` 금지

## Commands

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew test
./gradlew build -x test
```

`hangang-pay-be/`에서 실행. `./gradlew` 사용(시스템 gradle 금지).

## Package Structure

자세한 내용은 `docs/package.md`를 따른다.

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
  global --> pagination["pagination<br/>커서 페이지네이션"]
  global --> response["response<br/>공통 API 응답 래퍼"]
  global --> security["security<br/>세션 인증 필터"]
  global --> session["session<br/>세션 attribute 상수"]
```

각 도메인은 필요한 하위 패키지만 둔다. 현재 사용 중인 기본 하위 패키지는 `code/`, `controller/`, `dto/`, `entity/`, `repository/`, `repository/jpa/`, `service/`, `scheduler/`다.

Repository는 Port & Adapter 패턴을 따른다. 도메인별 포트 인터페이스는 `domain/{domain}/repository`에, Spring Data JPA 인터페이스는 필요 시 `domain/{domain}/repository/jpa`에 둔다.

## API Rules

- 모든 엔드포인트 prefix: `/api/v1`
- 응답 포맷: `{ isSuccess, status, code, message, result }`
- 엔티티 직접 반환 금지. DTO 변환 필수.
- 모든 엔드포인트 SpringDoc 어노테이션 필수: `@Operation` (`@ApiResponse`는 사용하지 않는다)
- 승인번호 형식: `APV-YYYY-NNNNNNNN`
- 승인번호는 `transaction.id`를 8자리 zero padding해서 생성한다. 예: `transaction.id=25` -> `APV-2026-00000025`
- 승인번호 생성은 `transaction` 저장으로 id를 확보한 뒤 수행한다.
- 상세 API 목록과 권한 규칙은 `docs/rest_api.md`를 따른다.

## Key Business Rules

```mermaid
flowchart TD
  charge["충전<br/>할인율 10%<br/>액면 - 10% = 실 결제 금액"]
  charge --> usage{"최근 충전액의<br/>60% 이상 사용?"}
  usage -->|Yes| exchange["환전 가능"]
  usage -->|No| noExchange["환전 불가"]

  payment["결제"] --> paymentCancel["결제 취소<br/>시간 제한 없이 가능<br/>요청 주체는 가맹점"]

  coinFlow["코인 기반 처리"] --> immediateTransfer["소비자 결제 시<br/>가맹점 월렛으로 즉시 코인 이체"]
  immediateTransfer --> merchantExchange["가맹점은 쌓인 코인을<br/>1:1 비율로 계좌 환전 가능"]
  immediateTransfer --> noSettlement["별도 정산 배치·적재 없음"]
  noSettlement --> settlementHistory["정산 내역<br/>transaction의 EXCHANGE 거래<br/>기록 조회 용도"]

  accountRegistration["계좌 등록"] --> accountLimit["최대 3개"]
  accountRegistration --> ownerCheck["본인 명의"]
  accountRegistration --> oneWon["1원 인증 필수"]
  oneWon --> oneWonTx["1원 인증 트랜잭션 테이블<br/>추후 추가 예정"]
```

## Blockchain Integration

```mermaid
flowchart LR
  app["Spring Boot API"] --> bankClient["client/bank<br/>BankClient"]
  bankClient --> bank["hangang-pay-bank API"]
  bank --> web3j["Web3j"]
  web3j --> besu["Besu<br/>QBFT"]
  besu --> contract["Smart Contract"]

  contract --> mint["mint<br/>충전"]
  contract --> burn["burn<br/>환전"]
  contract --> transfer["transfer<br/>결제/취소"]
  contract --> whitelist["whitelist 등록"]

  app --> tx["transaction"]
  tx --> txHash["tx_hash<br/>블록체인 증거"]
  tx --> bankTx["bank_transaction_id<br/>은행 거래 식별자"]
```

## Error Handling

```mermaid
flowchart LR
  domainException["도메인 예외"] --> businessException["BusinessException(ErrorCode)"]
  businessException --> errorCode["ErrorCode<br/>HttpStatus status<br/>String code<br/>String message"]
  businessException --> handler["GlobalExceptionHandler<br/>@RestControllerAdvice"]
  handler --> response["공통 API 응답<br/>{ isSuccess, code, message, result }"]
```

도메인 예외는 `BusinessException(ErrorCode)`를 상속한다.

## Response Code Naming

`BaseErrorCode` / `BaseSuccessCode` 를 구현하는 enum 의 `code` 문자열은 **enum 이름과 동일**하게 둔다. HTTP 상태는 `code` 문자열에 박지 않고 `HttpStatus status` 필드로만 표현한다.

규칙:

- enum 이름 = `code` 문자열. 예: `USER_NOT_FOUND` → `"USER_NOT_FOUND"`.
- 도메인 접두어로 그룹핑한다. 예: `USER_`, `ACCOUNT_`, `PAYMENT_`, `MERCHANT_`, `WALLET_`, `EXCHANGE_`.
- 공통 영역은 `COMMON_` 접두어를 쓴다. 예: `COMMON_BAD_REQUEST`, `COMMON_OK`.
- 같은 status 의 코드가 여러 개여도 시퀀스 번호(`_0`, `_1`)를 붙이지 않는다. 의미가 다르면 enum 이름 자체를 구체적으로 짓는다.
- HTTP 상태나 숫자만으로 만든 opaque 코드(`USER404_0`, `COMMON200`)는 신규로 만들지 않는다.

예시:

```java
// Good
USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "계좌를 찾을 수 없습니다."),
COMMON_BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_BAD_REQUEST", "잘못된 요청입니다."),
CHARGE_HISTORIES_RETRIEVED(HttpStatus.OK, "CHARGE_HISTORIES_RETRIEVED", "충전 내역을 조회했습니다."),

// Bad — 신규 작성 금지
USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404_0", "..."),
OK(HttpStatus.OK, "COMMON200", "..."),
```

근거:
- 로그/응답에서 코드 문자열만 봐도 의미가 드러나 운영·디버깅이 빠르다.
- `_N` 시퀀스 관리 부담과 머지 충돌이 없다.
- FE 분기문이 자기 설명적이다.

기존 opaque 코드(`COMMON400`, `USER404_0`, `COMMON200` 등)는 별도 리팩터링 PR 로 일괄 변경한다. 신규 enum 은 처음부터 새 컨벤션을 따른다.

## Configuration

- `application.yaml`: 공통 설정. 커밋 대상.
- `application-local.yaml`: DB 연결 정보 등 secrets. 커밋 금지.

```yaml
# application-local.yaml 로컬 개발 전용 예시
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hangang_pay
    username: <username>
    password: <password>
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
```

## Testing

- `@SpringBootTest`: 통합 테스트
- `@WebMvcTest`: 컨트롤러 단위, `spring-security-test` 포함
- `@DataJpaTest`: 리포지토리, H2 in-memory. 기본 CRUD 테스트는 생략한다.
- 클래스명: `{ClassName}Test.java`

Repository 테스트 작성 기준:

- 생략 가능: `findById`, `save`, `delete`, 단순 `findByUsername` 같은 Spring Data JPA 기본/단순 derived query.
- 작성 필요: 커스텀 `@Query`, fetch join, 집계, 페이징/정렬, 기간/상태/권한 조건, 소유권 검증 쿼리.
- 작성 필요: 결제/환전/정산 내역, `transaction.tx_hash`/`bank_transaction_id`, 주계좌 유일성처럼 돈 흐름이나 정합성에 직접 영향을 주는 조회.
- 서비스 테스트에서 같은 조건을 충분히 검증한다면 중복 repository 테스트는 추가하지 않는다.

## What NOT to Do

- 컨트롤러에 비즈니스 로직 작성 금지
- `@Autowired` 필드 주입 금지
- 엔티티를 응답으로 직접 반환 금지
- `application-local.yaml` 커밋 금지
- 세션 인증을 JWT로 변경하지 말 것
- `Item` suffix record DTO에 Lombok `@Builder` 붙이지 말 것
- `bank_account`, `bank_wallet`, `blockchain_ledger`, contract 실행 책임을 BE 도메인으로 가져오지 말 것
