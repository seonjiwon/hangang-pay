package family.fisa.hangangpay.domain.transaction.service.exchange;

import family.fisa.hangangpay.client.bank.dto.request.ExchangeRequest;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ExchangeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import java.time.LocalDateTime;

/**
 * EXCHANGE(환전) 상태 쓰기/조회 전담.
 *
 * <p>각 메서드는 REQUIRES_NEW로 자기 트랜잭션을 열고 독립적인 Commit을 수행한다.
 */
public interface ExchangeStateWriter {

    /** intent 생성 = PENDING. transactionUuid는 서버가 발급한다(FE 신뢰 안 함). */
    ExchangeIntentResponse createIntent(
            Long partyId,
            ExchangeIntentCreateRequest request,
            AccountType depositType,
            LocalDateTime expiresAt);

    /**
     * 실행 선점(CAS): PENDING -> PROCESSING 원자적 전이. 영향 행 1이면 선점 성공으로 PROCESSING을 반환하고, 0이면 이미
     * 비-PENDING이므로 현재 상태를 재조회해 반환한다.
     */
    TransactionStatus claimForExecution(String uuid);

    /** 소유자 검증 - 남의 거래 실행 차단. claim 전에 호출한다. */
    void validateOwner(String uuid, Long partyId);

    /** bank 호출용 요청 빌드 (트랜잭션 내부에서 수행) */
    ExchangeRequest getBankRequest(String uuid);

    /** 미저장 거래를 SUCCESS로 확정하며 저장 + 응답 빌드 */
    ExchangeExecuteResponse completeExchange(
            Transaction tx, String txHash, String bankTransactionId);

    /** 현재 상태 응답 빌드 - 멱등 재요청(이미 SUCCESS/FAILED) 시 그대로 돌려주기 위함 */
    ExchangeExecuteResponse getResponse(String uuid);

    /** SUCCESS 확정 + 응답 빌드 */
    ExchangeExecuteResponse markSuccess(String uuid, String txHash, String bankTransactionId);

    /** FAILED 확정 + 응답 빌드 */
    ExchangeExecuteResponse markFailed(String uuid);

    /** UNKNOWN 확정 (retry_count++) + 응답 빌드 */
    ExchangeExecuteResponse markUnknown(String uuid);

    /** reconcile 재시도 예산 +1 (배치에서 bank가 아직 PENDING일 때) */
    void incrementRetry(String uuid);

    /** 버려진 PENDING intent 만료 처리 */
    void markExpired(String uuid);
}
