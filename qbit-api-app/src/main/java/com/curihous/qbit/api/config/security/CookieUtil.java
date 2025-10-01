package com.curihous.qbit.api.config.security;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class CookieUtil {

    private final Environment environment;

    public void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        try {
            Cookie cookie = new Cookie(name, value);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            
            // 프로덕션 환경에서 쿠키가 HTTPS를 통해서만 전송됨
            // 개발 환경에서는 로컬 테스트를 위해 HTTP 허용
            boolean isDevProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");
            cookie.setSecure(!isDevProfile);
            
            cookie.setMaxAge(maxAge);
            response.addCookie(cookie);
        } catch (Exception e) {
            throw new QbitException(ErrorCode.COOKIE_ADD_FAILED);
        }
    }

    public void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        try {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookie.getName().equals(name)) {
                        cookie.setValue("");
                        cookie.setPath("/");
                        cookie.setMaxAge(0);
                        response.addCookie(cookie);
                    }
                }
            }
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
