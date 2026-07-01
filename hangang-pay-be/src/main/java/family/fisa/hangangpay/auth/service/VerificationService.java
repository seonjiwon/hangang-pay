package family.fisa.hangangpay.auth.service;

import family.fisa.hangangpay.auth.code.AuthErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** SMS 및 계좌 1원 인증 처리 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final String SESSION_SMS_CODE = "sms_code";
    private static final String SESSION_SMS_PHONE = "sms_phone";
    private static final String SESSION_SMS_EXPIRES_AT = "sms_expires_at";
    private static final String SESSION_ACCOUNT_CODE = "account_code";
    private static final String SESSION_ACCOUNT_NUMBER = "account_number";
    private static final String SESSION_ACCOUNT_INSTITUTION_ID = "account_institution_id";
    private static final String SESSION_ACCOUNT_EXPIRES_AT = "account_expires_at";

    private static final int SMS_EXPIRE_MINUTES = 5;
    private static final int ACCOUNT_EXPIRE_MINUTES = 10;

    private final SecureRandom random = new SecureRandom();

    /** SMS 인증 코드 발송 */
    public String sendSms(String phoneNumber, HttpSession session) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        session.setAttribute(SESSION_SMS_CODE, code);
        session.setAttribute(SESSION_SMS_PHONE, phoneNumber);
        session.setAttribute(
                SESSION_SMS_EXPIRES_AT, LocalDateTime.now().plusMinutes(SMS_EXPIRE_MINUTES));
        log.info("SMS 인증 코드 발송: phoneNumber={}, code={}", phoneNumber, code);
        return code;
    }

    /** SMS 인증 코드 검증 */
    public void verifySms(String phoneNumber, String code, HttpSession session) {
        String savedCode = (String) session.getAttribute(SESSION_SMS_CODE);
        String savedPhone = (String) session.getAttribute(SESSION_SMS_PHONE);
        LocalDateTime expiresAt = (LocalDateTime) session.getAttribute(SESSION_SMS_EXPIRES_AT);

        if (savedCode == null || savedPhone == null || expiresAt == null) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_NOT_FOUND);
        }
        if (LocalDateTime.now().isAfter(expiresAt)) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_EXPIRED);
        }
        if (!savedPhone.equals(phoneNumber) || !savedCode.equals(code)) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        session.removeAttribute(SESSION_SMS_CODE);
        session.removeAttribute(SESSION_SMS_PHONE);
        session.removeAttribute(SESSION_SMS_EXPIRES_AT);

        // 회원가입 상태 저장
        session.setAttribute(SessionAttributeNames.SIGNUP_PHONE_VERIFIED, true);
        session.setAttribute(SessionAttributeNames.SIGNUP_PHONE_NUMBER, savedPhone);
        session.setAttribute(SessionAttributeNames.SIGNUP_PHONE_VERIFIED_AT, LocalDateTime.now());

        log.info("SMS 인증 완료: phoneNumber={}", phoneNumber);
    }

    /** 계좌 1원 인증 코드 발송 */
    public String sendAccountVerification(
            Long institutionId, String accountNumber, HttpSession session) {

        String code = String.format("%06d", random.nextInt(1_000_000));
        session.setAttribute(SESSION_ACCOUNT_CODE, code);
        session.setAttribute(SESSION_ACCOUNT_INSTITUTION_ID, institutionId);
        session.setAttribute(SESSION_ACCOUNT_NUMBER, accountNumber);
        session.setAttribute(
                SESSION_ACCOUNT_EXPIRES_AT,
                LocalDateTime.now().plusMinutes(ACCOUNT_EXPIRE_MINUTES));
        log.info("계좌 1원 인증 코드 발송: code={}, accountNumber={}", code, accountNumber);
        return code;
    }

    /** 계좌 1원 인증 코드 검증 */
    public void verifyAccount(
            Long institutionId, String accountNumber, String code, HttpSession session) {
        String savedCode = (String) session.getAttribute(SESSION_ACCOUNT_CODE);
        Long savedInstitutionId = (Long) session.getAttribute(SESSION_ACCOUNT_INSTITUTION_ID);
        String savedAccount = (String) session.getAttribute(SESSION_ACCOUNT_NUMBER);
        LocalDateTime expiresAt = (LocalDateTime) session.getAttribute(SESSION_ACCOUNT_EXPIRES_AT);

        if (savedCode == null
                || savedInstitutionId == null
                || savedAccount == null
                || expiresAt == null) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_NOT_FOUND);
        }
        if (LocalDateTime.now().isAfter(expiresAt)) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_EXPIRED);
        }
        if (!savedInstitutionId.equals(institutionId)
                || !savedAccount.equals(accountNumber)
                || !savedCode.equals(code)) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        session.removeAttribute(SESSION_ACCOUNT_CODE);
        session.removeAttribute(SESSION_ACCOUNT_INSTITUTION_ID);
        session.removeAttribute(SESSION_ACCOUNT_NUMBER);
        session.removeAttribute(SESSION_ACCOUNT_EXPIRES_AT);

        // 회원가입 상태 저장
        session.setAttribute(SessionAttributeNames.SIGNUP_ACCOUNT_VERIFIED, true);
        session.setAttribute(SessionAttributeNames.SIGNUP_INSTITUTION_ID, savedInstitutionId);
        session.setAttribute(SessionAttributeNames.SIGNUP_ACCOUNT_NUMBER, savedAccount);
        session.setAttribute(SessionAttributeNames.SIGNUP_ACCOUNT_VERIFIED_AT, LocalDateTime.now());

        log.info("계좌 1원 인증 완료");
    }
}
