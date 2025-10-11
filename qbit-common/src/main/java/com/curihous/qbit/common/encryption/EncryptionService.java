package com.curihous.qbit.common.encryption;

import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 16; // 128 bits

    @Value("${encryption.secret-key}")
    private String secretKeyString;

    private SecretKey getSecretKey() {
        // Base64로 인코딩된 키를 디코딩하여 SecretKey 생성
        byte[] keyBytes = Base64.getDecoder().decode(secretKeyString);
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    // 토큰 암호화
    public String encrypt(String plainText) {
        try {
            if (plainText == null || plainText.isEmpty()) {
                return plainText;
            }

            SecretKey secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            // 랜덤 IV 생성
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // IV + 암호화된 텍스트를 결합하여 Base64로 인코딩
            byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherText, 0, encryptedWithIv, GCM_IV_LENGTH, cipherText.length);

            return Base64.getEncoder().encodeToString(encryptedWithIv);

        } catch (Exception e) {
            log.error("암호화 실패", e);
            throw new QbitException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // 토큰 복호화
    public String decrypt(String encryptedText) {
        try {
            if (encryptedText == null || encryptedText.isEmpty()) {
                return encryptedText;
            }

            SecretKey secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            // Base64 디코딩
            byte[] encryptedWithIv;
            try {
                encryptedWithIv = Base64.getDecoder().decode(encryptedText);
            } catch (IllegalArgumentException ex) {
                // URL-safe Base64로 재시도 (-, _ 문자 포함된 경우)
                String fixed = encryptedText.replace('-', '+').replace('_', '/');
                int pad = (4 - (fixed.length() % 4)) % 4;
                fixed = fixed + "=".repeat(pad);
                encryptedWithIv = Base64.getDecoder().decode(fixed);
            }

            // IV와 암호화된 텍스트 분리
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedWithIv, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedWithIv, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("복호화 실패", e);
            throw new QbitException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // AES-256 키 생성 유틸리티 (개발/테스트용)
    public static String generateSecretKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(256); // AES-256
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new QbitException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
