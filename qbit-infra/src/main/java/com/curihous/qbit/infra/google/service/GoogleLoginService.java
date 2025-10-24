package com.curihous.qbit.infra.google.service;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.user.entity.LoginType;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.domain.user.repository.UserRepository;
import com.curihous.qbit.infra.google.dto.GoogleUserInfo;
import com.curihous.qbit.infra.security.jwt.JwtUtil;
import com.curihous.qbit.infra.security.util.CookieUtil;
import com.curihous.qbit.common.event.LoginOrderSyncEvent;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleLoginService {
    
    private final GoogleAuthService googleAuthService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;
    private final ApplicationEventPublisher eventPublisher;
    
    @Transactional
    public Map<String, Object> loginWithGoogle(String googleIdToken, HttpServletResponse response) {
        log.info("구글 네이티브 로그인 시작");
        
        // 1. 구글 ID 토큰 검증 및 사용자 정보 조회
        GoogleUserInfo googleUserInfo = googleAuthService.getUserInfo(googleIdToken);
        
        // 2. 신규 여부 확인 (저장 전 존재 여부 체크)
        String email = googleUserInfo.email();
        boolean isNewUser = userRepository.findByEmail(email).isEmpty();
        
        // 3. 사용자 저장 또는 조회
        User user = getOrSaveGoogleUser(googleUserInfo);
        
        log.info("사용자 처리 완료: userId={}, email={}, isNewUser={}", 
                user.getId(), user.getEmail(), isNewUser);
        
        // 4. JWT 토큰 생성
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            user.getEmail(),
            null,
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        
        String accessToken = jwtUtil.generateAccessTokenWithUserId(authentication, user.getId());
        String refreshToken = jwtUtil.generateRefreshTokenWithUserId(authentication, user.getId());
        int expiresIn = jwtUtil.getAccessTokenMaxAge();
        
        // 5. Refresh Token을 쿠키에 저장
        cookieUtil.addCookie(response, "refreshToken", refreshToken, jwtUtil.getRefreshTokenMaxAge());
        
        log.info("JWT 토큰 발급 완료");
        
        // 6. Alpaca 주문 동기화 이벤트 발행 (비동기)
        eventPublisher.publishEvent(new LoginOrderSyncEvent(user.getId(), user.getEmail()));
        
        // 7. 응답 데이터 구성
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("expiresIn", expiresIn);
        result.put("isNewUser", isNewUser);
        result.put("userId", user.getId());
        result.put("email", user.getEmail());
        result.put("nickname", user.getNickname());
        
        return result;
    }

    // TODO: 사용자 개인정보를 담은 개발 로그 삭제
    private User getOrSaveGoogleUser(GoogleUserInfo googleUserInfo) {
        String email = googleUserInfo.email();
        String nickname = googleUserInfo.name();
        
        log.info("구글 사용자 정보 파싱 결과: email={}, nickname={}", email, nickname);
        
        User existingUser = userRepository.findByEmail(email).orElse(null);

        if (existingUser != null) {
            log.info("기존 사용자 발견: userId={}, email={}, loginType={}", 
                    existingUser.getId(), existingUser.getEmail(), existingUser.getLoginType());
            
            // 기존 사용자 - 로그인 타입 확인
            if (!existingUser.getLoginType().equals(LoginType.GOOGLE)) {
                log.error("기존 사용자의 로그인 타입이 구글이 아님: email={}, loginType={}", 
                        email, existingUser.getLoginType());
                throw new QbitException(ErrorCode.EMAIL_ALREADY_REGISTERED);
            }
            
            // 기존 사용자가 비활성화 상태라면 자동으로 활성화
            if (!existingUser.isActive()) {
                existingUser.activate();
                log.info("비활성화된 사용자가 구글 로그인을 통해 자동 활성화됨: email={}", email);
            }
            
            return existingUser;
        } else {
            log.info("새 사용자 생성 시작: email={}, nickname={}", email, nickname);
            
            // 새 사용자 생성
            User newUser = User.builder()
                    .email(email)
                    .nickname(nickname)
                    .provider("google")
                    .loginType(LoginType.GOOGLE)
                    .build();
            
            log.info("새 사용자 객체 생성 완료: email={}, nickname={}, provider={}, loginType={}", 
                    newUser.getEmail(), newUser.getNickname(), newUser.getProvider(), newUser.getLoginType());
            
            try {
                User savedUser = userRepository.save(newUser);
                log.info("새 사용자 저장 성공: userId={}, email={}", savedUser.getId(), savedUser.getEmail());
                return savedUser;
            } catch (Exception e) {
                log.error("새 사용자 저장 실패: email={}, nickname={}, error={}", 
                        email, nickname, e.getMessage(), e);
                throw e;
            }
        }
    }
}

