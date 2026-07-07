package family.fisa.hangangpay.global.init;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.repository.jpa.InstitutionJpaRepository;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.jpa.MerchantJpaRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.service.WalletCommandService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile({"local", "prod"})
@RequiredArgsConstructor
@SuppressWarnings("java:S2068") // 데모/실험용 시드 테스트 계정 (local·prod 데모 환경 한정)
public class LocalDataInitializer implements ApplicationRunner {

    private final PartyRepository partyRepository;
    private final UserRepository userRepository;
    private final MerchantJpaRepository merchantJpaRepository;
    private final InstitutionJpaRepository institutionJpaRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final WalletCommandService walletCommandService;
    private final PasswordEncoder passwordEncoder;
    private final BankClient bankClient;

    // created_at 과거 시각 덮어쓰기용 JDBC 템플릿
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (partyRepository.count() > 0) {
            log.info("[be-local-seed] skipped");
            return;
        }
        log.info("[be-local-seed] start");

        // BC팀 SQL과 동일한 id/code/name (BE는 institution 캐시 역할)
        Institution bok =
                institutionJpaRepository.save(
                        Institution.builder()
                                .id(1L)
                                .institutionCode("BoK")
                                .institutionName("한국은행")
                                .build());
        Institution woori =
                institutionJpaRepository.save(
                        Institution.builder()
                                .id(2L)
                                .institutionCode("WR")
                                .institutionName("우리은행")
                                .build());
        Institution shinhan =
                institutionJpaRepository.save(
                        Institution.builder()
                                .id(3L)
                                .institutionCode("SH")
                                .institutionName("신한은행")
                                .build());
        institutionJpaRepository.save(
                Institution.builder().id(4L).institutionCode("HN").institutionName("하나은행").build());

        Party userParty = partyRepository.save(Party.of(PartyType.USER));
        userRepository.save(
                User.builder()
                        .party(userParty)
                        .username("테스트유저")
                        .passwordHash(passwordEncoder.encode("password"))
                        .paymentPinHash(passwordEncoder.encode("123456"))
                        .phoneNumber("01012345678")
                        .birthDate(LocalDate.of(1995, 1, 1))
                        .region("성동구")
                        .build());
        Account userAccount =
                accountRepository.save(
                        Account.builder()
                                .party(userParty)
                                .institution(woori)
                                .accountType(AccountType.PRIMARY)
                                .accountNumber("1002123456789")
                                .build());
        Wallet userWallet = walletCommandService.createWallet(userParty, bok);

        Party merchantParty = partyRepository.save(Party.of(PartyType.MERCHANT));
        merchantJpaRepository.save(
                Merchant.builder()
                        .party(merchantParty)
                        .username("테스트가맹점")
                        .passwordHash(passwordEncoder.encode("password"))
                        .paymentPinHash(passwordEncoder.encode("123456"))
                        .businessNumber("1234567890")
                        .merchantName("한강떡볶이")
                        .ownerName("홍길동")
                        .phoneNumber("0226001234")
                        .address("서울시 성동구 왕십리로 222")
                        .latitude(new BigDecimal("37.5635030"))
                        .longitude(new BigDecimal("127.0369670"))
                        .build());
        Account merchantAccount =
                accountRepository.save(
                        Account.builder()
                                .party(merchantParty)
                                .institution(shinhan)
                                .accountType(AccountType.SETTLEMENT)
                                .accountNumber("110987654321")
                                .build());
        Wallet merchantWallet = walletCommandService.createWallet(merchantParty, bok);

        // user2 — 환불 가능 시나리오 (충전 후 결제 65,000 >= threshold 60,000, 잔액 35,000)
        Party user2Party = partyRepository.save(Party.of(PartyType.USER));
        userRepository.save(
                User.builder()
                        .party(user2Party)
                        .username("환불가능유저")
                        .passwordHash(passwordEncoder.encode("password"))
                        .paymentPinHash(passwordEncoder.encode("123456"))
                        .phoneNumber("01099999999")
                        .birthDate(LocalDate.of(1990, 6, 15))
                        .region("성동구")
                        .build());
        Account user2Account =
                accountRepository.save(
                        Account.builder()
                                .party(user2Party)
                                .institution(shinhan)
                                .accountType(AccountType.PRIMARY)
                                .accountNumber("110111222333")
                                .build());
        Wallet user2Wallet = walletCommandService.createWallet(user2Party, bok);

        seedHistoryTransactions(
                userParty, userAccount, userWallet, merchantParty, merchantAccount, merchantWallet);

        syncOnChainBalance(userWallet.getAddress(), new BigDecimal("57500"));
        syncOnChainBalance(merchantWallet.getAddress(), new BigDecimal("14500"));

