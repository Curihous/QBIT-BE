package com.curihous.qbit.api.config.security;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Duration;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {
    private final Key accessKey;
    private final Key refreshKey;
    
    private static final Duration ACCESS_TOKEN_EXPIRE_TIME = Duration.ofMinutes(50); // TODO: 개발 후 duration 조정
    private static final Duration REFRESH_TOKEN_EXPIRE_TIME = Duration.ofDays(7);

    public JwtUtil(@Value("${jwt.secret.access}") String accessSecret, 
                   @Value("${jwt.secret.refresh}") String refreshSecret) {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(accessSecret);
            this.accessKey = Keys.hmacShaKeyFor(keyBytes);
            keyBytes = Decoders.BASE64.decode(refreshSecret);
            this.refreshKey = Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            throw new QbitException(ErrorCode.JWT_GENERATION_FAILED);
        }
    }

    public String generateAccessToken(Authentication authentication) {
        return generateToken(authentication, accessKey, ACCESS_TOKEN_EXPIRE_TIME, null);
    }

    public String generateRefreshToken(Authentication authentication) {
        return generateToken(authentication, refreshKey, REFRESH_TOKEN_EXPIRE_TIME, null);
    }

    public String generateAccessTokenWithUserId(Authentication authentication, Long userId) {
        return generateToken(authentication, accessKey, ACCESS_TOKEN_EXPIRE_TIME, userId);
    }

    public String generateRefreshTokenWithUserId(Authentication authentication, Long userId) {
        return generateToken(authentication, refreshKey, REFRESH_TOKEN_EXPIRE_TIME, userId);
    }

    private String generateToken(Authentication authentication, Key key, Duration expiredTime, Long userId) {
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_USER");

        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expiredTime.toMillis());

        JwtBuilder builder = Jwts.builder()
                .setSubject(authentication.getName())
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expireDate);
        
        if (userId != null) {
            builder.claim("userId", userId);
        }

        return builder.signWith(key, SignatureAlgorithm.HS256).compact();
    }

    public void validateAccessToken(String accessToken) {
        try {
            Jwts.parser().setSigningKey(accessKey).build().parseClaimsJws(accessToken);
        } catch (SecurityException | MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
            throw new QbitException(ErrorCode.INVALID_TOKEN);
        } catch (ExpiredJwtException e) {
            throw new QbitException(ErrorCode.EXPIRED_ACCESS_TOKEN);
        }
    }

    public void validateRefreshToken(String refreshToken) {
        try {
            Jwts.parser().setSigningKey(refreshKey).build().parseClaimsJws(refreshToken);
        } catch (SecurityException | MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
            throw new QbitException(ErrorCode.INVALID_REFRESH_TOKEN);
        } catch (ExpiredJwtException e) {
            throw new QbitException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token, accessKey);
        
        // JWT에서 사용자 정보 추출
        String subject = claims.getSubject();
        String role = claims.get("role").toString();
        Long userId = getUserIdFromClaims(claims);
        
        // CustomUserDetails 생성
        CustomUserDetails userDetails = new CustomUserDetails(userId, subject, role);
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }
    
    private Long getUserIdFromClaims(Claims claims) {
        Object userIdObj = claims.get("userId");
        if (userIdObj != null) {
            return Long.valueOf(userIdObj.toString());
        }
        return null;
    }

    private Claims parseClaims(String token, Key key) {
        try {
            return Jwts.parser()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token).getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        } catch (JwtException e) {
            throw new QbitException(ErrorCode.JWT_VALIDATION_FAILED);
        }
    }

    public boolean isAccessTokenExpired(String accessToken) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(accessKey)
                    .build()
                    .parseClaimsJws(accessToken).getBody();
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT validation failed: {}", e.getMessage(), e);
            throw new QbitException(ErrorCode.JWT_VALIDATION_FAILED);
        }
    }

    public Claims getRefreshTokenClaims(String refreshToken) {
        validateRefreshToken(refreshToken);
        return parseClaims(refreshToken, refreshKey);
    }

    public int getAccessTokenMaxAge() {
        return (int) ACCESS_TOKEN_EXPIRE_TIME.toSeconds();
    }

    public int getRefreshTokenMaxAge() {
        return (int) REFRESH_TOKEN_EXPIRE_TIME.toSeconds();
    }
}
