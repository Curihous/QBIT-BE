package com.curihous.qbit.infra.yahoo.service;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.stock.entity.MarketIndex;
import com.curihous.qbit.domain.stock.port.MarketIndexPort;
import com.curihous.qbit.domain.stock.repository.MarketIndexRepository;
import com.curihous.qbit.infra.yahoo.client.YahooFinanceClient;
import com.curihous.qbit.infra.yahoo.dto.response.YahooFinanceChartResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Yahoo Finance를 통한 시장 지수 데이터 조회 Adapter
 * (Hexagonal Architecture - Adapter)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YahooFinanceMarketIndexService implements MarketIndexPort {

    private final YahooFinanceClient yahooFinanceClient;
    private final MarketIndexRepository marketIndexRepository;

    // 조회 가능한 주요 지수 심볼들 
    public static final List<String> MAJOR_INDEX_SYMBOLS = Arrays.asList(
            "^GSPC",  // S&P 500 - 미국 전체 시장
            "^IXIC",  // NASDAQ Composite - 기술주 중심
            "^DJI",   // Dow Jones Industrial Average - 대형주 중심
            "^VIX"    // VIX (변동성 지수) - 미국 시장 변동성 지수(공포 지수)
    );

    // 애플리케이션 시작 시 주요 지수 데이터 초기화
    @PostConstruct
    public void initMajorIndices() {
        try {
            log.info("주요 지수 데이터 초기화 시작...");
            syncMajorIndices();
            log.info("주요 지수 데이터 초기화 완료");
        } catch (Exception e) {
            log.warn("초기 지수 데이터 생성 실패 (서버는 정상 시작): {}", e.getMessage());
        }
    }

    // 배치 작업 - 주요 지수 데이터 동기화 (평일 15분마다)
    @Scheduled(cron = "0 */15 * * * MON-FRI", zone = "Asia/Seoul")
    @Transactional
    public void syncMajorIndices() {
        try {
            log.info("Yahoo Finance를 통한 주요 지수 데이터 동기화 시작...");
            
            for (String symbol : MAJOR_INDEX_SYMBOLS) {
                try {
                    updateIndexFromYahoo(symbol);
                } catch (Exception e) {
                    log.warn("지수 동기화 실패: {}", symbol, e);
                }
            }
            
            log.info("주요 지수 데이터 동기화 완료");
            
        } catch (Exception e) {
            log.error("주요 지수 데이터 동기화 실패", e);
        }
    }

    // 특정 지수 데이터 조회 및 업데이트
    @Override
    @Transactional
    public MarketIndex getOrFetchIndex(String symbol) {
        try {
            updateIndexFromYahoo(symbol);
            return marketIndexRepository.findBySymbol(symbol)
                    .orElseThrow(() -> new QbitException(ErrorCode.INDEX_NOT_FOUND));
        } catch (QbitException e) {
            throw e;
        } catch (Exception e) {
            log.error("지수 데이터 조회 실패: {}", symbol, e);
            throw new QbitException(ErrorCode.INDEX_DATA_UNAVAILABLE);
        }
    }

    // Yahoo Finance로부터 지수 업데이트
    private void updateIndexFromYahoo(String symbol) {
        try {
            YahooFinanceChartResponse yahooData = yahooFinanceClient.getChart(symbol);
            
            Optional<MarketIndex> existing = marketIndexRepository.findBySymbol(symbol);
            
            if (existing.isPresent()) {
                // 기존 데이터 업데이트
                MarketIndex index = existing.get();
                index.updatePrice(
                    yahooData.getCurrentPrice(),
                    yahooData.getPreviousClose(),
                    yahooData.getChangeAmount(),
                    yahooData.getChangePercentage()
                );
                index.updateVolume(yahooData.getVolume());
                marketIndexRepository.save(index);
                
                log.debug("지수 업데이트: {} = {}", symbol, yahooData.getCurrentPrice());
            } else {
                // 새 지수 생성
                createIndexFromYahooData(yahooData);
            }
        } catch (Exception e) {
            log.error("Yahoo Finance 지수 조회 실패: {}", symbol, e);
            throw e;
        }
    }

    // Yahoo 데이터로부터 새 지수 생성
    private MarketIndex createIndexFromYahooData(YahooFinanceChartResponse yahooData) {
        String symbol = yahooData.getSymbol();
        String name = getIndexName(symbol);
        
        MarketIndex index = MarketIndex.builder()
                .symbol(symbol)
                .name(name)
                .description(name + " 지수")
                .country("US")
                .currency("USD")
                .currentPrice(yahooData.getCurrentPrice())
                .previousClose(yahooData.getPreviousClose())
                .changeAmount(yahooData.getChangeAmount())
                .changePercentage(yahooData.getChangePercentage())
                .volume(yahooData.getVolume())
                .marketCap(null)
                .lastUpdated(LocalDateTime.now())
                .isActive(true)
                .build();
        
        MarketIndex saved = marketIndexRepository.save(index);
        log.info("새 지수 생성: {} - {}", symbol, name);
        
        return saved;
    }

    // 심볼로 지수명 매핑
    private String getIndexName(String symbol) {
        return switch (symbol) {
            case "^GSPC" -> "S&P 500";
            case "^IXIC" -> "NASDAQ Composite";
            case "^DJI" -> "Dow Jones Industrial Average";
            case "^VIX" -> "CBOE Volatility Index";
            default -> symbol.replace("^", "");
        };
    }

    // 과거 데이터 조회
    @Override
    public MarketIndexHistoryData getIndexHistory(String symbol, String from, String to) {
        try {
            // yyyy-MM-dd → Unix timestamp 변환
            long period1 = java.time.LocalDate.parse(from).atStartOfDay(java.time.ZoneId.of("UTC")).toEpochSecond();
            long period2 = java.time.LocalDate.parse(to).atStartOfDay(java.time.ZoneId.of("UTC")).toEpochSecond();
            
            YahooFinanceChartResponse yahooData = yahooFinanceClient.getChartHistory(symbol, "1d", period1, period2);
            
            // Yahoo Finance 응답을 Domain DTO로 변환
            return convertToHistoryData(yahooData);
        } catch (Exception e) {
            log.error("Yahoo Finance 과거 데이터 조회 실패: symbol={}, from={}, to={}", symbol, from, to, e);
            throw new QbitException(ErrorCode.INDEX_DATA_UNAVAILABLE, "지수 과거 데이터를 조회할 수 없습니다.");
        }
    }
    
    // 주요 지수 심볼 목록 반환
    @Override
    public List<String> getMajorIndexSymbols() {
        return MAJOR_INDEX_SYMBOLS;
    }
    
    // Yahoo Finance 응답을 Domain DTO로 변환
    private MarketIndexHistoryData convertToHistoryData(YahooFinanceChartResponse yahooData) {
        if (yahooData.getChart() == null || yahooData.getChart().getResult() == null 
            || yahooData.getChart().getResult().isEmpty()) {
            return new MarketIndexHistoryData(List.of());
        }

        YahooFinanceChartResponse.Result result = yahooData.getChart().getResult().get(0);
        List<Long> timestamps = result.getTimestamp();
        
        if (result.getIndicators() == null || result.getIndicators().getQuote() == null 
            || result.getIndicators().getQuote().isEmpty()) {
            return new MarketIndexHistoryData(List.of());
        }

        YahooFinanceChartResponse.Quote quote = result.getIndicators().getQuote().get(0);
        
        List<MarketIndexHistoryData.HistoryPoint> dataPoints = 
            java.util.stream.IntStream.range(0, timestamps.size())
                .mapToObj(i -> new MarketIndexHistoryData.HistoryPoint(
                    timestamps.get(i) * 1000, // 초 → 밀리초
                    quote.getOpen().get(i),
                    quote.getClose().get(i),
                    quote.getHigh().get(i),
                    quote.getLow().get(i),
                    quote.getVolume().get(i)
                ))
                .collect(Collectors.toList());
        
        return new MarketIndexHistoryData(dataPoints);
    }
}

