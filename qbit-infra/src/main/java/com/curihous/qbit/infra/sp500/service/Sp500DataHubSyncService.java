package com.curihous.qbit.infra.sp500.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Slf4j
@Service
@RequiredArgsConstructor
public class Sp500DataHubSyncService {

    private final StockRepository stockRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 인메모리 캐시 (동일 인스턴스 내에서 재사용)
    private volatile Set<String> cachedSp500Symbols = new HashSet<>();

    @Value("${sp500.json-url}")
    private String sp500JsonUrl;

    // S&P500 플래그 동기화
    @Transactional
    public void syncSp500Flags() {
        Set<String> sp500Symbols = fetchSp500SymbolsFromJson();
        if (sp500Symbols.isEmpty()) {
            log.warn("DataHub S&P500 심볼 집합이 비어 있습니다. sp500 동기화를 건너뜁니다.");
            return;
        }

        log.info("S&P500 JSON에서 수신한 심볼 수: {}", sp500Symbols.size());

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

        log.info("S&P500 플래그 동기화 완료 (JSON): true 업데이트={}, false 업데이트={}",
                updatedTrue, updatedFalse);

        // 캐시 업데이트
        cachedSp500Symbols = sp500Symbols;
    }

    // S&P500 JSON에서 심볼 집합 조회
    public Set<String> fetchSp500SymbolsFromJson() {
        try {
            log.info("S&P500 JSON 조회 시작: url={}", sp500JsonUrl);
            byte[] bytes = restTemplate.getForObject(sp500JsonUrl, byte[].class);
            if (bytes == null || bytes.length == 0) {
                log.warn("S&P500 JSON 응답이 비어 있습니다.");
                return new HashSet<>();
            }

            String json = new String(bytes, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                log.warn("S&P500 JSON 형식이 배열이 아닙니다.");
                return new HashSet<>();
            }

            Set<String> symbols = new HashSet<>();
            for (JsonNode node : root) {
                JsonNode symbolNode = node.get("Symbol");
                if (symbolNode != null && !symbolNode.asText().isBlank()) {
                    symbols.add(symbolNode.asText().trim().toUpperCase());
                }
            }
            return symbols;
        } catch (Exception e) {
            log.warn("S&P500 JSON 조회/파싱 실패: {}", e.getMessage(), e);
            return new HashSet<>();
        }
    }

    // 단일 심볼에 대해 S&P500 편입 여부 확인
    public boolean isSp500Symbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        // 캐시가 비어 있으면 한 번 로드
        if (cachedSp500Symbols == null || cachedSp500Symbols.isEmpty()) {
            cachedSp500Symbols = fetchSp500SymbolsFromJson();
        }
        return cachedSp500Symbols.contains(symbol.trim().toUpperCase());
    }
}


