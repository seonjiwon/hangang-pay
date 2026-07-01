package family.fisa.hangangpay.domain.merchant.dto.response;

import family.fisa.hangangpay.domain.transaction.entity.TransactionType;

public record MerchantPaymentDetailResponse<T>(TransactionType transactionType, T detail) {

    public static <T> MerchantPaymentDetailResponse<T> of(
            TransactionType transactionType, T detail) {
        return new MerchantPaymentDetailResponse<>(transactionType, detail);
    }
}
