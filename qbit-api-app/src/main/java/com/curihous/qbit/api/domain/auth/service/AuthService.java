package com.curihous.qbit.api.domain.auth.service;

import com.curihous.qbit.api.domain.auth.dto.response.TokenResponseDto;
import com.curihous.qbit.api.config.security.CookieUtil;
import com.curihous.qbit.api.config.security.JwtUtil;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;

    @Transactional(readOnly = true)
    public TokenResponseDto refreshTokens(String accessToken, String refreshToken, HttpServletResponse response) {
        try {
            // 리프레시 토큰 유효성 검증
            jwtUtil.validateRefreshToken(refreshToken);
            
            // 리프레시 토큰에서 사용자 정보 추출
            var claims = jwtUtil.getRefreshTokenClaims(refreshToken);
            String email = claims.getSubject();
            String role = claims.get("role", String.class);

            // 새로운 인증 객체 생성
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    email,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority(role))
            );

            // 새로운 토큰들 발급
            String newAccessToken = jwtUtil.generateAccessToken(authentication);
            String newRefreshToken = jwtUtil.generateRefreshToken(authentication);

            // 새로운 리프레시 토큰을 쿠키에 저장
            cookieUtil.addCookie(response, "refreshToken", newRefreshToken, jwtUtil.getRefreshTokenMaxAge());

            return new TokenResponseDto(newAccessToken, jwtUtil.getAccessTokenMaxAge());
        } catch (QbitException e) {
            throw e;
        } catch (Exception e) {
            throw new QbitException(ErrorCode.UNAUTHORIZED);
        }
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        // 리프레시 토큰 쿠키 삭제
        cookieUtil.deleteCookie(request, response, "refreshToken");
    }
}

