package com.curihous.qbit.alpaca.service;

import com.curihous.qbit.alpaca.client.AlpacaOAuthClient;
import com.curihous.qbit.alpaca.dto.response.AlpacaTokenResponse;
import com.curihous.qbit.alpaca.dto.internal.AlpacaConnectionStatus;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlpacaOAuthService {

    @Value("${alpaca.oauth.client-id}")
    private String clientId;

    @Value("${alpaca.oauth.redirect-uri}")
    private String redirectUri;

    @Value("${alpaca.oauth.authorization-url}")
    private String authorizationUrl;

    private final AlpacaOAuthClient alpacaOAuthClient;
    private final AlpacaOAuthConnectionService alpacaOAuthConnectionService;
    private final UserService userService;

    // OAuth 승인 URL 생성 
    public String generateAuthUrl(String state) {
        return UriComponentsBuilder.fromHttpUrl(authorizationUrl)
            .queryParam("response_type", "code")
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("scope", "trading")
            .queryParam("state", state)
            .queryParam("env", "paper") // Paper Trading
            .build()
            .toUriString();
    }

    // 인증 코드를 액세스 토큰으로 교환하고 DB에 저장
    @Transactional
    public AlpacaOAuthConnection exchangeTokenAndSave(Long userId, String code) {
        try {
            // Alpaca OAuth 토큰 엔드포인트 호출
            AlpacaTokenResponse tokenResponse = alpacaOAuthClient.exchangeToken(
                code, clientId, redirectUri
            );

            // 사용자 조회
            User user = userService.findById(userId);

            // 토큰 만료 시간 계산
            LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC)
                .plusSeconds(tokenResponse.getExpiresIn());

            // OAuth 연결 정보 저장
            return alpacaOAuthConnectionService.createConnection(
                user,
                tokenResponse.getAlpacaUserId(),
                tokenResponse.getAccessToken(),
                tokenResponse.getRefreshToken(),
                tokenResponse.getTokenType(),
                expiresAt
            );

        } catch (Exception e) {
            log.error("Alpaca OAuth 토큰 교환 실패: userId={}, code={}", userId, code, e);
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
    }

    // 액세스 토큰 갱신
    @Transactional
    public AlpacaOAuthConnection refreshToken(Long userId) {
        try {
            Optional<AlpacaOAuthConnection> connectionOpt = alpacaOAuthConnectionService.findByUserId(userId);
            AlpacaOAuthConnection connection = connectionOpt
                .orElseThrow(() -> new QbitException(ErrorCode.UNAUTHORIZED));

            if (connection.getRefreshToken() == null) {
                throw new QbitException(ErrorCode.UNAUTHORIZED);
            }

            // 리프레시 토큰으로 새 액세스 토큰 요청
            AlpacaTokenResponse tokenResponse = alpacaOAuthClient.refreshToken(
                connection.getRefreshToken(), clientId
            );

            // 토큰 만료 시간 계산
            LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC)
                .plusSeconds(tokenResponse.getExpiresIn());

            // 토큰 업데이트
            return alpacaOAuthConnectionService.updateTokens(
                connection,
                tokenResponse.getAccessToken(),
                tokenResponse.getRefreshToken(),
                expiresAt
            );

        } catch (Exception e) {
            log.error("Alpaca OAuth 토큰 갱신 실패: userId={}", userId, e);
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
    }

    // OAuth 연결 해제
    @Transactional
    public void disconnect(Long userId) {
        Optional<AlpacaOAuthConnection> connectionOpt = alpacaOAuthConnectionService.findByUserId(userId);
        if (connectionOpt.isPresent()) {
            alpacaOAuthConnectionService.disconnect(connectionOpt.get());
        }
    }

    // 연결 상태 확인
    public AlpacaConnectionStatus getConnectionStatus(Long userId) {
        Optional<AlpacaOAuthConnection> connectionOpt = alpacaOAuthConnectionService.findByUserId(userId);
        
        if (connectionOpt.isEmpty()) {
            return new AlpacaConnectionStatus(
                false, false, "NOT_CONNECTED", false
            );
        }
        
        AlpacaOAuthConnection connection = connectionOpt.get();
        return new AlpacaConnectionStatus(
            true,
            connection.isPaperTrading(),
            connection.getConnectionStatus().name(),
            connection.isTokenExpired()
        );
    }

}
