package family.fisa.hangangpay.domain.account.service;

import family.fisa.hangangpay.domain.account.dto.response.AccountListResponse;
import family.fisa.hangangpay.domain.account.dto.response.AccountResponse;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 계좌 조회 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountQueryService {

    private final AccountRepository accountRepository;

    /** 현재 로그인한 사용자의 등록 계좌 목록 조회 메서드 */
    public AccountListResponse getAccounts(Long partyId) {
        // 파티 식별자 기준 전체 계좌 목록 조회
        List<Account> accounts = accountRepository.findAllByParty_Id(partyId);
        log.info("계좌 목록 조회: partyId={}, count={}", partyId, accounts.size());

        // 엔티티 목록을 응답 DTO 목록으로 변환
        List<AccountResponse> accountResponses =
                accounts.stream().map(AccountResponse::from).toList();

        return new AccountListResponse(accountResponses, accountResponses.size());
    }
}
