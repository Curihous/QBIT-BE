package com.curihous.qbit.api.domain.stock.controller;

import com.curihous.qbit.api.domain.stock.dto.response.QuoteResponseDto;
import com.curihous.qbit.api.domain.stock.dto.response.CandleResponseDto;
import com.curihous.qbit.api.domain.stock.dto.response.OrderBookResponseDto;
import com.curihous.qbit.infra.binance.service.BinanceMarketService;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 헥사고날 아키텍처: Infrastructure → Domain 변환
@Tag(name = "Stock - Realtime Market Data", description = "실시간 시장 데이터 API (Binance API) - 시세, 차트, 호가창")
@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockMarketDataController {

    private final BinanceMarketService binanceMarketService;

    @Operation(
        summary = "암호화폐 실시간 시세 조회", 
        description = "암호화폐의 현재가, 고가, 저가, 시가, 전일가 등 실시간 시세 정보를 조회합니다. (Binance API)"
    )
    @GetMapping("/quote/{symbol}")
    public ResponseEntity<QuoteResponseDto> getQuote(
        @Parameter(description = "종목 심볼", example = "BTCUSDT")
        @PathVariable String symbol
    ) {
        var binanceTicker = binanceMarketService.get24hrTicker(symbol);
        
        QuoteResponseDto quote = QuoteResponseDto.of(
            symbol,
            Double.parseDouble(binanceTicker.getLastPrice()),
            Double.parseDouble(binanceTicker.getHighPrice()),
            Double.parseDouble(binanceTicker.getLowPrice()),
            Double.parseDouble(binanceTicker.getOpenPrice()),
            Double.parseDouble(binanceTicker.getPrevClosePrice()),
            Double.parseDouble(binanceTicker.getPriceChange()),
            Double.parseDouble(binanceTicker.getPriceChangePercent()),
            binanceTicker.getCloseTime()
        );
        
        return ResponseEntity.ok(quote);
    }

    @Operation(
        summary = "암호화폐 차트 데이터 조회", 
        description = "암호화폐의 OHLCV 캔들 데이터를 조회합니다. (Binance API - 1분봉, 5분봉, 1시간봉, 일봉 등)"
    )
    @GetMapping("/candle/{symbol}")
    public ResponseEntity<CandleResponseDto> getCandle(
        @Parameter(description = "종목 심볼", example = "BTCUSDT")
        @PathVariable String symbol,
        @Parameter(description = "차트 해상도 (1m, 5m, 15m, 30m, 1h, 4h, 1d, 1w, 1M)", example = "1d")
        @RequestParam(defaultValue = "1d") String interval,
        @Parameter(description = "시작 시간 (Unix timestamp)", example = "1696800000")
        @RequestParam long startTime,
        @Parameter(description = "종료 시간 (Unix timestamp)", example = "1696886400")
        @RequestParam long endTime
    ) {
        var binanceKlines = binanceMarketService.getKlines(symbol, interval, startTime, endTime);
        
        List<CandleResponseDto.CandleData> candles = binanceKlines.stream()
            .map(kline -> new CandleResponseDto.CandleData(
                Long.parseLong(kline.get(0)),      // openTime
                Double.parseDouble(kline.get(1)),  // open
                Double.parseDouble(kline.get(2)),  // high
                Double.parseDouble(kline.get(3)),  // low
                Double.parseDouble(kline.get(4)),  // close
                Double.parseDouble(kline.get(5))   // volume
            ))
            .toList();
        
        CandleResponseDto candle = CandleResponseDto.of(symbol, interval, candles);
        return ResponseEntity.ok(candle);
    }

    @Operation(
        summary = "암호화폐 호가창 조회", 
        description = "암호화폐의 매수/매도 호가 정보를 조회합니다.(일회성 조회, 사용자가 페이지 로딩 후 즉시 데이터 확인용)"
    )
    @GetMapping("/orderbook/{symbol}")
    public ResponseEntity<OrderBookResponseDto> getCryptoOrderBook(
        @Parameter(description = "암호화폐 심볼", example = "BTCUSDT")
        @PathVariable String symbol
    ) {
        var binanceOrderBook = binanceMarketService.getOrderBook(symbol);
        
        List<OrderBookResponseDto.OrderBookLevel> asks = binanceOrderBook.getAsks().stream()
            .map(ask -> new OrderBookResponseDto.OrderBookLevel(ask.get(0), ask.get(1)))
            .toList();
            
        List<OrderBookResponseDto.OrderBookLevel> bids = binanceOrderBook.getBids().stream()
            .map(bid -> new OrderBookResponseDto.OrderBookLevel(bid.get(0), bid.get(1)))
            .toList();
        
        OrderBookResponseDto orderBook = OrderBookResponseDto.of(symbol, asks, bids, binanceOrderBook.getLastUpdateId());
        return ResponseEntity.ok(orderBook);
    }
}
