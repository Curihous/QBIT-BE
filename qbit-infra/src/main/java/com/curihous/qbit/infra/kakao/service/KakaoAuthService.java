package com.curihous.qbit.infra.kakao.service;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.infra.kakao.dto.KakaoUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoAuthService {
    
    private static final String KAKAO_USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
    private final RestTemplate restTemplate = new RestTemplate();
    
    // 카카오 사용자 정보 조회
    public KakaoUserInfo getUserInfo(String kakaoAccessToken) {
        try {
            log.info("카카오 사용자 정보 요청 시작");
            
            // 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + kakaoAccessToken);
            headers.set("Content-type", "application/x-www-form-urlencoded;charset=utf-8");
            
            // HTTP 요청
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<KakaoUserInfo> response = restTemplate.exchange(
                KAKAO_USER_INFO_URI,
                HttpMethod.GET,
                entity,
                KakaoUserInfo.class
            );
            
            KakaoUserInfo userInfo = response.getBody();
            
            if (userInfo == null) {
                log.error("카카오 사용자 정보가 비어있습니다.");
                throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
            }
            
            log.info("카카오 사용자 정보 조회 성공: email={}, nickname={}", 
                    userInfo.getEmail(), userInfo.getNickname());
            
            return userInfo;
            
        } catch (HttpClientErrorException e) {
            log.error("카카오 API 호출 실패: status={}, body={}", 
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        } catch (Exception e) {
            log.error("카카오 사용자 정보 조회 중 예외 발생", e);
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
    }
}

