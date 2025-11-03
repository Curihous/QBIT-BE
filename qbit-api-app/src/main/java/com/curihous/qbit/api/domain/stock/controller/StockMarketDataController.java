package com.curihous.qbit.api.domain.stock.controller;

import com.curihous.qbit.api.domain.stock.dto.response.QuoteResponseDto;
import com.curihous.qbit.api.domain.stock.dto.response.CandleResponseDto;
import com.curihous.qbit.infra.binance.service.BinanceMarketService;
import com.curihous.qbit.infra.massive.service.MassiveMarketService;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Stock - Realtime Market Data", description = "실시간 시장 데이터 API(시세, 차트, 호가창) - 암호화폐 (Binance), 미국 주식 (Massive.io)")
@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockMarketDataController {

    private final BinanceMarketService binanceMarketService;
    private final MassiveMarketService massiveMarketService;

    @Operation(
        summary = "암호화폐 실시간 시세 조회", 
        description = "암호화폐의 현재가, 고가, 저가, 시가, 전일가 등 실시간 시세 정보를 조회합니다. (Binance API)"
    )
    @GetMapping("/crypto/quote/{binanceSymbol}")
    public ResponseEntity<QuoteResponseDto> getQuote(
        @PathVariable String binanceSymbol
    ) {
        var binanceTicker = binanceMarketService.get24hrTicker(binanceSymbol);
        
        QuoteResponseDto quote = QuoteResponseDto.of(
            binanceSymbol,
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
    @GetMapping("/crypto/candle/{binanceSymbol}")
    public ResponseEntity<CandleResponseDto> getCandle(
        @PathVariable String binanceSymbol,
        @RequestParam(defaultValue = "1d") String interval,
        @RequestParam long startTime,
        @RequestParam long endTime
    ) {
        var binanceKlines = binanceMarketService.getKlines(binanceSymbol, interval, startTime, endTime);
        
        CandleResponseDto candle = CandleResponseDto.fromBinance(binanceSymbol, interval, binanceKlines);
        return ResponseEntity.ok(candle);
    }

    @Operation(
        summary = "미국 주식 실시간 시세 조회", 
        description = "미국 주식의 현재가, 고가, 저가, 시가, 전일가 등 실시간 시세 정보를 조회합니다. (Massive.io API)"
    )
    @GetMapping("/us-equity/quote/{ticker}")
    public ResponseEntity<QuoteResponseDto> getUsEquityQuote(
        @PathVariable String ticker
    ) {
        var previousClose = massiveMarketService.getPreviousClose(ticker);
        var lastQuote = massiveMarketService.getLastQuote(ticker);
        
        QuoteResponseDto quote = QuoteResponseDto.fromMassive(ticker, previousClose, lastQuote);
        return ResponseEntity.ok(quote);
    }

    @Operation(
        summary = "미국 주식 차트 데이터 조회", 
        description = "미국 주식의 OHLCV 캔들 데이터를 조회합니다. (Massive.io API - 1분봉, 5분봉, 1시간봉, 일봉 등)"
    )
    @GetMapping("/us-equity/candle/{ticker}")
    public ResponseEntity<CandleResponseDto> getUsEquityCandle(
        @PathVariable String ticker,
        @RequestParam(defaultValue = "1") Integer multiplier,  // 시간 배수 (1, 5, 15, 30, 60분봉)
        @RequestParam(defaultValue = "day") String timespan,   // 시간 단위 (minute, hour, day, week, month)
        @RequestParam LocalDate from,
        @RequestParam LocalDate to,
        @RequestParam(defaultValue = "true") Boolean adjusted  // true: 조정 가격(분할/배당 반영), false: 원시 가격
    ) {
        var aggregates = massiveMarketService.getAggregates(ticker, multiplier, timespan, from, to, adjusted);
        
        CandleResponseDto candle = CandleResponseDto.fromMassive(ticker, multiplier, timespan, aggregates);
        return ResponseEntity.ok(candle);
    }
}
