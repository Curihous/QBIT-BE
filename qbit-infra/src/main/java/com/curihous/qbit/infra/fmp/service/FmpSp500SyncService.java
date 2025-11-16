package com.curihous.qbit.infra.fmp.service;

import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.stock.repository.StockRepository;
import com.curihous.qbit.infra.fmp.client.FinancialModelingPrepClient;
import com.curihous.qbit.infra.fmp.dto.FmpSp500ConstituentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FmpSp500SyncService {

    private final FinancialModelingPrepClient fmpClient;
    private final StockRepository stockRepository;

    @Value("${financialmodelingprep.api-key:}")
    private String apiKey;

    // 인메모리 캐시 (동일 인스턴스 내에서 재사용)
    private volatile Set<String> cachedSp500Symbols = new HashSet<>();

    // FMP API를 호출하여 S&P500 구성 종목을 조회하고 stocks.sp500 플래그를 일괄 갱신
    @Transactional
    public void syncSp500Flags() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Financial Modeling Prep API 키가 설정되지 않았습니다. sp500 동기화를 건너뜁니다.");
            return;
        }

        log.info("FMP S&P500 구성 종목 동기화 시작");

        Set<String> sp500Symbols = fetchSp500SymbolsFromFmp();
        if (sp500Symbols.isEmpty()) {
            log.warn("FMP S&P500 구성 종목 심볼 집합이 비어 있습니다. 동기화를 건너뜁니다.");
            return;
        }

        log.info("FMP에서 수신한 S&P500 심볼 수: {}", sp500Symbols.size());

        List<Stock> allStocks = stockRepository.findAll();
        int updatedTrue = 0;
        int updatedFalse = 0;

        for (Stock stock : allStocks) {
            String symbol = stock.getSymbol();
            if (symbol == null) {
                continue;
            }
            boolean isSp500 = sp500Symbols.contains(symbol.toUpperCase());
            if (Boolean.TRUE.equals(stock.getSp500()) != isSp500) {
                stock.setSp500(isSp500);
                if (isSp500) {
                    updatedTrue++;
                } else {
                    updatedFalse++;
                }
            }
        }

        log.info("S&P500 플래그 동기화 완료: true 업데이트={}, false 업데이트={}", updatedTrue, updatedFalse);

        // 캐시 업데이트
        cachedSp500Symbols = sp500Symbols;
    }

    // 단일 심볼에 대해 S&P500 편입 여부를 확인 (인메모리 캐시 사용)
    public boolean isSp500Symbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        // 캐시가 비어 있으면 한 번 로드
        if (cachedSp500Symbols == null || cachedSp500Symbols.isEmpty()) {
            cachedSp500Symbols = fetchSp500SymbolsFromFmp();
        }
        return cachedSp500Symbols.contains(symbol.trim().toUpperCase());
    }

    private Set<String> fetchSp500SymbolsFromFmp() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Financial Modeling Prep API 키가 설정되지 않았습니다. S&P500 심볼을 가져올 수 없습니다.");
            return new HashSet<>();
        }

        List<FmpSp500ConstituentResponse> constituents = fmpClient.getSp500Constituents(apiKey);
        if (constituents == null || constituents.isEmpty()) {
            log.warn("FMP S&P500 구성 종목 응답이 비어 있습니다.");
            return new HashSet<>();
        }

        Set<String> sp500Symbols = new HashSet<>();
        for (FmpSp500ConstituentResponse c : constituents) {
            if (c.getSymbol() != null && !c.getSymbol().isBlank()) {
                sp500Symbols.add(c.getSymbol().trim().toUpperCase());
            }
        }
        return sp500Symbols;
    }
}

