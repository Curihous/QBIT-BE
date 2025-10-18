package com.curihous.qbit.infra.google.service;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.infra.google.dto.GoogleUserInfo;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthService {
    
    @Value("${oauth2.google.client-id:}")
    private String googleClientId;
    
    // 구글 ID 토큰 검증 및 사용자 정보 조회
    public GoogleUserInfo getUserInfo(String googleIdToken) {
        try {
            log.info("구글 사용자 정보 요청 시작");
            
            // ID 토큰 검증
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
            
            GoogleIdToken idToken = verifier.verify(googleIdToken);
            
            if (idToken == null) {
                log.error("구글 ID 토큰 검증 실패");
                throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
            }
            
            GoogleIdToken.Payload payload = idToken.getPayload();
            
            // 사용자 정보 추출
            String userId = payload.getSubject();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");
            
            log.info("구글 API 응답 원본: googleId={}, email={}, name={}", 
                    userId, email, name);
            
            // 필수 정보 검증
            if (email == null || email.isEmpty()) {
                log.error("구글 사용자 정보에서 이메일이 없습니다.");
                throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
            }
            
            if (name == null || name.isEmpty()) {
                log.error("구글 사용자 정보에서 이름이 없습니다.");
                throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
            }
            
            log.info("구글 사용자 정보 조회 성공: email={}, name={}", email, name);
            
            return new GoogleUserInfo(userId, email, name, pictureUrl);
            
        } catch (Exception e) {
            log.error("구글 사용자 정보 조회 중 예외 발생", e);
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
    }
}

