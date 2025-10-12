package com.curihous.qbit.infra.alpaca.service;

import com.curihous.qbit.infra.alpaca.client.AlpacaOAuthClient;
import com.curihous.qbit.infra.alpaca.client.AlpacaTradingClient;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaAccountResponse;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaTokenResponse;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.domain.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.domain.alpaca.service.AlpacaOAuthConnectionService;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
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
            AlpacaTokenResponse tokenResponse = alpacaOAuthClient.exchangeToken(
                code, clientId, redirectUri
            );

            String alpacaUserId = getAlpacaUserIdFromAccount(tokenResponse.accessToken());
            User user = userService.findById(userId);

            long expiresInSeconds = tokenResponse.expiresIn() != null ?
                    tokenResponse.expiresIn() : 604800L;
            // TODO: alpaca가 모의투자라서 제한을 두지 않는것같으므로.. 추후 보안을 위해 주기적 재인증 로직 추가 필요
            LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC)
                .plusSeconds(expiresInSeconds);

            AlpacaOAuthConnection connection = alpacaOAuthConnectionService.createConnection(
                user,
                alpacaUserId,
                tokenResponse.accessToken(),
                tokenResponse.tokenType(),
                expiresAt
            );

            return connection;

        } catch (Exception e) {
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
    }

    private String getAlpacaUserIdFromAccount(String accessToken) {
        String bearerToken = "Bearer " + accessToken;
        
        try {
            AlpacaAccountResponse accountInfo = alpacaTradingClient.getAccount(bearerToken);
            return accountInfo.id();
            
        } catch (Exception e) {
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, 
                "Alpaca 계정 정보 조회에 실패했습니다. Paper Trading 계정이 활성화되어 있는지 확인해주세요.");
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

}

