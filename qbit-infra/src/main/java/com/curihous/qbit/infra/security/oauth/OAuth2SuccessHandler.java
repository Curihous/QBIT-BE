package com.curihous.qbit.infra.security.oauth;

import com.curihous.qbit.infra.security.util.CookieUtil;
import com.curihous.qbit.infra.security.jwt.JwtUtil;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.infra.security.oauth.dto.OAuth2UserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;
    
    private static final String OAUTH_PATH = "/oauth";
    private static final String DEPLOYED_REDIRECT_URL = "https://qbit.o-r.kr"; // 배포
    private static final String MOBILE_REDIRECT_URL = "qbit://oauth"; // 플러터 앱
    private static final List<String> ALLOWED_REDIRECT_URLS = List.of(
            "http://localhost:3000",     // FE 로컬 
            "http://localhost:8080",     // BE 로컬
            "https://qbit.o-r.kr"        // 배포
    );

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            // OAuth2UserDetails에서 사용자 정보 추출
            OAuth2UserDetails userDetails = (OAuth2UserDetails) authentication.getPrincipal();
            boolean isNewUser = userDetails.isNewUser();
            Long userId = userDetails.userId();

            // JWT 토큰 발급 (userId 포함)
            String accessToken = jwtUtil.generateAccessTokenWithUserId(authentication, userId);
            String refreshToken = jwtUtil.generateRefreshTokenWithUserId(authentication, userId);
            
            log.info("AccessToken generated successfully: length={}, masked={}", 
                    accessToken.length(), maskToken(accessToken));

            // 모바일 앱 요청인지 확인 (User-Agent 또는 state 파라미터로 구분)
            String userAgent = request.getHeader("User-Agent");
            String state = request.getParameter("state");
            boolean isMobileApp = (userAgent != null && (userAgent.contains("Flutter") || userAgent.contains("Dart")))
                    || (state != null && state.contains("mobile"));

            String redirectUrl;
            if (isMobileApp) {
                // 플러터 앱: 커스텀 스킴으로 리다이렉트
                redirectUrl = MOBILE_REDIRECT_URL;
            } else {
                // 웹: 기존 로직
                String origin = request.getHeader("origin");
                String referer = request.getHeader("referer");
                String baseRedirectUrl = ALLOWED_REDIRECT_URLS.stream()
                        .filter(url -> url.equals(origin) || (referer != null && referer.startsWith(url)))
                        .findFirst()
                        .orElse(DEPLOYED_REDIRECT_URL);
                redirectUrl = baseRedirectUrl + OAUTH_PATH;
                
                // 웹의 경우에만 RefreshToken 쿠키 추가
                cookieUtil.addCookie(response, "refreshToken", refreshToken, jwtUtil.getRefreshTokenMaxAge());
            }

            // 리다이렉트 URL에 토큰 정보 포함
            String redirectUrlWithParams = UriComponentsBuilder.fromUriString(redirectUrl)
                    .queryParam("accessToken", accessToken)
                    .queryParam("userId", userId)
                    .queryParam("isNewUser", isNewUser)
                    .build()
                    .toUriString();
            
            response.sendRedirect(redirectUrlWithParams);
        } catch (Exception e) {
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
    }

    // 토큰을 마스킹하여 안전하게 로깅
    private String maskToken(String token) {
        if (token == null || token.length() <= 12) {
            return "***";
        }
        return token.substring(0, 8) + "..." + token.substring(token.length() - 4);
    }
}
