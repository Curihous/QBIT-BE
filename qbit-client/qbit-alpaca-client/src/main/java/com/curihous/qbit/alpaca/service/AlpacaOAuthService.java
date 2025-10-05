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
import java.util.Map;
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
    private final OAuthStateService oAuthStateService;

    // OAuth 승인 URL 생성
    public String generateAuthUrl(Long userId) {
        String secureState = oAuthStateService.generateSecureState(userId);
        return UriComponentsBuilder.fromUriString(authorizationUrl)
            .queryParam("response_type", "code")
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("scope", "trading")
            .queryParam("state", secureState)
            .queryParam("env", "paper") // Paper Trading
            .build()
            .toUriString();
    }

    // OAuth 상태값 검증 및 사용자 ID 추출
    public Long validateStateAndExtractUserId(String state) {
        return oAuthStateService.validateAndExtractUserId(state);
    }

    // 인증 코드를 액세스 토큰으로 교환하고 DB에 저장
    @Transactional
    public AlpacaOAuthConnection exchangeTokenAndSave(Long userId, String code) {
        try {
            // Alpaca OAuth 토큰 엔드포인트 호출
            AlpacaTokenResponse tokenResponse = alpacaOAuthClient.exchangeToken(
                code, clientId, redirectUri
            );

            // 계정 정보 조회로 실제 사용자 ID 획득
            String alpacaUserId = getAlpacaUserIdFromAccount(tokenResponse.getAccessToken());
            
            // 사용자 조회
            User user = userService.findById(userId);

            // 토큰 만료 시간 계산
            long expiresInSeconds = tokenResponse.getExpiresIn() != null ?
                tokenResponse.getExpiresIn() : 3600L; // 기본값 1시간
            LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC)
                .plusSeconds(expiresInSeconds);

            // OAuth 연결 정보 저장
            AlpacaOAuthConnection connection = alpacaOAuthConnectionService.createConnection(
                user,
                alpacaUserId,
                tokenResponse.getAccessToken(),
                tokenResponse.getRefreshToken(),
                tokenResponse.getTokenType(),
                expiresAt
            );

            return connection;

        } catch (Exception e) {
            log.error("Alpaca OAuth 토큰 교환 실패: userId={}, code={}, error={}", userId, code, e.getMessage(), e);
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
    }

    /**
     * Alpaca 계정 정보를 조회해서 실제 사용자 ID를 가져옴
     */
    private String getAlpacaUserIdFromAccount(String accessToken) {
        try {
            String bearerToken = "Bearer " + accessToken;
            Map<String, Object> accountInfo = alpacaOAuthClient.getAccount(bearerToken);
            
            // Alpaca 계정 ID 추출
            String accountId = (String) accountInfo.get("id");
            if (accountId != null && !accountId.isEmpty()) {
                return accountId;
            } else {
                return "account_" + System.currentTimeMillis();
            }
            
        } catch (Exception e) {
            log.error("계정 정보 조회 실패: {}", e.getMessage(), e);
            // 계정 조회 실패 시 임시 ID 생성
            // TODO: 지금 무조건 이 상태라 이 부분 확인 필요
            return "temp_account_" + System.currentTimeMillis();
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
            AlpacaTokenResponse tokenResponse = alpacaOAuthClient.refreshTokenWithCredentials(
                connection.getRefreshToken(), clientId
            );

            // 토큰 만료 시간 계산
            long expiresInSeconds = tokenResponse.getExpiresIn() != null ? 
                tokenResponse.getExpiresIn() : 3600L; // 기본값 1시간
            LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC)
                .plusSeconds(expiresInSeconds);

            // 토큰 업데이트
            String newRefreshToken = tokenResponse.getRefreshToken();
            if (newRefreshToken == null || newRefreshToken.isEmpty()) {
                // 리프레시 토큰이 없는 경우 기존 토큰 보존
                newRefreshToken = connection.getRefreshToken();
            }
            
            return alpacaOAuthConnectionService.updateTokens(
                connection,
                tokenResponse.getAccessToken(),
                newRefreshToken,
                expiresAt
            );

        } catch (Exception e) {
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
