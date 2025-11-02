package com.curihous.qbit.api.domain.stock.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 암호화폐 호가창 응답 DTO
 * 
 * QBIT API: GET /stocks/crypto/orderbook/{binanceSymbol} (Binance API)
 */
public record OrderBookResponseDto(
    @Schema(description = "종목 심볼", example = "BTCUSDT")
    String symbol,
    
    @Schema(description = "매도 호가")
    List<OrderBookLevel> asks,
    
    @Schema(description = "매수 호가")
    List<OrderBookLevel> bids,
    
    @Schema(description = "업데이트 시간 (Unix timestamp)", example = "1696887456000")
    Long lastUpdateId
) {
    
    @Schema(description = "호가 레벨")
    public record OrderBookLevel(
        @Schema(description = "가격", example = "27123.5")
        BigDecimal price,
        
        @Schema(description = "수량", example = "0.002")
        BigDecimal quantity
    ) {}
    
    public static OrderBookResponseDto of(String symbol, List<OrderBookLevel> asks, 
                                        List<OrderBookLevel> bids, Long lastUpdateId) {
        return new OrderBookResponseDto(symbol, asks, bids, lastUpdateId);
    }
    
    public static OrderBookResponseDto fromBinance(String symbol, 
                                                   List<List<String>> binanceAsks,
                                                   List<List<String>> binanceBids,
                                                   Long lastUpdateId) {
        // 매도 호가 변환 (가격 낮은 순)
        List<OrderBookLevel> asks = convertToOrderBookLevels(binanceAsks)
                .stream()
                .sorted(Comparator.comparing(OrderBookLevel::price))
                .collect(Collectors.toList());
        
        // 매수 호가 변환 (가격 높은 순)
        List<OrderBookLevel> bids = convertToOrderBookLevels(binanceBids)
                .stream()
                .sorted(Comparator.comparing(OrderBookLevel::price).reversed())
                .collect(Collectors.toList());
        
        return new OrderBookResponseDto(symbol, asks, bids, lastUpdateId);
    }
    
    private static List<OrderBookLevel> convertToOrderBookLevels(List<List<String>> rawLevels) {
        if (rawLevels == null) {
            return Collections.emptyList();
        }
        
        return rawLevels.stream()
                .map(OrderBookResponseDto::convertToOrderBookLevel)
                .filter(level -> level != null)
                .collect(Collectors.toList());
    }
    
    private static OrderBookLevel convertToOrderBookLevel(List<String> level) {
        try {
            if (level == null || level.size() < 2) {
                return null;
            }
            return new OrderBookLevel(
                new BigDecimal(level.get(0)),
                new BigDecimal(level.get(1))
            );
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return null;
        }
    }
}
