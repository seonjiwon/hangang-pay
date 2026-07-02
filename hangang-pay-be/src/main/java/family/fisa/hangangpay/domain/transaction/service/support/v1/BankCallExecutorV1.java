package family.fisa.hangangpay.domain.transaction.service.support.v1;

import family.fisa.hangangpay.client.bank.BankErrorInterpreter;
import family.fisa.hangangpay.client.bank.exception.BankException;
import family.fisa.hangangpay.domain.transaction.dto.bank.BankOutcome;
import family.fisa.hangangpay.domain.transaction.service.support.BankCallExecutor;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankCallExecutorV1 implements BankCallExecutor {

    private static final long BANK_RETRY_DELAY_MILLIS = 200L;

    private final BankErrorInterpreter bankErrorInterpreter;

    /** Bank 쓰기 호출 + 일시적 오류 1회 재시도를 한다. bank가 transactionUuid로 멱등 처리를 하므로 재호출은 안전하다. */
    @Override
    public <T> BankOutcome<T> callBankWithRetry(
            Supplier<T> bankCall,
            Map<String, BaseErrorCode> failCodeMap,
            BaseErrorCode fallbackCode) {
        // 1. 1차 시도
        try {
            return BankOutcome.success(bankCall.get()); // Supplier 로 제네릭하게 호출
        } catch (BankException first) {
            if (!bankErrorInterpreter.isRetryable(first.getError())) {
                // 비지니스 로직상 불가능한 것들은 FAILED 처리 (ex: 잔액부족)
                return BankOutcome.failed(
                        bankErrorInterpreter.resolveTerminalCode(
                                first.getError(), failCodeMap, fallbackCode));
            }
            log.warn("Bank 호출 일시적 오류, 1회 재시도. reason={}", first.getMessage());

            // 2. 백오프 대기 중 인터럽트되면 재시도 포기하고 UNKNOWN (스케줄러가 정산)
            if (!sleepBeforeRetry()) {
                log.warn("재시도 대기 중 인터럽트 발생 - UNKNOWN으로 변경");
                return BankOutcome.unknown();
            }
        }

        // 3. 재시도 (같은 client 쓰기 호출)
        try {
            return BankOutcome.success(bankCall.get());
        } catch (BankException retry) {
            if (bankErrorInterpreter.isRetryable(retry.getError())) {
                return BankOutcome.unknown(); // 여전히 불확실한 것들은 UNKNOWN 처리 후 스케줄러에게 위임
            }
            // 재시도 중 종단 실패로 확정 (보상의 보상을 하지않기 위함)
            return BankOutcome.failed(
                    bankErrorInterpreter.resolveTerminalCode(
                            retry.getError(), failCodeMap, fallbackCode));
        }
    }

    /** 백오프 대기 - 인터럽트 되면 flag 복원 후 false -> 호출부가 UNKNOWN 처리 */
    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(BANK_RETRY_DELAY_MILLIS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 플래그 복원 (셧다운 로직이 인지)
            return false;
        }
    }
}
