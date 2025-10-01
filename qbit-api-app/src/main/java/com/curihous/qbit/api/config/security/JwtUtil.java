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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class JwtUtil {
    private final Key accessKey;
    private final Key refreshKey;
    
    private static final Duration ACCESS_TOKEN_EXPIRE_TIME = Duration.ofMinutes(5);
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
        return generateToken(authentication, accessKey, ACCESS_TOKEN_EXPIRE_TIME);
    }

    public String generateRefreshToken(Authentication authentication) {
        return generateToken(authentication, refreshKey, REFRESH_TOKEN_EXPIRE_TIME);
    }

    private String generateToken(Authentication authentication, Key key, Duration expiredTime) {
        Claims claims = Jwts.claims();
        claims.setSubject(authentication.getName());

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_USER");
        claims.put("role", role);

        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expiredTime.toMillis());

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public void validateAccessToken(String accessToken) {
        try {
            Jwts.parserBuilder().setSigningKey(accessKey).build().parseClaimsJws(accessToken);
        } catch (SecurityException | MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
            throw new QbitException(ErrorCode.INVALID_TOKEN);
        } catch (ExpiredJwtException e) {
            throw new QbitException(ErrorCode.EXPIRED_ACCESS_TOKEN);
        }
    }

    public void validateRefreshToken(String refreshToken) {
        try {
            Jwts.parserBuilder().setSigningKey(refreshKey).build().parseClaimsJws(refreshToken);
        } catch (SecurityException | MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
            throw new QbitException(ErrorCode.INVALID_REFRESH_TOKEN);
        } catch (ExpiredJwtException e) {
            throw new QbitException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token, accessKey);
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority(claims.get("role").toString())
        );
        User principal = new User(claims.getSubject(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
    }

    private Claims parseClaims(String token, Key key) {
        try {
            return Jwts.parserBuilder()
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
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(accessKey)
                    .build()
                    .parseClaimsJws(accessToken).getBody();
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException | IllegalArgumentException e) {
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
