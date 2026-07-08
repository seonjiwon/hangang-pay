package family.fisa.hangangpay.domain.transaction.internal.payment;

import java.util.Optional;

public interface PaymentIntentDedupStore {

    /**
     * 결제 intent 중복 선점(dedup). fingerprint를 키로 30초 창에서 transactionUuid를 예약한다.
     *
     * @param fingerprint 결제 의도 지문(송신자·수신자·금액 해시)
     * @param newTransactionUuid 이번 요청이 새로 발급한 uuid
     * @return 이미 같은 fingerprint가 선점돼 있으면 그 기존 uuid, 처음 선점이면 empty
     */
    Optional<String> reserve(String fingerprint, String newTransactionUuid);
}
