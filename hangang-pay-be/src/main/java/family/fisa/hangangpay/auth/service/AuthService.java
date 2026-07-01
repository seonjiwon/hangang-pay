package family.fisa.hangangpay.auth.service;

import static family.fisa.hangangpay.global.session.SessionAttributeNames.MERCHANT_ID;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.PARTY_ID;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.ROLE;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.USER_ID;

import family.fisa.hangangpay.auth.code.AuthErrorCode;
import family.fisa.hangangpay.auth.dto.request.LoginRequest;
import family.fisa.hangangpay.auth.dto.request.MerchantLoginRequest;
import family.fisa.hangangpay.auth.dto.response.LoginResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse loginUser(LoginRequest request, HttpSession session) {
        validateLoginRequest(request.phoneNumber(), request.password());

        User user =
                userRepository
                        .findByPhoneNumberWithParty(request.phoneNumber())
                        .orElseThrow(
                                () -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        saveLoginSession(session, USER_ID, user.getId(), user.getParty().getId(), PartyType.USER);

        log.info("사용자 로그인 성공. userId={}, partyId={}", user.getId(), user.getParty().getId());

        return new LoginResponse(user.getId(), user.getParty().getId(), PartyType.USER);
    }

    public LoginResponse loginMerchant(MerchantLoginRequest request, HttpSession session) {
        validateLoginRequest(request.businessNumber(), request.password());

        Merchant merchant =
                merchantRepository
                        .findByBusinessNumberWithParty(request.businessNumber())
                        .orElseThrow(
                                () -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), merchant.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        saveLoginSession(
                session,
                MERCHANT_ID,
                merchant.getId(),
                merchant.getParty().getId(),
                PartyType.MERCHANT);

        log.info(
                "가맹점 로그인 성공. merchantId={}, partyId={}",
                merchant.getId(),
                merchant.getParty().getId());

        return new LoginResponse(merchant.getId(), merchant.getParty().getId(), PartyType.MERCHANT);
    }

    public void logout(HttpSession session) {
        if (session == null) {
            return;
        }
        session.invalidate();
        log.info("로그아웃 성공");
    }

    private void validateLoginRequest(String phoneNumber, String password) {
        if (!StringUtils.hasText(phoneNumber) || !StringUtils.hasText(password)) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
    }

    private void saveLoginSession(
            HttpSession session,
            String principalIdKey,
            Long principalId,
            Long partyId,
            PartyType role) {
        clearAuthAttributes(session);
        session.setAttribute(principalIdKey, principalId);
        session.setAttribute(PARTY_ID, partyId);
        session.setAttribute(ROLE, role.name());
    }

    private void clearAuthAttributes(HttpSession session) {
        session.removeAttribute(USER_ID);
        session.removeAttribute(MERCHANT_ID);
        session.removeAttribute(PARTY_ID);
        session.removeAttribute(ROLE);
    }
}
