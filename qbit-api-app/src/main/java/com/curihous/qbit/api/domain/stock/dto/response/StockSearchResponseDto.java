package com.curihous.qbit.api.domain.stock.dto.response;

import com.curihous.qbit.domain.stock.entity.Stock;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 종목 검색 응답 DTO
 * 
 * QBIT API: GET /stocks/search
 */
@Schema(description = "종목 검색 응답")
public record StockSearchResponseDto(
    
    @Schema(description = "종목 코드", example = "AAPL")
    String symbol,
    
    @Schema(description = "종목명", example = "Apple Inc.")
    String name,

    
    @Schema(description = "자산 클래스", example = "us_equity", allowableValues = {"us_equity", "crypto"})
    String assetClass,
    
    @Schema(description = "거래 가능 여부", example = "true")
    Boolean tradable,
    
    @Schema(description = "로고 이미지 URL (Clearbit)", example = "https://logo.clearbit.com/apple.com")
    String logoUrl
) {
    
    public static StockSearchResponseDto fromEntity(Stock stock) {
        return new StockSearchResponseDto(
                stock.getSymbol(),
                stock.getStockName(),
                stock.getAssetClass(),
                stock.getTradable(),
                stock.getLogoUrl()
        );
    }
}

