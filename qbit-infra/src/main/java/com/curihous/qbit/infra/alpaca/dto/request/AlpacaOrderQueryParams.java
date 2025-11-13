package com.curihous.qbit.infra.alpaca.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Alpaca GET /v2/orders API 쿼리 파라미터 DTO
 * 
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlpacaOrderQueryParams {
    private String status;
    private Integer limit;
    private String direction;
    private Boolean nested;
    private String after;
    private String until;
    private String side;
    private String symbol;
}

