package com.curihous.qbit.client.stock.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockInfoResponse {
    
    private String ticker;
    private String stockName;
    private String market;
    private String sector;
    private String logoUrl;
    private String description;
}
