package com.curihous.qbit.api.domain.stock.controller;

import com.curihous.qbit.api.domain.stock.dto.StockDetailResponseDto;
import com.curihous.qbit.api.domain.stock.dto.StockSearchResponseDto;
import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.stock.service.StockService;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.infra.alpaca.service.AlpacaStockService;
import com.curihous.qbit.infra.security.facade.UserSecurityFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Stock", description = "주식 종목 관련 API입니다")
@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final AlpacaStockService alpacaStockService;
    private final UserSecurityFacade userSecurityFacade;

    @Operation(
        summary = "미국 주식 목록 조회",
        description = "DB에 저장된 인기 종목 목록을 조회합니다 (매월 1일 동기화)"
    )
    @GetMapping
    public ResponseEntity<List<StockSearchResponseDto>> searchStocks() { // TODO: 페이징 처리
        List<Stock> stocks = stockService.getAllStocks();
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
