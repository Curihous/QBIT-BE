package com.curihous.qbit.infra.fmp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Financial Modeling Prep S&P500 구성 종목 응답 DTO
 *
 * Financial Modeling Prep API: GET /api/v3/sp500_constituent
 * 종목 저장 시 S&P500 편입 여부 판별용
 */
@Data
public class FmpSp500ConstituentResponse {

    @JsonProperty("symbol")
    private String symbol;
}

