package com.curihous.qbit.api.domain.portfolio.dto.response;

import com.curihous.qbit.domain.order.port.TradingPort;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 포트폴리오 포지션 정보 응답 DTO
 * 
 * QBIT API: GET /portfolio/positions
 */
public record PositionResponseDto(
    @Schema(description = "종목 심볼", example = "AAPL")
    String symbol,
    
    @Schema(description = "보유 수량", example = "10")
    String quantity,
    
    @Schema(description = "평균 매수가", example = "150.25")
    String avgEntryPrice,
    
    @Schema(description = "시장 가치 (현재가 기준)", example = "1502.50")
    String marketValue,
    
    @Schema(description = "원가 기준 (총 매수 금액)", example = "1500.00")
    String costBasis,
    
    @Schema(description = "미실현 손익 (금액)", example = "2.50")
    String unrealizedPl,
    
    @Schema(description = "미실현 손익률 (%)", example = "0.0167")
    String unrealizedPlpc,
    
    @Schema(description = "현재 가격", example = "150.25")
    String currentPrice,
    
    @Schema(description = "포지션 방향", example = "long")
    String side
) {
    public static PositionResponseDto from(TradingPort.PositionInfo position) {
        return new PositionResponseDto(
            position.symbol(),
            position.quantity(),
            position.avgEntryPrice(),
            position.marketValue(),
            position.costBasis(),
            position.unrealizedPl(),
            position.unrealizedPlpc(),
            position.currentPrice(),
            position.side()
        );
    }
}
