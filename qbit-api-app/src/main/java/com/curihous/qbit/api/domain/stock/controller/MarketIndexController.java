package com.curihous.qbit.api.domain.stock.controller;

import com.curihous.qbit.api.domain.stock.dto.response.MarketIndexHistoryDto;
import com.curihous.qbit.api.domain.stock.dto.response.MarketIndexResponseDto;
import com.curihous.qbit.api.domain.stock.dto.response.MarketIndexSummaryDto;
import com.curihous.qbit.domain.stock.entity.MarketIndex;
import com.curihous.qbit.domain.stock.port.MarketIndexPort;
import com.curihous.qbit.domain.stock.service.MarketIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/indices")
@RequiredArgsConstructor
@Tag(name = "Stock - Index", description = "해외 주요 지수 API입니다.")
public class MarketIndexController {

    private final MarketIndexService marketIndexService;
    private final MarketIndexPort marketIndexPort;

    @GetMapping
    @Operation(summary = "주요 지수 목록 조회", description = "해외 주요 지수 목록을 조회합니다. (홈 화면용)")
    public ResponseEntity<List<MarketIndexSummaryDto>> getMajorIndices() {
        List<MarketIndex> indices = marketIndexService.getMajorIndices();
        List<MarketIndexSummaryDto> response = indices.stream()
                .map(MarketIndexSummaryDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }


    @GetMapping("/{symbol}")
    @Operation(summary = "특정 지수 상세 조회", description = "심볼로 특정 지수의 상세 정보를 조회합니다.")
    public ResponseEntity<MarketIndexResponseDto> getIndexBySymbol(
            @PathVariable String symbol) {
        
        // 허용된 지수 심볼만 조회 가능
        marketIndexService.validateIndexSymbol(symbol);
        
        MarketIndex index = marketIndexPort.getOrFetchIndex(symbol);
        MarketIndexResponseDto response = MarketIndexResponseDto.fromEntity(index);
        
        log.info("지수 조회: {} - {}", symbol, index.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{symbol}/history")
    @Operation(summary = "지수 과거 데이터 조회", description = "특정 지수의 과거 데이터를 조회합니다. (차트용)")
    public ResponseEntity<List<MarketIndexHistoryDto>> getIndexHistory(
            @Parameter(description = "지수 심볼", example = "^GSPC")
            @PathVariable String symbol,
            @Parameter(description = "시작일 (yyyy-MM-dd)", example = "2024-01-01")
            @RequestParam String from,
            @Parameter(description = "종료일 (yyyy-MM-dd)", example = "2024-12-31")
            @RequestParam String to) {

        // 허용된 지수 심볼만 조회 가능
        marketIndexService.validateIndexSymbol(symbol);

        MarketIndexPort.MarketIndexHistoryData data = marketIndexPort.getIndexHistory(symbol, from, to);
        
        List<MarketIndexHistoryDto> response = data.dataPoints().stream()
            .map(point -> new MarketIndexHistoryDto(
                point.timestamp(),
                point.openPrice(),
                point.closePrice(),
                point.highPrice(),
                point.lowPrice(),
                point.volume()
            ))
            .collect(Collectors.toList());

        log.info("지수 과거 데이터 조회: {} ({} ~ {}), {}건", symbol, from, to, response.size());
        return ResponseEntity.ok(response);
    }
}
