package family.fisa.hangangpaybank.global.init;

import family.fisa.hangangpaybank.domain.account.entity.BankAccount;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.account.repository.BankAccountRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile({"local", "prod"})
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final InstitutionRepository institutionRepository;
    private final BankAccountRepository bankAccountRepository;

    @Value("${blockchain.rpc-endpoint.bok:${BOK_RPC_ENDPOINT:http://localhost:18545}}")
    private String bokRpcEndpoint;

    @Value("${blockchain.rpc-endpoint.wr:${WR_RPC_ENDPOINT:http://localhost:18545}}")
    private String wrRpcEndpoint;

    @Value("${blockchain.rpc-endpoint.sh:${SH_RPC_ENDPOINT:http://localhost:18545}}")
    private String shRpcEndpoint;

    @Value("${blockchain.rpc-endpoint.hn:${HN_RPC_ENDPOINT:http://localhost:18545}}")
    private String hnRpcEndpoint;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (institutionRepository.count() > 0) {
            log.info("[bank-local-seed] skipped");
            return;
        }
        log.info("[bank-local-seed] start");

        seedInstitutions();
        seedBankAccounts();

        log.info(
                "[bank-local-seed] done: institutions={}, accounts={}",
                institutionRepository.count(),
                bankAccountRepository.count());
    }

    /**
     * BC팀 제공 institution 시드. id가 명시적이라 JPA save() 대신 native SQL. operatorWalletAddress /
     * operatorEncryptedPrivateKey / rpcEndpoint 는 컨트랙트 서명자(operator) 지갑 + Besu RPC 운영 데이터.
     */
    private void seedInstitutions() {
        jdbcTemplate.update(
                """
                                    INSERT INTO institution (
                                        id, institution_code, institution_name,
                                        operator_wallet_address, operator_encrypted_private_key,
                                        rpc_endpoint,
                                        created_at, updated_at
                                    ) VALUES
                                        (1, 'BoK', 'Bank of Korea',
                                         '0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73',
                                         '8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63',
                                         ?,
                                         NOW(6), NOW(6)),
                                        (2, 'WR', 'Woori Bank',
                                         '0x627306090abaB3A6e1400e9345bC60c78a8BEf57',
                                         'c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3',
                                         ?,
                                         NOW(6), NOW(6)),
                                        (3, 'SH', 'Shinhan Bank',
                                         '0xf17f52151EbEF6C7334FAD080c5704D77216b732',
                                         'ae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f',
                                         ?,
                                         NOW(6), NOW(6)),
                                        (4, 'HN', 'Hana Bank',
                                         '0xE9BA79E62a58225065bF24313896CD332dAFCB3C',
                                         'fdad4ce4c7c8382ea0357ad12071156ba54963cabed82f415e24c43f537fe784',
                                         ?,
                                         NOW(6), NOW(6))
                                    """,
                bokRpcEndpoint,
                wrRpcEndpoint,
                shRpcEndpoint,
                hnRpcEndpoint);
    }

    private void seedBankAccounts() {
        Institution woori = institutionRepository.findById(2L).orElseThrow();
        Institution shinhan = institutionRepository.findById(3L).orElseThrow();

        bankAccountRepository.save(
                BankAccount.builder()
                        .institution(woori)
                        .accountNumber("1002123456789")
                        .ownerName("테스트유저")
                        .balance(new BigDecimal("1000000.0000"))
                        .build());
        bankAccountRepository.save(
                BankAccount.builder()
                        .institution(shinhan)
                        .accountNumber("110987654321")
                        .ownerName("한강떡볶이")
                        .balance(BigDecimal.ZERO.setScale(4))
                        .build());
        bankAccountRepository.save(
                BankAccount.builder()
                        .institution(shinhan)
                        .accountNumber("110111222333")
                        .ownerName("환불가능유저")
                        .balance(new BigDecimal("1000000.0000"))
                        .build());
    }
}
