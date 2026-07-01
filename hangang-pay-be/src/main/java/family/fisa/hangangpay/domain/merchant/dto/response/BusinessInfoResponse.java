package family.fisa.hangangpay.domain.merchant.dto.response;

public record BusinessInfoResponse(
        String businessNumber,
        String merchantName,
        String ownerName,
        String address,
        String businessType) {}
