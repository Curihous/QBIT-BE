package com.curihous.qbit.api.domain.alpaca.dto.response;

import com.curihous.qbit.domain.order.port.TradingPort;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Alpaca 계정 정보 응답 DTO
 * 
 * 사용 API:
 * - GET /alpaca/account
 */
public record AccountInfoResponseDto(
    @Schema(description = "계정 번호", example = "PA2J8NKJ1M7P")
    String accountNumber,
    
    @Schema(description = "계정 상태", example = "ACTIVE")
    String status,
    
    @Schema(description = "통화", example = "USD")
    String currency,
    
    @Schema(description = "매수 가능 금액", example = "100000.00")
    String buyingPower,
    
    @Schema(description = "현금", example = "100000.00")
    String cash,
    
    @Schema(description = "포트폴리오 총 가치", example = "100500.00")
    String portfolioValue,
    
    @Schema(description = "자산 가치 (현금 + 포지션)", example = "100500.00")
    String equity,
    
    @Schema(description = "전일 자산 가치", example = "100000.00")
    String lastEquity,
    
    @Schema(description = "롱 포지션(매수) 시장 가치", example = "500.00")
    String longMarketValue
) {
    public static AccountInfoResponseDto from(TradingPort.AccountInfo account) {
        return new AccountInfoResponseDto(
            account.accountNumber(),
            account.status(),
            account.currency(),
            account.buyingPower(),
            account.cash(),
            account.portfolioValue(),
            account.equity(),
            account.lastEquity(),
            account.longMarketValue()
        );
    }
}
