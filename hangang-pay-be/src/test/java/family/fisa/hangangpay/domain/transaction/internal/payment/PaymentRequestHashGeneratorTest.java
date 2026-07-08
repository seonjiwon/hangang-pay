package family.fisa.hangangpay.domain.transaction.internal.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.internal.RequestHashDigest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PaymentRequestHashGenerator - 결제 의도 해시")
class PaymentRequestHashGeneratorTest {

    private final PaymentRequestHashGenerator generator =
            new PaymentRequestHashGenerator(new RequestHashDigest(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));

    // 1. 결제는 저장된 거래(fromParty/toParty/amount)로 해시하므로 Transaction을 구성해 검증한다
    private Transaction payment(Long fromPartyId, Long toPartyId, String amount) {
        Party from = org.mockito.Mockito.mock(Party.class);
        Party to = org.mockito.Mockito.mock(Party.class);
        lenient().when(from.getId()).thenReturn(fromPartyId);
        lenient().when(to.getId()).thenReturn(toPartyId);

        Transaction tx = org.mockito.Mockito.mock(Transaction.class);
        when(tx.getFromParty()).thenReturn(from);
        when(tx.getToParty()).thenReturn(to);
        when(tx.getAmount()).thenReturn(new BigDecimal(amount));
        return tx;
    }

    @Test
    @DisplayName("같은 결제 거래는 같은 해시를 만든다")
    void 같은_거래_같은_해시() {
        String first = generator.generatePaymentExecuteHash(payment(1L, 2L, "10000"));
        String second = generator.generatePaymentExecuteHash(payment(1L, 2L, "10000"));

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("금액 scale 차이(10000 vs 10000.00)는 같은 해시로 정규화된다")
    void 금액_scale_무관() {
        String noScale = generator.generatePaymentExecuteHash(payment(1L, 2L, "10000"));
        String withScale = generator.generatePaymentExecuteHash(payment(1L, 2L, "10000.00"));

        assertThat(noScale).isEqualTo(withScale);
    }

    @Test
    @DisplayName("intent 생성 해시도 금액 scale 차이를 같은 해시로 정규화한다")
    void intent_금액_scale_무관() {
        String noScale = generator.generateIntentExecutionHash(1L, 2L, new BigDecimal("10000"));
        String withScale =
                generator.generateIntentExecutionHash(1L, 2L, new BigDecimal("10000.00"));

        assertThat(noScale).isEqualTo(withScale);
    }

    @Test
    @DisplayName("수신 가맹점(toParty)이 다르면 해시가 달라진다")
    void 다른_수신자_다른_해시() {
        String to2 = generator.generatePaymentExecuteHash(payment(1L, 2L, "10000"));
        String to3 = generator.generatePaymentExecuteHash(payment(1L, 3L, "10000"));

        assertThat(to2).isNotEqualTo(to3);
    }
}
