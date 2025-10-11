package com.curihous.qbit.api.domain.stock.dto;

import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaAssetResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 종목 상세 조회 응답 DTO
 * 
 * 사용 API:
 * - GET /stocks/{symbol}
 */
@Schema(description = "종목 상세 조회 응답")
public record StockDetailResponseDto(
    
    @Schema(description = "종목 코드", example = "AAPL")
    String symbol,
    
    @Schema(description = "종목명", example = "Apple Inc.")
    String name,
    
    @Schema(description = "거래소", example = "NASDAQ")
    String exchange,
    
    @Schema(description = "자산 클래스 (주식/ETF 구분)", example = "us_equity")
    String assetClass,
    
    @Schema(description = "거래 상태", example = "active")
    String status,
    
    @Schema(description = "거래 가능 여부", example = "true")
    Boolean tradable,
    
    @Schema(description = "소수점 거래 가능 여부 (1주 미만 구매 가능)", example = "true")
    Boolean fractionable,
    
    @Schema(description = "최소 주문 수량", example = "1")
    String minOrderSize,
    
    @Schema(description = "최소 거래 증분 (프론트엔드 입력 폼 step 속성용)", example = "0.01")
    String minTradeIncrement,
    
    @Schema(description = "가격 증분 (프론트엔드 가격 입력 폼 step 속성용)", example = "0.01")
    String priceIncrement,
    
    @Schema(description = "로고 이미지 URL (Clearbit)", example = "https://logo.clearbit.com/apple.com")
    String logoUrl
) {
    
    // Stock 엔티티를 StockDetailResponseDto로 변환 (DB 조회용)
    public static StockDetailResponseDto fromEntity(Stock stock) {
        return new StockDetailResponseDto(
                stock.getSymbol(),
                stock.getStockName(),
                stock.getExchange(),
                stock.getAssetClass(),
                stock.getStatus(),
                stock.getTradable(),
                stock.getFractionable(),
                stock.getMinOrderSize(),
                stock.getMinTradeIncrement(),
                stock.getPriceIncrement(),
                stock.getLogoUrl()
        );
    }
    
    // AlpacaAssetResponse를 StockDetailResponseDto로 변환 (API 조회용)
    public static StockDetailResponseDto from(AlpacaAssetResponse alpacaAsset) {
        return new StockDetailResponseDto(
                alpacaAsset.symbol(),
                alpacaAsset.name(),
                alpacaAsset.exchange(),
                alpacaAsset.assetClass(),
                alpacaAsset.status(),
                alpacaAsset.tradable(),
                alpacaAsset.fractionable(),
                alpacaAsset.minOrderSize(),
                alpacaAsset.minTradeIncrement(),
                alpacaAsset.priceIncrement(),
                null // 추후 Clearbit 통해 로고 추가
        );
    }
}
