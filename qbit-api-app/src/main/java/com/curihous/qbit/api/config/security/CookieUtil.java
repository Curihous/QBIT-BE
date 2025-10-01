package com.curihous.qbit.api.config.security;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CookieUtil {

    public void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        try {
            Cookie cookie = new Cookie(name, value);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setSecure(false); // TODO: HTTPS 환경에서는 true로 설정
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
