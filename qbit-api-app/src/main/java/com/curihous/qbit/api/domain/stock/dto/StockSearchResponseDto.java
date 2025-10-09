package com.curihous.qbit.api.domain.stock.dto;

import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaAssetResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 종목 목록 조회 응답 DTO
 * 
 * 사용 API:
 * - GET /stocks
 */
@Schema(description = "종목 목록 조회 응답")
public record StockSearchResponseDto(
    
    @Schema(description = "종목 코드", example = "AAPL")
    String symbol,
    
    @Schema(description = "종목명", example = "Apple Inc.")
    String name,
    
    @Schema(description = "거래소", example = "NASDAQ")
    String exchange,
    
    @Schema(description = "거래 가능 여부", example = "true")
    Boolean tradable
) {
    
    // Stock 엔티티를 StockSearchResponseDto로 변환 (DB 조회용)
    public static StockSearchResponseDto fromEntity(Stock stock) {
        return new StockSearchResponseDto(
                stock.getSymbol(),
                stock.getStockName(),
                stock.getExchange(),
                stock.getTradable()
        );
    }
    
    // AlpacaAssetResponse를 StockSearchResponseDto로 변환 (API 조회용)
    public static StockSearchResponseDto from(AlpacaAssetResponse alpacaAsset) {
        return new StockSearchResponseDto(
                alpacaAsset.symbol(),
                alpacaAsset.name(),
                alpacaAsset.exchange(),
                alpacaAsset.tradable()
        );
    }
}