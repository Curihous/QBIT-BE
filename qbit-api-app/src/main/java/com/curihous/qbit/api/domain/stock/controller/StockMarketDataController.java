package com.curihous.qbit.api.domain.stock.controller;

import com.curihous.qbit.api.domain.stock.dto.response.QuoteResponseDto;
import com.curihous.qbit.api.domain.stock.dto.response.CandleResponseDto;
import com.curihous.qbit.api.domain.stock.dto.response.OrderBookResponseDto;
import com.curihous.qbit.infra.finnhub.service.FinnhubMarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Stock - Realtime Market Data", description = "실시간 시장 데이터 API (Finnhub API) - 시세, 차트, 호가창")
@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockMarketDataController {

    private final FinnhubMarketService finnhubMarketService;

    @Operation(
        summary = "실시간 시세 조회", 
        description = "종목의 현재가, 고가, 저가, 시가, 전일가 등 실시간 시세 정보를 조회합니다."
    )
    @GetMapping("/quote/{symbol}")
    public ResponseEntity<QuoteResponseDto> getQuote(
        @Parameter(description = "종목 심볼", example = "AAPL")
        @PathVariable String symbol
    ) {
        var finnhubQuote = finnhubMarketService.getQuote(symbol);
        QuoteResponseDto quote = QuoteResponseDto.from(symbol, finnhubQuote);
        return ResponseEntity.ok(quote);
    }

    @Operation(
        summary = "차트 데이터 조회", 
        description = "종목의 OHLCV 캔들 데이터를 조회합니다. (1분봉, 5분봉, 1시간봉, 일봉 등)"
    )
    @GetMapping("/candle/{symbol}")
    public ResponseEntity<CandleResponseDto> getCandle(
        @Parameter(description = "종목 심볼", example = "AAPL")
        @PathVariable String symbol,
        @Parameter(description = "차트 해상도 (1, 5, 15, 30, 60, D, W, M)", example = "1")
        @RequestParam(defaultValue = "1") String resolution,
        @Parameter(description = "시작 시간 (Unix timestamp)", example = "1696800000")
        @RequestParam long from,
        @Parameter(description = "종료 시간 (Unix timestamp)", example = "1696886400")
        @RequestParam long to
    ) {
        var finnhubCandle = finnhubMarketService.getCandle(symbol, resolution, from, to);
        CandleResponseDto candle = CandleResponseDto.from(symbol, resolution, finnhubCandle);
        return ResponseEntity.ok(candle);
    }

    @Operation(
        summary = "암호화폐 호가창 조회", 
        description = "암호화폐의 실시간 매수/매도 호가 정보를 조회합니다."
    )
    @GetMapping("/orderbook/{symbol}")
    public ResponseEntity<OrderBookResponseDto> getCryptoOrderBook(
        @Parameter(description = "암호화폐 심볼 (BINANCE:BTCUSDT 형식)", example = "BINANCE:BTCUSDT")
        @PathVariable String symbol
    ) {
        var finnhubOrderBook = finnhubMarketService.getCryptoOrderBook(symbol);
        OrderBookResponseDto orderBook = OrderBookResponseDto.from(symbol, finnhubOrderBook);
        return ResponseEntity.ok(orderBook);
    }
}
