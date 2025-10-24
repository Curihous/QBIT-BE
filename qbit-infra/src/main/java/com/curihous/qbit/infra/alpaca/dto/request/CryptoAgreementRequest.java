package com.curihous.qbit.infra.alpaca.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * Alpaca crypto agreement 서명 요청 DTO
 * 
 * - Alpaca API: PATCH /v1/accounts/{account_id}
 * - QBIT API: POST /trading/crypto/agreements
 */
public record CryptoAgreementRequest(
    @JsonProperty("agreements")
    Agreement[] agreements
) {
    public record Agreement(
        @JsonProperty("agreement")
        String agreement,
        
        @JsonProperty("signed_at")
        OffsetDateTime signedAt,
        
        @JsonProperty("ip_address")
        String ipAddress
    ) {}
}
