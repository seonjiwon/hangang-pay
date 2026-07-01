package family.fisa.hangangpay.domain.merchant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.dto.MerchantQrPayload;
import family.fisa.hangangpay.domain.merchant.dto.response.MerchantQrResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가맹점 결제 QR 생성 / 조회 서비스 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MerchantQrService {

    // QR 사이즈
    private static final int QR_SIZE = 512;

    // QR 외곽 여백
    private static final int QR_MARGIN = 1;

    // data URL prefix. 프론트에서 <img src="..."> 로 사용 가능
    private static final String DATA_URL_PREFIX = "data:image/png;base64,";

    private final MerchantRepository merchantRepository;
    private final ObjectMapper objectMapper;

    /** 가맹점 PartyId로 QR 생성 */
    public MerchantQrResponse getQrForPartyId(Long partyId) {
        log.info("가맹점 QR 조회 시작. partyId={}", partyId);

        // 1. 가맹점 정보 조회
        Merchant merchant = findMerchantByPartyId(partyId);

        // 2. 페이로드 직렬화
        String payloadJson = serializePayload(merchant.getId());

        // 3. QR PNG 생성 + base64 인코딩
        String qrImageBase64 = generateQrPngBase64(payloadJson);

        log.info("가맹점 QR 조회 완료. partyId={}, merchantId={}", partyId, merchant.getId());

        return MerchantQrResponse.of(qrImageBase64);
    }

    /** partyId로 연결된 Merchant 엔티티 조회 */
    private Merchant findMerchantByPartyId(Long partyId) {
        return merchantRepository
                .findByParty_Id(partyId)
                .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
    }

    /** QR에 인코딩할 페이로드를 JSON 문자열로 직렬화 */
    private String serializePayload(Long merchantId) {
        try {
            return objectMapper.writeValueAsString(MerchantQrPayload.of(merchantId));
        } catch (JsonProcessingException e) {
            log.error("QR 페이로드 직렬화 실패. merchantId={}", merchantId, e);
            throw new BusinessException(MerchantErrorCode.QR_IMAGE_GENERATION_FAILED);
        }
    }

    /** 문자열을 QR PNG로 변환하고 base64 data URL 형태로 반환한다. */
    private String generateQrPngBase64(String content) {
        try {
            BitMatrix matrix = encodeQr(content);
            byte[] pngBytes = toPngBytes(matrix);
            return DATA_URL_PREFIX + Base64.getEncoder().encodeToString(pngBytes);
        } catch (WriterException | IOException e) {
            log.error("QR 이미지 생성 실패. contentLength={}", content.length(), e);
            throw new BusinessException(MerchantErrorCode.QR_IMAGE_GENERATION_FAILED);
        }
    }

    /** zxing QRCodeWriter 문자열을 BitMatrix로 인코딩 한다. */
    private BitMatrix encodeQr(String content) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, QR_MARGIN);

        return new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);
    }

    /** BitMatrix -> PNG 바이트배열 변환 */
    private byte[] toPngBytes(BitMatrix matrix) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        }
    }
}
