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
    @Schema(description = "계정 ID (Alpaca 내부 ID)", example = "account-id")
    String accountId,
    
    @Schema(description = "계정 번호 (사용자 식별용)", example = "PA2J8NKJ1M7P")
    String accountNumber,
    
    @Schema(description = "계정 상태", example = "ACTIVE")
    String status,
    
    @Schema(description = "암호화폐 거래 상태", example = "ACTIVE")
    String cryptoStatus,
    
    @Schema(description = "거래 차단 여부", example = "false")
    boolean tradingBlocked,
    
    @Schema(description = "계정 차단 여부", example = "false")
    boolean accountBlocked,
    
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
    String longMarketValue,
    
    @Schema(description = "숏 포지션(매도) 시장 가치", example = "0.00")
    String shortMarketValue,
    
    @Schema(description = "레버리지 배수(마진 계정에 따라)", example = "1")
    String multiplier
) {
    public static AccountInfoResponseDto from(TradingPort.AccountInfo account) {
        return new AccountInfoResponseDto(
            account.accountId(),                  
            account.accountNumber(),         
            account.status(),                  
            account.cryptoStatus(),                 
            account.tradingBlocked(),               
            account.accountBlocked(),               
            account.currency(),                      
            account.buyingPower(),                  
            account.cash(),                         
            account.portfolioValue(),               
            account.equity(),                       
            account.lastEquity(),                   
            account.longMarketValue(),              
            account.shortMarketValue(),
            account.multiplier()              
        );
    }
}
