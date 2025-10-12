package com.curihous.qbit.infra.finnhub.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Finnhub 암호화폐 호가창 응답 DTO
 * 
 * GET /crypto/orderbook?symbol=BINANCE:BTCUSDT&token=YOUR_TOKEN
 */
public record FinnhubCryptoOrderBookResponse(
    @JsonProperty("asks")
    @Schema(description = "매도 호가 (Ask Orders) - [가격, 수량] 배열", example = "[[\"27123.5\", \"0.002\"], [\"27124.0\", \"0.01\"]]")
    List<List<String>> asks,
    
    @JsonProperty("bids")
    @Schema(description = "매수 호가 (Bid Orders) - [가격, 수량] 배열", example = "[[\"27120.0\", \"0.05\"], [\"27119.5\", \"0.002\"]]")
    List<List<String>> bids,
    
    @JsonProperty("t")
    @Schema(description = "타임스탬프 (Timestamp)", example = "1696887456000")
    Long timestamp
) {}
