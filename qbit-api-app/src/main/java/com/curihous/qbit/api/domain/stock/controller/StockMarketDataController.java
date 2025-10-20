package com.curihous.qbit.api.domain.stock.controller;

import com.curihous.qbit.api.domain.stock.dto.response.QuoteResponseDto;
import com.curihous.qbit.api.domain.stock.dto.response.CandleResponseDto;
import com.curihous.qbit.api.domain.stock.dto.response.OrderBookResponseDto;
import com.curihous.qbit.infra.binance.service.BinanceMarketService;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 헥사고날 아키텍처: Infrastructure → Domain 변환
@Slf4j
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
    @GetMapping("/quote/{binanceSymbol}")
    public ResponseEntity<QuoteResponseDto> getQuote(
        @Parameter(description = "Binance 심볼", example = "BTCUSDT")
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
    @GetMapping("/candle/{binanceSymbol}")
    public ResponseEntity<CandleResponseDto> getCandle(
        @Parameter(description = "Binance 심볼", example = "BTCUSDT")
        @PathVariable String binanceSymbol,
        @Parameter(description = "차트 해상도 (1m, 5m, 15m, 30m, 1h, 4h, 1d, 1w, 1M)", example = "1d")
        @RequestParam(defaultValue = "1d") String interval,
        @Parameter(description = "시작 시간 (Unix timestamp)", example = "1696800000")
        @RequestParam long startTime,
        @Parameter(description = "종료 시간 (Unix timestamp)", example = "1696886400")
        @RequestParam long endTime
    ) {
        var binanceKlines = binanceMarketService.getKlines(binanceSymbol, interval, startTime, endTime);
        
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
        
        CandleResponseDto candle = CandleResponseDto.of(binanceSymbol, interval, candles);
        return ResponseEntity.ok(candle);
    }

    @Operation(
        summary = "암호화폐 호가창 조회", 
        description = "암호화폐의 매수/매도 호가 정보를 조회합니다.(일회성 조회, 사용자가 페이지 로딩 후 즉시 데이터 확인용)"
    )
    @GetMapping("/orderbook/{binanceSymbol}")
    public ResponseEntity<OrderBookResponseDto> getCryptoOrderBook(
        @Parameter(description = "Binance 심볼", example = "BTCUSDT")
        @PathVariable String binanceSymbol
    ) {
        var binanceOrderBook = binanceMarketService.getOrderBook(binanceSymbol);
        
        // 매도 호가 (가격 낮은 순)
        List<OrderBookResponseDto.OrderBookLevel> asks = convertToOrderBookLevels(binanceOrderBook.getAsks())
            .stream()
            .sorted(Comparator.comparing(OrderBookResponseDto.OrderBookLevel::price))
            .toList();
        
        // 매수 호가 (가격 높은 순)
        List<OrderBookResponseDto.OrderBookLevel> bids = convertToOrderBookLevels(binanceOrderBook.getBids())
            .stream()
            .sorted(Comparator.comparing(OrderBookResponseDto.OrderBookLevel::price).reversed())
            .toList();
        
        return ResponseEntity.ok(OrderBookResponseDto.of(binanceSymbol, asks, bids, binanceOrderBook.getLastUpdateId()));
    }

    
    // =======================================================
    // 호가 변환 헬퍼 메서드
    // =======================================================
    private List<OrderBookResponseDto.OrderBookLevel> convertToOrderBookLevels(List<List<String>> rawLevels) {
        if (rawLevels == null) {
            return Collections.emptyList();
        }
        
        return rawLevels.stream()
            .map(this::convertToOrderBookLevel)
            .filter(level -> level != null)
            .toList();
    }
    
    private OrderBookResponseDto.OrderBookLevel convertToOrderBookLevel(List<String> level) {
        try {
            if (level == null || level.size() < 2) {
                return null;
            }
            return new OrderBookResponseDto.OrderBookLevel(
                new BigDecimal(level.get(0)),
                new BigDecimal(level.get(1))
            );
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return null;
        }
    }
}
