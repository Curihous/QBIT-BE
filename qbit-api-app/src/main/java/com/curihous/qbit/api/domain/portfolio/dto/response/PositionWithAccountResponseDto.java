package com.curihous.qbit.api.domain.portfolio.dto.response;

import com.curihous.qbit.domain.order.port.TradingPort;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 포트폴리오 포지션 정보 응답 DTO (자산 정보 포함) - 주문용
 * 
 * QBIT API: GET /portfolios/positions/{symbol}
 */
public record PositionWithAccountResponseDto(
    @Schema(description = "포지션 정보")
    SimplePositionDto position,
    
    @Schema(description = "계정 정보 (매수 최대치 계산용)")
    AccountInfoDto account
) {
    public static PositionWithAccountResponseDto from(TradingPort.SimplePositionWithAccountInfo data) {
        return new PositionWithAccountResponseDto(
            new SimplePositionDto(
                data.position().symbol(),
                data.position().quantity(),
                data.position().side()
            ),
            new AccountInfoDto(
                data.account().buyingPower()
            )
        );
    }
    
    public record SimplePositionDto(
        @Schema(description = "종목 심볼", example = "AAPL")
        String symbol,
        
        @Schema(description = "보유 수량 (매도 최대치)", example = "10")
        String quantity,
        
        @Schema(description = "포지션 방향", example = "long")
        String side
    ) {
    }
    
    public record AccountInfoDto(
        @Schema(description = "매수 가능 금액 (매수 최대치 계산용)", example = "100000.00")
        String buyingPower // 레퍼리지 포함 매수 가능 금액
    ) {
    }
}

