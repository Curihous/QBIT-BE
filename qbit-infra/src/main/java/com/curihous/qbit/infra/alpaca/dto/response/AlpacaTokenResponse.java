package com.curihous.qbit.infra.alpaca.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Alpaca OAuth 토큰 응답 DTO
 * 
 * 사용 API:
 * - Alpaca API: POST /oauth/token
 * - QBIT API: GET /auth/alpaca/callback
 */
@JsonIgnoreProperties(ignoreUnknown = true) // 외부 API 응답 파싱 시 내가 정의하지 않은 필드는 무시
public record AlpacaTokenResponse(
    @Schema(description = "액세스 토큰")
    @JsonProperty("access_token")
    String accessToken,

    @Schema(description = "리프레시 토큰")
    @JsonProperty("refresh_token")
    String refreshToken,

    @Schema(description = "토큰 타입 (Bearer)")
    @JsonProperty("token_type")
    String tokenType,

    @Schema(description = "토큰 만료 시간 (초)")
    @JsonProperty("expires_in")
    Long expiresIn,

    @Schema(description = "OAuth 스코프")
    @JsonProperty("scope")
    String scope,

    @Schema(description = "Alpaca 사용자 ID")
    @JsonProperty("alpaca_user_id")
    String alpacaUserId
) {
}
