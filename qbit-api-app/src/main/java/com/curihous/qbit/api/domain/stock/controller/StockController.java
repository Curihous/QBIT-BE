package com.curihous.qbit.api.domain.stock.controller;

import com.curihous.qbit.api.domain.stock.dto.StockDetailResponseDto;
import com.curihous.qbit.api.domain.stock.dto.StockSearchResponseDto;
import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.stock.service.StockService;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.infra.alpaca.service.AlpacaStockService;
import com.curihous.qbit.infra.security.facade.UserSecurityFacade;
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
@Tag(name = "Stock", description = "주식 종목 관련 API입니다")
@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final AlpacaStockService alpacaStockService;
    private final UserSecurityFacade userSecurityFacade;

    @Operation(
        summary = "미국 주식 검색",
        description = "종목명(name) 또는 심볼(symbol)로 검색합니다. DB에 없는 심볼은 Alpaca API에서 자동 조회 후 저장됩니다 (미국 주식만)."
    )
    @GetMapping("/search")
    public ResponseEntity<List<StockSearchResponseDto>> searchStocks(
            @Parameter(description = "검색어 (종목명 또는 심볼)", example = "Apple")
            @RequestParam(value = "q", required = false) String keyword
    ) { // TODO: 페이징 처리
        // 1. DB에서 먼저 검색 (name 또는 symbol)
        List<Stock> stocks = stockService.searchStocks(keyword);
        
        // 2. DB에 없고, 검색어가 심볼 형식이면 (대문자 1-5자) Alpaca API 조회
        if (keyword != null && !keyword.isBlank() && 
            stocks.isEmpty() && keyword.matches("^[A-Z]{1,5}$")) {
            try {
                User user = userSecurityFacade.getCurrentUser();
                Stock stock = alpacaStockService.getOrFetchStock(user, keyword);
                
                // 미국 주식만 허용 (코인 등 제외)
                if ("us_equity".equalsIgnoreCase(stock.getAssetClass())) {
                    stocks = List.of(stock);
                } else {
                    log.info("미국 주식 아님, 검색 결과 제외: symbol={}, class={}", 
                            keyword, stock.getAssetClass());
                }
            } catch (Exception e) {
                // Alpaca 조회 실패 시 빈 결과 반환 (프론트에서 예외 처리)
                log.debug("Alpaca API 조회 실패: {}", keyword);
            }
        }
        
        List<StockSearchResponseDto> response = stocks.stream()
                .map(StockSearchResponseDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "특정 종목 상세 조회",
        description = "종목의 상세 정보를 조회합니다. DB에 없으면 Alpaca API에서 조회 후 자동 저장됩니다."
    )
    @GetMapping("/{symbol}")
    public ResponseEntity<StockDetailResponseDto> getStock(
            @PathVariable String symbol
    ) {
        User user = userSecurityFacade.getCurrentUser();
        // Cache-Aside: DB 우선 → 없으면 Alpaca API 조회 → 저장
        Stock stock = alpacaStockService.getOrFetchStock(user, symbol);
        return ResponseEntity.ok(StockDetailResponseDto.fromEntity(stock));
    }
}
