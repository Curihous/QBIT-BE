package com.curihous.qbit.alpaca.service;

import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthStateService {

    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${oauth.state.secret:default-secret-key-change-in-production}")
    private String stateSecret;
    
    @Value("${oauth.state.expiry-minutes:10}")
    private int stateExpiryMinutes;
    
    private static final String STATE_PREFIX = "oauth:state:";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom secureRandom = new SecureRandom();

    // OAuth 상태값 생성 
    public String generateSecureState(Long userId) {
        try {
            // 1. 암호학적으로 안전한 랜덤 nonce 생성 (32바이트)
            byte[] nonce = new byte[32];
            secureRandom.nextBytes(nonce);
            String nonceBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
            
            // 2. 현재 타임스탬프
            long timestamp = Instant.now().getEpochSecond();
            
            // 3. 페이로드 구성: userId:timestamp:nonce
            String payload = userId + ":" + timestamp + ":" + nonceBase64;
            
            // 4. HMAC 서명 생성
            String signature = generateHmacSignature(payload);
            
            // 5. 최종 상태값: payload.signature
            String state = payload + "." + signature;
            
            // 6. Redis에 상태값 저장 (중복 사용 방지 및 만료 관리)
            String redisKey = STATE_PREFIX + userId + ":" + timestamp;
            redisTemplate.opsForValue().set(redisKey, state, Duration.ofMinutes(stateExpiryMinutes));
            
            return state;
            
        } catch (Exception e) {
            log.error("OAuth 상태값 생성 실패: userId={}", userId, e);
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
    }

    // OAuth 상태값 검증 및 사용자 ID 추출
    public Long validateAndExtractUserId(String state) {
        try {
            if (state == null || state.isEmpty()) {
                throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
            }
            
            // 1. 상태값 파싱: payload.signature
            String[] parts = state.split("\\.", 2);
            if (parts.length != 2) {
                throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
            }
            
            String payload = parts[0];
            String signature = parts[1];
            
            // 2. HMAC 서명 검증
            String expectedSignature = generateHmacSignature(payload);
            if (!MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
            }
            
            // 3. 페이로드 파싱: userId:timestamp:nonce
            String[] payloadParts = payload.split(":", 3);
            if (payloadParts.length != 3) {
                throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
            }
            
            Long userId = Long.parseLong(payloadParts[0]);
            long timestamp = Long.parseLong(payloadParts[1]);
            String nonce = payloadParts[2];
            
            // 4. 타임스탬프 만료 검증
            long currentTime = Instant.now().getEpochSecond();
            if (currentTime - timestamp > (stateExpiryMinutes * 60)) {
                throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
            }
            
            // 5. Redis에서 상태값 사용 여부 확인 및 제거 (replay 공격 방지)
            String redisKey = STATE_PREFIX + userId + ":" + timestamp;
            String storedState = redisTemplate.opsForValue().getAndDelete(redisKey);
            
            if (storedState == null) {
                throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
            }
            
            if (!state.equals(storedState)) {
                throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
            }
            
            return userId;
            
        } catch (NumberFormatException e) {
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        } catch (Exception e) {
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
    }

    // HMAC 서명 생성
    private String generateHmacSignature(String payload) throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            stateSecret.getBytes(StandardCharsets.UTF_8), 
            HMAC_ALGORITHM
        );
        
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(secretKeySpec);
        
        byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
    }

    // 만료된 상태값 정리
    public void cleanupExpiredStates() {
        try {
            // Redis TTL을 사용하므로 자동으로 만료되지만, 
            // 혹시 모를 수동 정리를 위해 패턴으로 검색하여 삭제
            String pattern = STATE_PREFIX + "*";
            redisTemplate.delete(redisTemplate.keys(pattern));
            
        } catch (Exception e) {
            // 정리 실패는 무시 (Redis TTL이 자동으로 처리)
        }
    }
}
