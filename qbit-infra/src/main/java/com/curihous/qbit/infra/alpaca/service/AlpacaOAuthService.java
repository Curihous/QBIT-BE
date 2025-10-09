package com.curihous.qbit.infra.alpaca.service;

import com.curihous.qbit.infra.alpaca.client.AlpacaOAuthClient;
import com.curihous.qbit.infra.alpaca.client.AlpacaTradingClient;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaAccountResponse;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaTokenResponse;
import com.curihous.qbit.infra.alpaca.dto.internal.AlpacaConnectionStatusDto;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.domain.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.domain.alpaca.service.AlpacaOAuthConnectionService;
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

// TODO: 로그 제거
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
    private final AlpacaTradingClient alpacaTradingClient;
    private final AlpacaOAuthConnectionService alpacaOAuthConnectionService;
    private final UserService userService;
    private final AlpacaOAuthStateService alpacaOAuthStateService;

    // OAuth 승인 URL 생성
    public String generateAuthUrl(Long userId) {
        String secureState = alpacaOAuthStateService.generateSecureState(userId);
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
        return alpacaOAuthStateService.validateAndExtractUserId(state);
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
            log.info("Alpaca 토큰 교환 성공, access token 앞 10자: {}", 
                tokenResponse.accessToken() != null ? tokenResponse.accessToken().substring(0, Math.min(10, tokenResponse.accessToken().length())) : "null");
            String alpacaUserId = getAlpacaUserIdFromAccount(tokenResponse.accessToken());
            
            // 사용자 조회
            User user = userService.findById(userId);

            // 토큰 만료 시간 계산
            long expiresInSeconds = tokenResponse.expiresIn() != null ?
                tokenResponse.expiresIn() : 3600L; // 기본값 1시간
            LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC)
                .plusSeconds(expiresInSeconds);

            // OAuth 연결 정보 저장
            AlpacaOAuthConnection connection = alpacaOAuthConnectionService.createConnection(
                user,
                alpacaUserId,
                tokenResponse.accessToken(),
                tokenResponse.refreshToken(),
                tokenResponse.tokenType(),
                expiresAt
            );

            return connection;

        } catch (Exception e) {
            log.error("Alpaca OAuth 토큰 교환 실패: userId={}, code={}, error={}", userId, code, e.getMessage(), e);
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
    }

    // Alpaca 계정 정보를 조회해서 실제 사용자 ID를 가져옴
    private String getAlpacaUserIdFromAccount(String accessToken) {
        String bearerToken = "Bearer " + accessToken;
        log.info("Alpaca 계정 정보 조회 시도, Bearer token 길이: {}", bearerToken.length());
        
        try {
            // Paper Trading API로 계정 정보 조회
            AlpacaAccountResponse accountInfo = alpacaTradingClient.getAccount(bearerToken);
            String accountId = accountInfo.id();
            
            log.info("Alpaca 계정 ID 조회 성공: {}", accountId);
            return accountId;
            
        } catch (Exception e) {
            // Feign 예외 등 외부 API 호출 실패를 QbitException으로 변환
            log.error("Alpaca 계정 정보 조회 실패: {}", e.getMessage(), e);
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, 
                "Alpaca 계정 정보 조회에 실패했습니다. Paper Trading 계정이 활성화되어 있는지 확인해주세요.");
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
            long expiresInSeconds = tokenResponse.expiresIn() != null ? 
                tokenResponse.expiresIn() : 3600L; // 기본값 1시간
            LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC)
                .plusSeconds(expiresInSeconds);

            // 토큰 업데이트
            String newRefreshToken = tokenResponse.refreshToken();
            if (newRefreshToken == null || newRefreshToken.isEmpty()) {
                // 리프레시 토큰이 없는 경우 기존 토큰 보존
                newRefreshToken = connection.getRefreshToken();
            }
            
            return alpacaOAuthConnectionService.updateTokens(
                connection,
                tokenResponse.accessToken(),
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
    public AlpacaConnectionStatusDto getConnectionStatus(Long userId) {
        Optional<AlpacaOAuthConnection> connectionOpt = alpacaOAuthConnectionService.findByUserId(userId);
        
        if (connectionOpt.isEmpty()) {
            return new AlpacaConnectionStatusDto(
                false, false, "NOT_CONNECTED", false
            );
        }
        
        AlpacaOAuthConnection connection = connectionOpt.get();
        return new AlpacaConnectionStatusDto(
            true,
            connection.isPaperTrading(),
            connection.getAlpacaConnectionStatus().name(),
            connection.isTokenExpired()
        );
    }

}

