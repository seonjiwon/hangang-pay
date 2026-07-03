package family.fisa.hangangpaybank.domain.institution.dto.response;

import family.fisa.hangangpaybank.domain.institution.entity.Institution;

public record InstitutionResponse(Long id, String institutionCode, String institutionName) {

    public static InstitutionResponse from(Institution institution) {
        // 1. Entity의 핵심 필드만 노출 (operator 지갑/키·rpc 등 운영 데이터는 외부 노출 안 함)
        return new InstitutionResponse(
                institution.getId(),
                institution.getInstitutionCode(),
                institution.getInstitutionName());
    }
}
