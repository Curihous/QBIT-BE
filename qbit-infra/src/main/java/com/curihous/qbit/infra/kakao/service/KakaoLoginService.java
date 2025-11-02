package com.curihous.qbit.infra.kakao.service;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.domain.alpaca.repository.AlpacaOAuthConnectionRepository;
import com.curihous.qbit.domain.user.entity.LoginType;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.domain.user.repository.UserRepository;
import com.curihous.qbit.infra.kakao.dto.KakaoUserInfo;
import com.curihous.qbit.infra.security.jwt.JwtUtil;
import com.curihous.qbit.infra.security.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.redis.core.RedisTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoLoginService {
    
    private final KakaoAuthService kakaoAuthService;
    private final UserRepository userRepository;
    private final AlpacaOAuthConnectionRepository alpacaOAuthConnectionRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;
    
    private static final String LOGIN_SYNC_STREAM = "login-order-sync";
    
    @Transactional
    public Map<String, Object> loginWithKakao(String kakaoAccessToken, HttpServletResponse response) {
        log.info("카카오 네이티브 로그인 시작");
        
        // 1. 카카오 API로 사용자 정보 조회
        KakaoUserInfo kakaoUserInfo = kakaoAuthService.getUserInfo(kakaoAccessToken);
        
        // 2. 신규 여부 확인 (저장 전 존재 여부 체크)
        // TODO: perf 개선. race condition 문제 방지
        String email = kakaoUserInfo.getEmail();
        boolean isNewUser = userRepository.findByEmail(email).isEmpty();
        
        // 3. 사용자 저장 또는 조회
        User user = getOrSaveKakaoUser(kakaoUserInfo);
        
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
        
        // 6. DB에서 Alpaca 토큰 조회
        Optional<AlpacaOAuthConnection> alpacaConnection = alpacaOAuthConnectionRepository.findByUserId(user.getId());
        String alpacaAccessToken = alpacaConnection.map(AlpacaOAuthConnection::getAccessToken).orElse(null);
        
        final String finalAlpacaAccessToken = alpacaAccessToken; 
        
        // 7. 트랜잭션 커밋 후 Alpaca 주문 동기화 이벤트 발행
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                boolean hasAlpacaToken = finalAlpacaAccessToken != null && !finalAlpacaAccessToken.isEmpty();
                log.info("트랜잭션 커밋 완료, 주문 동기화 이벤트 발행: userId={}, hasAlpacaToken={}", 
                    user.getId(), hasAlpacaToken);
                
                Map<String, String> fields = new HashMap<>();
                fields.put("userId", String.valueOf(user.getId()));
                fields.put("userEmail", user.getEmail());
                fields.put("hasAlpacaToken", String.valueOf(hasAlpacaToken));
                
                try {
                    redisTemplate.opsForStream().add(LOGIN_SYNC_STREAM, fields);
                    log.info("LoginOrderSyncEvent 발행 완료: userId={}", user.getId());
                } catch (Exception e) {
                    log.error("LoginOrderSyncEvent 발행 실패: userId={}, error={}", user.getId(), e.getMessage(), e);
                }
            }
        });
        
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
    private User getOrSaveKakaoUser(KakaoUserInfo kakaoUserInfo) {
        String email = kakaoUserInfo.getEmail();
        String nickname = kakaoUserInfo.getNickname();
        
        log.info("카카오 사용자 정보 파싱 결과: email={}, nickname={}", email, nickname);
        
        User existingUser = userRepository.findByEmail(email).orElse(null);

        if (existingUser != null) {
            log.info("기존 사용자 발견: userId={}, email={}, loginType={}", 
                    existingUser.getId(), existingUser.getEmail(), existingUser.getLoginType());
            
            // 기존 사용자 - 로그인 타입 확인
            if (!existingUser.getLoginType().equals(LoginType.KAKAO)) {
                log.error("기존 사용자의 로그인 타입이 카카오가 아님: email={}, loginType={}", 
                        email, existingUser.getLoginType());
                throw new QbitException(ErrorCode.EMAIL_ALREADY_REGISTERED);
            }
            
            // 기존 사용자가 비활성화 상태라면 자동으로 활성화
            if (!existingUser.isActive()) {
                existingUser.activate();
                log.info("비활성화된 사용자가 카카오 로그인을 통해 자동 활성화됨: email={}", email);
            }
            
            return existingUser;
        } else {
            log.info("새 사용자 생성 시작: email={}, nickname={}", email, nickname);
            
            // 새 사용자 생성
            User newUser = User.builder()
                    .email(email)
                    .nickname(nickname)
                    .provider("kakao")
                    .loginType(LoginType.KAKAO)
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
