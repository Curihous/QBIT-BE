package com.curihous.qbit.infra.kakao.service;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.user.entity.LoginType;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.domain.user.repository.UserRepository;
import com.curihous.qbit.infra.kakao.dto.KakaoUserInfo;
import com.curihous.qbit.infra.security.jwt.JwtUtil;
import com.curihous.qbit.infra.security.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class KakaoLoginService {
    
    private final KakaoAuthService kakaoAuthService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;
    
    @Transactional
    public Map<String, Object> loginWithKakao(String kakaoAccessToken, HttpServletResponse response) {
        log.info("카카오 네이티브 로그인 시작");
        
        // 1. 카카오 API로 사용자 정보 조회
        KakaoUserInfo kakaoUserInfo = kakaoAuthService.getUserInfo(kakaoAccessToken);
        
        // 2. 사용자 저장 또는 조회
        User user = getOrSaveKakaoUser(kakaoUserInfo);
        
        // 3. 신규 여부 확인 (생성 시간과 수정 시간이 같으면 신규)
        boolean isNewUser = user.getCreatedAt().equals(user.getUpdatedAt());
        
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
        
        // 6. 응답 데이터 구성
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("expiresIn", expiresIn);
        result.put("isNewUser", isNewUser);
        result.put("userId", user.getId());
        result.put("email", user.getEmail());
        result.put("nickname", user.getNickname());
        
        return result;
    }
    
    private User getOrSaveKakaoUser(KakaoUserInfo kakaoUserInfo) {
        String email = kakaoUserInfo.getEmail();
        User existingUser = userRepository.findByEmail(email).orElse(null);

        if (existingUser != null) {
            // 기존 사용자 - 로그인 타입 확인
            if (!existingUser.getLoginType().equals(LoginType.KAKAO)) {
                throw new QbitException(ErrorCode.EMAIL_ALREADY_REGISTERED);
            }
            
            // 기존 사용자가 비활성화 상태라면 자동으로 활성화
            if (!existingUser.isActive()) {
                existingUser.activate();
                log.info("비활성화된 사용자가 카카오 로그인을 통해 자동 활성화됨: email={}", email);
            }
            
            return existingUser;
        } else {
            // 새 사용자 생성
            User newUser = User.builder()
                    .email(email)
                    .nickname(kakaoUserInfo.getNickname())
                    .provider("kakao")
                    .loginType(LoginType.KAKAO)
                    .build();
            return userRepository.save(newUser);
        }
    }
}
