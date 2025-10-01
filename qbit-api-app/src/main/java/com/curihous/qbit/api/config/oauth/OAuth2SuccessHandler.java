package com.curihous.qbit.api.config.oauth;

import com.curihous.qbit.api.config.security.CookieUtil;
import com.curihous.qbit.api.config.security.JwtUtil;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.auth.dto.OAuth2UserDetails;
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
    
    private static final String OAUTH_PATH = "/api/oauth";
    private static final String DEPLOYED_REDIRECT_URL = "http://localhost:3000";
    private static final List<String> ALLOWED_REDIRECT_URLS = List.of(
            "http://localhost:3000",
            "http://localhost:8080"
    );

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            String origin = request.getHeader("origin");
            String referer = request.getHeader("referer");

            String baseRedirectUrl = ALLOWED_REDIRECT_URLS.stream()
                    .filter(url -> url.equals(origin) || (referer != null && referer.startsWith(url)))
                    .findFirst()
                    .orElse(DEPLOYED_REDIRECT_URL);
            String redirectUrl = baseRedirectUrl + OAUTH_PATH;

            // JWT 토큰 발급
            String accessToken = jwtUtil.generateAccessToken(authentication);
            int expiresIn = jwtUtil.getAccessTokenMaxAge();
            String refreshToken = jwtUtil.generateRefreshToken(authentication);
            cookieUtil.addCookie(response, "refreshToken", refreshToken, jwtUtil.getRefreshTokenMaxAge());

            // OAuth2UserDetails에서 사용자 정보 추출
            OAuth2UserDetails userDetails = (OAuth2UserDetails) authentication.getPrincipal();
            boolean isNewUser = userDetails.isNewUser();
            Long userId = userDetails.userId();

            log.info("AccessToken: {}", accessToken);

            // 리다이렉트 URL에 토큰 정보 포함
            String redirectUrlWithParams = UriComponentsBuilder.fromUriString(redirectUrl)
                    .queryParam("accessToken", accessToken)
                    .queryParam("expiresIn", expiresIn)
                    .queryParam("isNewUser", isNewUser)
                    .queryParam("userId", userId)
                    .build()
                    .toUriString();
            response.sendRedirect(redirectUrlWithParams);
        } catch (Exception e) {
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
    }
}
