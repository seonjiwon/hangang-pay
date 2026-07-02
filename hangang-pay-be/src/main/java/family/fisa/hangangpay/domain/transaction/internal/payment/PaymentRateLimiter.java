package family.fisa.hangangpay.domain.transaction.internal.payment;

public interface PaymentRateLimiter {
    void checkIntentRateLimit(Long partyId, Long merchantPartyId);

    void checkExecutionRateLimit(Long partyId, Long merchantPartyId, String transactionUuid);

    void checkReconcileRateLimit(Long partyId, String transactionUuid);

    void checkBankOutboundRateLimit();
}
