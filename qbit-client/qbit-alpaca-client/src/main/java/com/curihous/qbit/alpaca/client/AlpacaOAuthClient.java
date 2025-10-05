package com.curihous.qbit.alpaca.client;

import com.curihous.qbit.alpaca.config.AlpacaClientConfig;
import com.curihous.qbit.alpaca.dto.response.AlpacaTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Alpaca OAuth 2.0 인증 API 호출을 위한 Feign 클라이언트
 */
@FeignClient(
    name = "alpaca-oauth-client",
    url = "https://api.alpaca.markets",
    configuration = AlpacaClientConfig.class
)
public interface AlpacaOAuthClient {

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    AlpacaTokenResponse exchangeToken(
        @RequestParam("grant_type") String grantType,
        @RequestParam("code") String code,
        @RequestParam("client_id") String clientId,
        @RequestParam("client_secret") String clientSecret,
        @RequestParam("redirect_uri") String redirectUri
    );

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    AlpacaTokenResponse refreshToken(
        @RequestParam("grant_type") String grantType,
        @RequestParam("refresh_token") String refreshToken,
        @RequestParam("client_id") String clientId,
        @RequestParam("client_secret") String clientSecret
    );

    default AlpacaTokenResponse exchangeToken(String code, String clientId, String redirectUri) {
        return exchangeToken("authorization_code", code, clientId, getClientSecret(), redirectUri);
    }

    default AlpacaTokenResponse refreshToken(String refreshToken, String clientId) {
        return refreshToken("refresh_token", refreshToken, clientId, getClientSecret());
    }

    default String getClientSecret() {
        return System.getenv("ALPACA_OAUTH_CLIENT_SECRET");
    }
}
