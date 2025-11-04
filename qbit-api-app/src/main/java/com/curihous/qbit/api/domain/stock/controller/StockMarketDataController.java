package com.curihous.qbit.api.domain.stock.controller;

import com.curihous.qbit.api.domain.stock.dto.response.QuoteResponseDto;
import com.curihous.qbit.api.domain.stock.dto.response.CandleResponseDto;
import com.curihous.qbit.common.util.TimeZoneConverter;
import com.curihous.qbit.infra.binance.service.BinanceMarketService;
import com.curihous.qbit.infra.massive.service.MassiveMarketService;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
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
        description = """
            암호화폐의 OHLCV 캔들 데이터를 조회합니다. (Binance API - 1분봉, 5분봉, 1시간봉, 일봉 등)
            - startTime과 endTime은 모두 optional입니다.
            - startTime만 제공: endTime은 자동으로 startTime부터 최대 200일 후로 설정됩니다 (현재 시간 초과 불가).
            - endTime만 제공: startTime은 자동으로 endTime부터 최대 200일 전으로 설정됩니다.
            - 둘 다 제공하지 않으면 최근 데이터를 반환합니다 (limit로 개수 제한, 기본값 500, 최대 1000).
            """
    )
    @GetMapping("/crypto/candle/{binanceSymbol}")
    public ResponseEntity<CandleResponseDto> getCandle(
        @Parameter(description = "Binance 심볼 (예: BTCUSDT, ETHUSDT)", example = "BTCUSDT", required = true)
        @PathVariable String binanceSymbol,
        
        @Parameter(description = "캔들 간격 (1m, 3m, 5m, 15m, 30m, 1h, 2h, 4h, 6h, 8h, 12h, 1d, 3d, 1w, 1M)", example = "1d")
        @RequestParam(defaultValue = "1d") String interval,
        
        @Parameter(description = "시작 시간 (Unix 타임스탬프, 밀리초 단위, 한국 시간 KST 기준). 선택적. startTime만 제공하면 endTime은 자동으로 설정됩니다 (최대 200일 후, 현재 시간 초과 불가).", example = "1735689600000")
        @RequestParam(required = false) Long startTime,
        
        @Parameter(description = "종료 시간 (Unix 타임스탬프, 밀리초 단위, 한국 시간 KST 기준). 선택적. endTime만 제공하면 startTime은 자동으로 설정됩니다 (최대 200일 전).", example = "1735776000000")
        @RequestParam(required = false) Long endTime
    ) {
        // 한국 시간(KST)을 UTC로 변환하여 Binance API 호출
        Long utcStartTime = TimeZoneConverter.kstToUtc(startTime);
        Long utcEndTime = TimeZoneConverter.kstToUtc(endTime);
        
        var binanceKlines = binanceMarketService.getKlines(binanceSymbol, interval, utcStartTime, utcEndTime);
        
        // Binance 응답(UTC)을 한국 시간(KST)으로 변환하여 반환
        CandleResponseDto candle = CandleResponseDto.fromBinance(binanceSymbol, interval, binanceKlines);
        return ResponseEntity.ok(candle);
    }

    @Operation(
        summary = "미국 주식 실시간 시세 조회(15분 지연)", 
        description = "미국 주식의 현재가, 고가, 저가, 시가, 전일가 등 실시간 시세 정보를 조회합니다. (Massive.io API)"
    )
    @GetMapping("/us-equity/quote/{ticker}")
    public ResponseEntity<QuoteResponseDto> getUsEquityQuote(
        @Parameter(description = "종목 티커 심볼", example = "AAPL", required = true)
        @PathVariable String ticker
    ) {
        var snapshot = massiveMarketService.getSnapshot(ticker);
        
        QuoteResponseDto quote = QuoteResponseDto.fromMassiveSnapshot(ticker, snapshot);
        return ResponseEntity.ok(quote);
    }

    @Operation(
        summary = "미국 주식 차트 데이터 조회", 
        description = "미국 주식의 OHLCV 캔들 데이터를 조회합니다. (Massive.io API - 1분봉, 5분봉, 1시간봉, 일봉 등)"
    )
    @GetMapping("/us-equity/candle/{ticker}")
    public ResponseEntity<CandleResponseDto> getUsEquityCandle(
        @Parameter(description = "종목 티커 심볼", example = "AAPL", required = true)
        @PathVariable String ticker,
        
        @Parameter(description = "시간 배수", example = "1")
        @RequestParam(defaultValue = "1") Integer multiplier,
        
        @Parameter(description = "시간 단위 (minute, hour, day, week, month)", example = "day")
        @RequestParam(defaultValue = "day") String timespan,
        
        @Parameter(description = "시작 날짜 (yyyy-MM-dd)", example = "2025-04-01", required = true)
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        
        @Parameter(description = "종료 날짜 (yyyy-MM-dd)", example = "2025-04-06", required = true)
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        
        @Parameter(description = "조정 가격 여부 (true: 분할/배당 반영, false: 원시 가격)", example = "true")
        @RequestParam(defaultValue = "true") Boolean adjusted
    ) {
        var aggregates = massiveMarketService.getAggregates(ticker, multiplier, timespan, from, to, adjusted);
        
        CandleResponseDto candle = CandleResponseDto.fromMassive(ticker, multiplier, timespan, aggregates);
        return ResponseEntity.ok(candle);
    }
}
