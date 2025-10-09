package com.curihous.qbit.infra.alpaca.client;

import com.curihous.qbit.infra.alpaca.config.AlpacaClientConfig;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

// Alpaca OAuth 2.0 인증 API 호출을 위한 Feign 클라이언트
@FeignClient(
    name = "alpaca-oauth-client",
    url = "https://api.alpaca.markets",
    configuration = AlpacaClientConfig.class
)
public interface AlpacaOAuthClient {

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    AlpacaTokenResponse exchangeToken(
        @RequestHeader("Authorization") String basicAuth,
        @RequestBody String formBody
    );

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    AlpacaTokenResponse refreshToken(
        @RequestHeader("Authorization") String basicAuth,
        @RequestBody String formBody
    );

    @GetMapping("/v2/account")
    Map<String, Object> getAccount(@RequestHeader("Authorization") String bearerToken);

    default AlpacaTokenResponse exchangeToken(String code, String clientId, String redirectUri) {
        String basicAuth = "Basic " + Base64.getEncoder()
            .encodeToString((clientId + ":" + getClientSecret()).getBytes(StandardCharsets.UTF_8));
        
        String formBody = "grant_type=authorization_code" +
            "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
            "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        
        return exchangeToken(basicAuth, formBody);
    }

    default AlpacaTokenResponse refreshTokenWithCredentials(String refreshToken, String clientId) {
        String basicAuth = "Basic " + Base64.getEncoder()
            .encodeToString((clientId + ":" + getClientSecret()).getBytes(StandardCharsets.UTF_8));
        
        String formBody = "grant_type=refresh_token" +
            "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);
        
        return refreshToken(basicAuth, formBody);
    }

    default String getClientSecret() {
        return System.getenv("ALPACA_OAUTH_CLIENT_SECRET");
    }
}

