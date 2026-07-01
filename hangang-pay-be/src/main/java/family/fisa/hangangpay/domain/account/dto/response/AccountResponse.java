package family.fisa.hangangpay.domain.account.dto.response;

import family.fisa.hangangpay.domain.account.entity.Account;

/** 계좌 목록 조회 응답 항목 */
public record AccountResponse(
        /** 계좌 식별자 */
        Long accountId,
        /** 금융기관 코드 */
        String institutionCode,
        /** 금융기관명 */
        String bankName,
        /** 마스킹 처리된 계좌번호 */
        String maskedAccountNumber,
        /** 계좌 유형 PRIMARY 또는 SECONDARY */
        String accountType) {

    /** 엔티티를 응답 DTO로 변환하는 팩토리 메서드 */
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getInstitution().getInstitutionCode(),
                account.getInstitution().getInstitutionName(),
                maskAccountNumber(account.getAccountNumber()),
                account.getAccountType().name());
    }

    /** 뒤 4자리만 노출하고 앞 자리를 원래 길이만큼 별표로 마스킹 처리하는 계좌번호 변환 메서드 */
    private static String maskAccountNumber(String accountNumber) {
        // 계좌번호가 없거나 4자리 미만인 경우 기본 마스킹 반환
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        // 앞 자리를 원래 길이에 맞게 별표로 대체하고 마지막 4자리만 노출
        String masked = "*".repeat(accountNumber.length() - 4);
        return masked + accountNumber.substring(accountNumber.length() - 4);
    }
}
