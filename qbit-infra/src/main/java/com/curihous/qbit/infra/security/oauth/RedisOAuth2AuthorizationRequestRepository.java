package com.curihous.qbit.infra.security.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "oauth2_auth_request_state";
    private static final String REDIS_KEY_PREFIX = "oauth2:auth_request:";
    private static final Duration TTL = Duration.ofMinutes(3);

    private final StringRedisTemplate redis;

    // Redis에서 state로 인가 요청 정보 조회
    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = resolveState(request);
        if (!StringUtils.hasText(state)) return null;
        String data = redis.opsForValue().get(REDIS_KEY_PREFIX + state);
        return deserialize(data);
    }

    // Redis에 인가 요청 저장하고 쿠키에 state 보관
    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest req,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (req == null) { deleteByCookieAndRedis(request, response); return; }
        String state = req.getState();
        if (!StringUtils.hasText(state)) return;

        String key = REDIS_KEY_PREFIX + state;
        redis.opsForValue().set(key, serialize(req), TTL);

        // state만 쿠키에 단기 보관 (SameSite=None; Secure)
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, state)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(TTL)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    // Redis에서 인가 요청 조회 후 삭제하고 쿠키 만료
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        String state = resolveState(request);
        OAuth2AuthorizationRequest req = null;
        
        if (StringUtils.hasText(state)) {
            String key = REDIS_KEY_PREFIX + state;
            String data = redis.opsForValue().get(key);
            if (data != null) {
                req = deserialize(data);
                redis.delete(key);
            }
        }
        
        expireCookie(response);
        return req;
    }

    // 쿠키와 Redis에서 인가 요청 삭제 - 요청이 null일 때 호출
    private void deleteByCookieAndRedis(HttpServletRequest request, HttpServletResponse response) {
        String state = resolveState(request);
        if (StringUtils.hasText(state)) redis.delete(REDIS_KEY_PREFIX + state);
        expireCookie(response);
    }

    // 쿠키 만료 처리 - Set-Cookie 헤더로 쿠키 삭제
    private void expireCookie(HttpServletResponse response) {
        ResponseCookie expired = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", expired.toString());
    }

    // URL 파라미터 또는 쿠키에서 state 값 추출
    private String resolveState(HttpServletRequest request) {
        String state = request.getParameter("state");
        if (StringUtils.hasText(state)) return state;

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (COOKIE_NAME.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    // OAuth2 인가 요청 직렬화 - Java 직렬화 후 Base64 인코딩
    private String serialize(OAuth2AuthorizationRequest req) {
        byte[] bytes = SerializationUtils.serialize(req);
        return Base64.getUrlEncoder().encodeToString(bytes != null ? bytes : new byte[0]);
    }

    // OAuth2 인가 요청 역직렬화
    private OAuth2AuthorizationRequest deserialize(String data) {
        if (!StringUtils.hasText(data)) return null;
        byte[] bytes = Base64.getUrlDecoder().decode(data.getBytes(StandardCharsets.UTF_8));
        return (OAuth2AuthorizationRequest) SerializationUtils.deserialize(bytes);
    }
}