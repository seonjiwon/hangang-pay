package family.fisa.hangangpaybank.global.crypto;

import family.fisa.hangangpaybank.global.code.error.GeneralErrorCode;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;

@Component
public class WalletKeyCipher {

    private static final String ENCRYPTED_VALUE_PREFIX = "v1:";
    private static final String AES_ALGORITHM = "AES";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SecretKeySpec secretKeySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public WalletKeyCipher(@Value("${wallet.key-cipher.secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new BusinessException(GeneralErrorCode.COMMON_INVALID_WALLET_KEY);
        }
        this.secretKeySpec = new SecretKeySpec(sha256(secret), AES_ALGORITHM);
    }

    public String encryptPrivateKey(String privateKey) {
        if (privateKey == null || privateKey.isBlank()) {
            throw new BusinessException(GeneralErrorCode.COMMON_INVALID_WALLET_KEY);
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKeySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] encrypted =
                    cipher.doFinal(
                            stripHexPrefix(privateKey.trim()).getBytes(StandardCharsets.UTF_8));
            byte[] payload =
                    ByteBuffer.allocate(iv.length + encrypted.length)
                            .put(iv)
                            .put(encrypted)
                            .array();

            return ENCRYPTED_VALUE_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new BusinessException(GeneralErrorCode.COMMON_INVALID_WALLET_KEY);
        }
    }

    public Credentials decryptCredentials(String encryptedPrivateKey) {
        if (encryptedPrivateKey == null || encryptedPrivateKey.isBlank()) {
            throw new BusinessException(GeneralErrorCode.COMMON_INVALID_WALLET_KEY);
        }
        try {
            return Credentials.create(decryptPrivateKey(encryptedPrivateKey));
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BusinessException(GeneralErrorCode.COMMON_INVALID_WALLET_KEY);
        }
    }

    private String decryptPrivateKey(String encryptedPrivateKey) {
        String value = encryptedPrivateKey.trim();
        if (!value.startsWith(ENCRYPTED_VALUE_PREFIX)) {
            return stripHexPrefix(value);
        }
        try {
            byte[] payload =
                    Base64.getDecoder().decode(value.substring(ENCRYPTED_VALUE_PREFIX.length()));
            if (payload.length <= IV_LENGTH_BYTES) {
                throw new BusinessException(GeneralErrorCode.COMMON_INVALID_WALLET_KEY);
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH_BYTES, payload.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKeySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new BusinessException(GeneralErrorCode.COMMON_INVALID_WALLET_KEY);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(GeneralErrorCode.COMMON_INVALID_WALLET_KEY);
        }
    }

    private static String stripHexPrefix(String value) {
        if (value.startsWith("0x") || value.startsWith("0X")) {
            return value.substring(2);
        }
        return value;
    }
}
