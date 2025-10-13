package com.curihous.qbit.infra.binance.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Binance 호가창 조회 응답 DTO
 * 
 * 사용 API: GET /api/v3/depth
 */
@Data
public class BinanceOrderBookResponse {
    
    @JsonProperty("lastUpdateId")
    private Long lastUpdateId;
    
    @JsonProperty("bids")
    private List<List<String>> bids;
    
    @JsonProperty("asks")
    private List<List<String>> asks;
}
