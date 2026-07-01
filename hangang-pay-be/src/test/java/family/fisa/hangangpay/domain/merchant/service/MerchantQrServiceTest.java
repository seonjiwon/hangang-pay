package family.fisa.hangangpay.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.dto.response.MerchantQrResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantQrServiceTest {

    @Mock private PartyRepository partyRepository;
    @Mock private MerchantRepository merchantRepository;

    private MerchantQrService merchantQrService;

    private static final Long PARTY_ID = 10L;
    private static final Long MERCHANT_ID = 1L;

    @BeforeEach
    void setUp() {
        merchantQrService = new MerchantQrService(merchantRepository, new ObjectMapper());
    }

    private Party party(Long partyId, PartyType type) {
        return Party.builder().id(partyId).partyType(type).build();
    }

    private Merchant merchant(Long merchantId, Party party) {
        return Merchant.builder()
                .id(merchantId)
                .party(party)
                .username("dropTop01")
                .passwordHash("hash")
                .businessNumber("123-45-67890")
                .merchantName("카페 드롭탑 강남점")
                .ownerName("홍길동")
                .address("서울시 강남구 테헤란로 427")
                .build();
    }

    @Nested
    @DisplayName("가맹점 QR 조회 (getQrForPartyId)")
    class GetQrForPartyId {

        @Test
        @DisplayName("정상: 가맹점 partyId 로 base64 PNG QR 응답을 생성")
        void success() {
            // given
            Party merchantParty = party(PARTY_ID, PartyType.MERCHANT);
            Merchant merchant = merchant(MERCHANT_ID, merchantParty);

            when(merchantRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(merchant));

            // when
            MerchantQrResponse result = merchantQrService.getQrForPartyId(PARTY_ID);

            // then
            assertThat(result.qrImageBase64()).startsWith("data:image/png;base64,");
            assertThat(result.qrImageBase64().length())
                    .isGreaterThan("data:image/png;base64,".length());
        }

        @Test
        @DisplayName("partyId 에 연결된 Merchant 없음 -> MERCHANT_NOT_FOUND")
        void throws_whenMerchantNotFound() {
            // given
            when(merchantRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.empty());

            // when, then
            assertThatThrownBy(() -> merchantQrService.getQrForPartyId(PARTY_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", MerchantErrorCode.MERCHANT_NOT_FOUND);
        }
    }
}