        log.info("[be-local-seed] done");
    }

    private void seedHistoryTransactions(
            Party userParty,
            Account userAccount,
            Wallet userWallet,
            Party merchantParty,
            Account merchantAccount,
            Wallet merchantWallet) {

        // 충전 5건
        Transaction charge1 =
                transactionRepository.saveAndFlush(
                        Transaction.forCharge(
                                UUID.randomUUID().toString(),
                                userParty,
                                userAccount,
                                userWallet,
                                new BigDecimal("100000"),
                                new BigDecimal("10000"),
                                new BigDecimal("10.00")));
        charge1.markSuccess("0xCHARGETX0001", "BANK-TX-001");
        backdate(charge1.getId(), 15, 9);

        Transaction charge2 =
                transactionRepository.saveAndFlush(
                        Transaction.forCharge(
                                UUID.randomUUID().toString(),
                                userParty,
                                userAccount,
                                userWallet,
                                new BigDecimal("50000"),
                                new BigDecimal("5000"),
                                new BigDecimal("10.00")));
        charge2.markSuccess("0xCHARGETX0002", "BANK-TX-002");
        backdate(charge2.getId(), 12, 14);

        Transaction charge3 =
                transactionRepository.saveAndFlush(
                        Transaction.forCharge(
                                UUID.randomUUID().toString(),
                                userParty,
                                userAccount,
                                userWallet,
                                new BigDecimal("30000"),
                                new BigDecimal("3000"),
                                new BigDecimal("10.00")));
        charge3.markSuccess("0xCHARGETX0003", "BANK-TX-003");
        backdate(charge3.getId(), 9, 19);

        Transaction charge4 =
                transactionRepository.saveAndFlush(
                        Transaction.forCharge(
                                UUID.randomUUID().toString(),
                                userParty,
                                userAccount,
                                userWallet,
                                new BigDecimal("20000"),
                                new BigDecimal("2000"),
                                new BigDecimal("10.00")));
        charge4.markSuccess("0xCHARGETX0004", "BANK-TX-004");
        backdate(charge4.getId(), 6, 10);

        Transaction charge5 =
                transactionRepository.saveAndFlush(
                        Transaction.forCharge(
                                UUID.randomUUID().toString(),
                                userParty,
                                userAccount,
                                userWallet,
                                new BigDecimal("10000"),
                                new BigDecimal("1000"),
                                new BigDecimal("10.00")));
        charge5.markSuccess("0xCHARGETX0005", "BANK-TX-005");
        backdate(charge5.getId(), 3, 12);

        // 소비자 결제 8건 (가맹점 수취)
        Transaction payment1 =
                transactionRepository.saveAndFlush(
                        Transaction.forPayment(
                                UUID.randomUUID().toString(),
                                userParty,
                                merchantParty,
                                userWallet,
                                merchantWallet,
                                new BigDecimal("8500"),
                                "APV-2026-00000001",
                                "떡볶이"));
        payment1.markSuccess("0xPAYTX0001", null);
        backdate(payment1.getId(), 14, 12);

        Transaction payment2 =
                transactionRepository.saveAndFlush(
                        Transaction.forPayment(
                                UUID.randomUUID().toString(),
                                userParty,
                                merchantParty,
                                userWallet,
                                merchantWallet,
                                new BigDecimal("18000"),
                                "APV-2026-00000002",
                                "순대국"));
        payment2.markSuccess("0xPAYTX0002", null);
        backdate(payment2.getId(), 13, 18);

        Transaction payment3 =
                transactionRepository.saveAndFlush(
                        Transaction.forPayment(
                                UUID.randomUUID().toString(),
                                userParty,
                                merchantParty,
                                userWallet,
                                merchantWallet,
                                new BigDecimal("12000"),
                                "APV-2026-00000003",
                                "라면"));
        payment3.markSuccess("0xPAYTX0003", null);
        backdate(payment3.getId(), 11, 13);

        Transaction payment4 =
                transactionRepository.saveAndFlush(
                        Transaction.forPayment(
                                UUID.randomUUID().toString(),
                                userParty,
                                merchantParty,
                                userWallet,
                                merchantWallet,
                                new BigDecimal("25000"),
                                "APV-2026-00000004",
                                "갈비탕"));
        payment4.markSuccess("0xPAYTX0004", null);
        backdate(payment4.getId(), 10, 19);

        Transaction payment5 =
                transactionRepository.saveAndFlush(
                        Transaction.forPayment(
                                UUID.randomUUID().toString(),
                                userParty,
                                merchantParty,
                                userWallet,
                                merchantWallet,
                                new BigDecimal("5000"),
                                "APV-2026-00000005",
                                "음료"));
        payment5.markSuccess("0xPAYTX0005", null);
        backdate(payment5.getId(), 8, 12);

        Transaction payment6 =
                transactionRepository.saveAndFlush(
                        Transaction.forPayment(
                                UUID.randomUUID().toString(),
                                userParty,
                                merchantParty,
                                userWallet,
                                merchantWallet,
                                new BigDecimal("6000"),
                                "APV-2026-00000006",
                                "김밥"));
        payment6.markSuccess("0xPAYTX0006", null);
        backdate(payment6.getId(), 5, 11);

        Transaction payment7 =
                transactionRepository.saveAndFlush(
                        Transaction.forPayment(
                                UUID.randomUUID().toString(),
                                userParty,
                                merchantParty,
                                userWallet,
                                merchantWallet,
                                new BigDecimal("13000"),
                                "APV-2026-00000007",
                                "냉면"));
        payment7.markSuccess("0xPAYTX0007", null);
        backdate(payment7.getId(), 2, 20);

        Transaction payment8 =
                transactionRepository.saveAndFlush(
                        Transaction.forPayment(
                                UUID.randomUUID().toString(),
                                userParty,
                                merchantParty,
                                userWallet,
                                merchantWallet,
                                new BigDecimal("35000"),
                                "APV-2026-00000008",
                                "족발"));
        payment8.markSuccess("0xPAYTX0008", null);
        backdate(payment8.getId(), 1, 13);

        // 가맹점 정산(환전) 4건
        Transaction mExchange1 =
                transactionRepository.saveAndFlush(
                        Transaction.forExchange(
                                UUID.randomUUID().toString(),
                                merchantParty,
                                merchantWallet,
                                merchantAccount,
                                new BigDecimal("20000"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO));
        mExchange1.markSuccess("0xEXCTX0001", "BANK-TX-EXC-001");
        backdate(mExchange1.getId(), 10, 15);

        Transaction mExchange2 =
                transactionRepository.saveAndFlush(
                        Transaction.forExchange(
                                UUID.randomUUID().toString(),
                                merchantParty,
                                merchantWallet,
                                merchantAccount,
                                new BigDecimal("43000"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO));
        mExchange2.markSuccess("0xEXCTX0002", "BANK-TX-EXC-002");
        backdate(mExchange2.getId(), 7, 16);

        Transaction mExchange3 =
                transactionRepository.saveAndFlush(
                        Transaction.forExchange(
                                UUID.randomUUID().toString(),
                                merchantParty,
                                merchantWallet,
                                merchantAccount,
                                new BigDecimal("15000"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO));
        mExchange3.markSuccess("0xEXCTX0003", "BANK-TX-EXC-003");
        backdate(mExchange3.getId(), 3, 17);

        Transaction mExchange4 =
                transactionRepository.saveAndFlush(
                        Transaction.forExchange(
                                UUID.randomUUID().toString(),
                                merchantParty,
                                merchantWallet,
                                merchantAccount,
                                new BigDecimal("30000"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO));
        mExchange4.markSuccess("0xEXCTX0004", "BANK-TX-EXC-004");
        backdate(mExchange4.getId(), 1, 18);

        // 소비자 환불 1건
        Transaction uExchange =
                transactionRepository.saveAndFlush(
                        Transaction.forExchange(
                                UUID.randomUUID().toString(),
                                userParty,
                                userWallet,
                                userAccount,
                                new BigDecimal("30000"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO));
        uExchange.markSuccess("0xEXCTX0005", "BANK-TX-EXC-005");
        backdate(uExchange.getId(), 4, 17);
    }

    // seed DB 거래내역에 맞춰 온체인 잔액 동기화 (로컬 전용)
    private void syncOnChainBalance(String walletAddress, BigDecimal amount) {
        try {
            bankClient.localMint(2L, walletAddress, amount);
            log.info("[be-local-seed] 온체인 mint 완료. wallet={}, amount={}", walletAddress, amount);
        } catch (Exception e) {
            log.warn(
                    "[be-local-seed] 온체인 mint 실패 (컨트랙트 미배포 또는 bank 미실행). wallet={}",
                    walletAddress,
                    e);
        }
    }

    // created_at을 과거 시각으로 덮어씀 — @CreatedDate updatable=false라 JPA 재플러시 후에도 유지
    private void backdate(Long txId, int daysAgo, int hourOfDay) {
        LocalDateTime ts = LocalDate.now().minusDays(daysAgo).atTime(hourOfDay, 0);
        jdbcTemplate.update("UPDATE transaction SET created_at = ? WHERE id = ?", ts, txId);
    }
}
