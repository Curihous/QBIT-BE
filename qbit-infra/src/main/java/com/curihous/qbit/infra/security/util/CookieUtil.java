package com.curihous.qbit.infra.security.util;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class CookieUtil {

    private final Environment environment;

    public void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        try {
            // 프로덕션 환경에서 쿠키가 HTTPS를 통해서만 전송됨
            // 개발 환경에서는 로컬 테스트를 위해 HTTP 허용
            boolean isDevProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");
            
            ResponseCookie cookie = ResponseCookie.from(name, value)
                    .path("/")
                    .httpOnly(true)
                    .secure(!isDevProfile)
                    .maxAge(Duration.ofSeconds(maxAge))
                    .sameSite("Lax") // SameSite=Lax: CSRF 공격을 방지하면서도 일반적인 탐색에서는 쿠키를 전송
                    .build();
            
            response.addHeader("Set-Cookie", cookie.toString());
        } catch (Exception e) {
            throw new QbitException(ErrorCode.COOKIE_ADD_FAILED);
        }
    }

    public void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        try {
            // 프로덕션 환경에서 쿠키가 HTTPS를 통해서만 전송됨
            // 개발 환경에서는 로컬 테스트를 위해 HTTP 허용
            boolean isDevProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");
            
            // 원본 쿠키와 동일한 속성으로 삭제 쿠키 생성
            ResponseCookie deleteCookie = ResponseCookie.from(name, "")
                    .path("/")
                    .httpOnly(true)
                    .secure(!isDevProfile)
                    .maxAge(Duration.ofSeconds(0))
                    .sameSite("Lax") // 원본 쿠키와 동일한 SameSite 설정
                    .build();
            
            response.addHeader("Set-Cookie", deleteCookie.toString());
        } catch (Exception e) {
            throw new QbitException(ErrorCode.COOKIE_DELETE_FAILED);
        }
    }

    public String getCookieValue(HttpServletRequest request, String name) {
        try {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookie.getName().equals(name)) {
                        return cookie.getValue();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            throw new QbitException(ErrorCode.COOKIE_GET_FAILED);
        }
    }
}
