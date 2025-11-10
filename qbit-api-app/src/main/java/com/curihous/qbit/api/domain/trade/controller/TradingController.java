package com.curihous.qbit.api.domain.trade.controller;

import com.curihous.qbit.api.domain.trade.dto.request.CreateOrderRequestDto;
import com.curihous.qbit.api.domain.trade.dto.request.UpdateOrderRequestDto;
import com.curihous.qbit.api.domain.trade.dto.response.OrderCreatedResponseDto;
import com.curihous.qbit.api.domain.trade.dto.response.OrderDetailResponseDto;
import com.curihous.qbit.api.domain.trade.dto.response.OrderUpdateResponseDto;
import com.curihous.qbit.common.dto.PaginatedResponseDto;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.common.util.CryptoSymbolConverter;
import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.order.port.TradingPort;
import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.infra.alpaca.service.AlpacaStockService;
import com.curihous.qbit.api.domain.trade.service.AlpacaOrderSyncService;
import com.curihous.qbit.infra.security.facade.UserSecurityFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.curihous.qbit.common.util.PagingValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Tag(name = "Trading", description = "모의 주식 거래 관련 API입니다.")
@RestController
@RequestMapping("/trading")
@RequiredArgsConstructor
public class TradingController {

    private final TradingPort tradingPort; // 헥사고날 아키텍처
    private final AlpacaStockService alpacaStockService;
    private final UserSecurityFacade userSecurityFacade;
    private final AlpacaOrderSyncService alpacaOrderSyncService;
    
    @Value("${stock.sync.us-equity}")
    private boolean allowUsEquity;
    
    @Value("${stock.sync.crypto}")
    private boolean allowCrypto;

    @Operation(summary = "주문 생성", description = "Alpaca API를 통해 모의 주식 주문을 생성합니다.")
    @PostMapping("/orders")
    @Transactional
    public ResponseEntity<OrderCreatedResponseDto> createOrder(@Valid @RequestBody CreateOrderRequestDto request) {
        User user = userSecurityFacade.getCurrentUser();
        
        // 자산 클래스 허용 여부 체크
        Stock stock = alpacaStockService.getOrFetchStock(user, request.symbol());
        validateAssetClassAllowed(stock);
        
        // Binance 심볼 자동 변환 (DB에 없는 경우만)
        if (stock.getBinanceSymbol() == null || stock.getBinanceSymbol().isBlank()) {
            String binanceSymbol = CryptoSymbolConverter.convertToBinance(stock.getSymbol(), stock.getAssetClass());
            if (binanceSymbol != null) {
                stock.setBinanceSymbol(binanceSymbol);
                log.info("Binance 심볼 자동 변환: {} ({}) → {}", 
                        stock.getSymbol(), stock.getAssetClass(), binanceSymbol);
            }
        }
        
        // 최소 주문 금액 및 수량 검증
        validateMinimumOrderRequirements(stock, request.quantity(), request.limitPrice(), request.stopPrice());
        
        // 클라이언트 주문 ID 자동 생성
        String clientOrderId = generateClientOrderId(user.getId());
        
        TradingPort.CreateOrderCommand command = new TradingPort.CreateOrderCommand(
            request.symbol(),
            stock.getAssetClass(), 
            request.quantity(),
            request.side(),
            request.type(),
            request.timeInForce(),
            request.limitPrice(),
            request.stopPrice(),
            clientOrderId
        );
        
        TradingPort.OrderCreatedResult result = tradingPort.createOrder(user, command);
        OrderRequest orderRequest = tradingPort.getOrderByAlpacaOrderId(user, result.alpacaOrderId());
        return ResponseEntity.ok(OrderCreatedResponseDto.from(orderRequest, stock.getAssetClass()));
    }

    @Operation(summary = "주문 수정", description = "미체결 또는 부분 체결된 주문의 수량, 가격 등을 수정합니다.")
    @PatchMapping("/orders/{orderId}")
    public ResponseEntity<OrderUpdateResponseDto> updateOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderRequestDto request
    ) {
        User user = userSecurityFacade.getCurrentUser();
        
        TradingPort.UpdateOrderCommand command = new TradingPort.UpdateOrderCommand(
            request.quantity(),
            request.limitPrice(),
            request.stopPrice(),
            request.timeInForce(),
            null  // 주문 수정 시 clientOrderId는 변경하지 않음
        );
        
        TradingPort.OrderUpdateResult result = tradingPort.updateOrder(user, orderId, command);
        return ResponseEntity.ok(OrderUpdateResponseDto.from(result));
    }

    @Operation(summary = "내 주문 목록 조회", description = "사용자의 모든 주문 내역을 조회합니다. (페이징 지원)")
    @GetMapping("/orders")
    public ResponseEntity<PaginatedResponseDto<OrderDetailResponseDto>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PagingValidator.validate(page, size);

        User user = userSecurityFacade.getCurrentUser();
        Pageable pageable = PageRequest.of(page, size);
        Page<OrderRequest> ordersPage = tradingPort.getMyOrders(user, pageable);
        Page<OrderDetailResponseDto> responsePage = ordersPage.map(OrderDetailResponseDto::from);
        PaginatedResponseDto<OrderDetailResponseDto> response = PaginatedResponseDto.from(responsePage);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "주문 상세 조회", description = "특정 주문의 상세 정보를 조회합니다.")
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderDetailResponseDto> getOrder(
            @PathVariable Long orderId
    ) {
        User user = userSecurityFacade.getCurrentUser();
        OrderRequest order = tradingPort.getOrder(user, orderId);
        return ResponseEntity.ok(OrderDetailResponseDto.from(order));
    }

    @Operation(
        summary = "주문 취소", 
        description = "미체결 또는 부분 체결된 주문을 취소합니다. 이미 완전히 체결된 주문은 취소할 수 없습니다."
    )
    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long orderId) {
        User user = userSecurityFacade.getCurrentUser();
        tradingPort.cancelOrder(user, orderId);
        return ResponseEntity.noContent().build();
    }

    // tradeCycle 임시 메서드
    @Operation(
        summary = "[임시] TradeCycle 후처리 생성", 
        description = "DB의 OrderRequest를 기반으로 TradeCycle을 후처리로 생성합니다. " 
    )
    @PostMapping("/trade-cycles/backfill")
    @Transactional
    public ResponseEntity<Map<String, Object>> backfillTradeCycles() {
        User user = userSecurityFacade.getCurrentUser();
        int createdCount = alpacaOrderSyncService.backfillTradeCycles(user.getId());
        
        return ResponseEntity.ok(Map.of(
            "userId", user.getId(),
            "createdCount", createdCount,
            "message", "TradeCycle 후처리 완료"
        ));
    }
    
    // 자산 클래스 허용 여부 검증 헬퍼 메서드
    private void validateAssetClassAllowed(Stock stock) {
        String assetClass = stock.getAssetClass();
        
        if ("us_equity".equalsIgnoreCase(assetClass) && !allowUsEquity) {
            log.warn("미국 주식 거래 차단: symbol={}", stock.getSymbol());
            throw new QbitException(ErrorCode.ASSET_CLASS_NOT_SUPPORTED, "미국 주식 거래는 현재 비활성화되어 있습니다.");
        }
        
        if ("crypto".equalsIgnoreCase(assetClass) && !allowCrypto) {
            log.warn("암호화폐 거래 차단: symbol={}", stock.getSymbol());
            throw new QbitException(ErrorCode.ASSET_CLASS_NOT_SUPPORTED, "암호화폐 거래는 현재 비활성화되어 있습니다.");
        }
        
        // us_equity, crypto가 아닌 경우 기본적으로 차단
        if (!"us_equity".equalsIgnoreCase(assetClass) && !"crypto".equalsIgnoreCase(assetClass)) {
            log.warn("지원하지 않는 자산 클래스 거래 시도: symbol={}, assetClass={}", stock.getSymbol(), assetClass);
            throw new QbitException(ErrorCode.ASSET_CLASS_NOT_SUPPORTED);
        }
    }
    
    // 최소 주문 금액 및 수량 검증
    private void validateMinimumOrderRequirements(Stock stock, String quantityStr, String limitPriceStr, String stopPriceStr) {
        BigDecimal quantity = new BigDecimal(quantityStr);
        String assetClass = stock.getAssetClass();
        
        if ("crypto".equalsIgnoreCase(assetClass)) {
            validateCryptoOrderRequirements(stock, quantity, limitPriceStr, stopPriceStr);
        } else if ("us_equity".equalsIgnoreCase(assetClass)) {
            validateStockOrderRequirements(stock, quantity);
        }
    }
    
    // 암호화폐 주문 검증
    private void validateCryptoOrderRequirements(Stock stock, BigDecimal quantity, String limitPriceStr, String stopPriceStr) {
        // 1. 최소 주문 수량 검증 (minOrderSize)
        if (stock.getMinOrderSize() != null && !stock.getMinOrderSize().isBlank()) {
            BigDecimal minOrderSize = new BigDecimal(stock.getMinOrderSize());
            if (quantity.compareTo(minOrderSize) < 0) {
                log.warn("암호화폐 주문 수량이 최소 요구사항 미만: symbol={}, quantity={}, minOrderSize={}", 
                        stock.getSymbol(), quantity, minOrderSize);
                throw new QbitException(ErrorCode.INVALID_ORDER_PRICE, 
                        String.format("주문 수량이 최소 요구사항(%s) 미만입니다. 현재 주문 수량: %s", 
                                minOrderSize, quantity));
            }
        }
        
        // 2. 최소 거래 증분 검증 (minTradeIncrement)
        if (stock.getMinTradeIncrement() != null && !stock.getMinTradeIncrement().isBlank()) {
            BigDecimal minTradeIncrement = new BigDecimal(stock.getMinTradeIncrement());
            // 주문 수량이 minTradeIncrement의 배수인지 확인
            BigDecimal remainder = quantity.remainder(minTradeIncrement);
            if (remainder.compareTo(BigDecimal.ZERO) != 0) {
                log.warn("암호화폐 주문 수량이 최소 거래 증분의 배수가 아님: symbol={}, quantity={}, minTradeIncrement={}", 
                        stock.getSymbol(), quantity, minTradeIncrement);
                throw new QbitException(ErrorCode.INVALID_ORDER_PRICE, 
                        String.format("주문 수량이 최소 거래 증분(%s)의 배수가 아닙니다. 현재 주문 수량: %s", 
                                minTradeIncrement, quantity));
            }
        }
        
        // 3. 가격 증분 검증 (priceIncrement) - 지정가/손절지정가 주문에서만
        if (stock.getPriceIncrement() != null && !stock.getPriceIncrement().isBlank()) {
            BigDecimal priceIncrement = new BigDecimal(stock.getPriceIncrement());
            
            // limitPrice 검증 (limit/stop_limit 주문)
            if (limitPriceStr != null && !limitPriceStr.isBlank()) {
                BigDecimal limitPrice = new BigDecimal(limitPriceStr);
                BigDecimal priceRemainder = limitPrice.remainder(priceIncrement);
                if (priceRemainder.compareTo(BigDecimal.ZERO) != 0) {
                    log.warn("암호화폐 지정가가 가격 증분의 배수가 아님: symbol={}, limitPrice={}, priceIncrement={}", 
                            stock.getSymbol(), limitPrice, priceIncrement);
                    throw new QbitException(ErrorCode.INVALID_ORDER_PRICE, 
                            String.format("지정가가 가격 증분(%s)의 배수가 아닙니다. 현재 지정가: %s", 
                                    priceIncrement, limitPrice));
                }
            }
            
            // stopPrice 검증 (stop/stop_limit 주문)
            if (stopPriceStr != null && !stopPriceStr.isBlank()) {
                BigDecimal stopPrice = new BigDecimal(stopPriceStr);
                BigDecimal priceRemainder = stopPrice.remainder(priceIncrement);
                if (priceRemainder.compareTo(BigDecimal.ZERO) != 0) {
                    log.warn("암호화폐 손절가가 가격 증분의 배수가 아님: symbol={}, stopPrice={}, priceIncrement={}", 
                            stock.getSymbol(), stopPrice, priceIncrement);
                    throw new QbitException(ErrorCode.INVALID_ORDER_PRICE, 
                            String.format("손절가가 가격 증분(%s)의 배수가 아닙니다. 현재 손절가: %s", 
                                    priceIncrement, stopPrice));
                }
            }
        }
        
        // 수량/증분/가격 증분 검증만 수행하고, 금액 검증은 Alpaca에 맡김
        
        log.debug("암호화폐 주문 검증 통과: symbol={}, quantity={}", 
                stock.getSymbol(), quantity);
    }
    
    // 주식 주문 검증 
    private void validateStockOrderRequirements(Stock stock, BigDecimal quantity) {
        // 1. 최소 주문 수량 검증
        BigDecimal minOrderSize = BigDecimal.ONE; // 1주
        if (quantity.compareTo(minOrderSize) < 0) {
            log.warn("주식 주문 수량이 최소 요구사항 미만: symbol={}, quantity={}, minOrderSize={}", 
                    stock.getSymbol(), quantity, minOrderSize);
            throw new QbitException(ErrorCode.INVALID_ORDER_PRICE, 
                    String.format("주문 수량이 최소 요구사항(%s주) 미만입니다. 현재 주문 수량: %s주", 
                            minOrderSize, quantity));
        }
        
        // 2. 소수점 거래 가능 여부 확인
        if (!Boolean.TRUE.equals(stock.getFractionable())) {
            // 소수점 거래 불가능한 경우 정수 단위로만 거래 가능
            if (quantity.stripTrailingZeros().scale() > 0) {
                log.warn("주식이 소수점 거래를 지원하지 않음: symbol={}, quantity={}", 
                        stock.getSymbol(), quantity);
                throw new QbitException(ErrorCode.INVALID_ORDER_PRICE, 
                        String.format("이 종목은 소수점 거래를 지원하지 않습니다. 주문 수량은 정수 주 단위여야 합니다. 현재 주문 수량: %s", 
                                quantity));
            }
        }
        
        log.debug("주식 주문 검증 통과: symbol={}, quantity={}", 
                stock.getSymbol(), quantity);
    }
    
    // 클라이언트 주문 ID 자동 생성
    private String generateClientOrderId(Long userId) {
        // 형식: qbit-{userId}-{timestamp}-{random}
        // 예시: qbit-123-1697123456789-a1b2c3d4
        String timestamp = String.valueOf(System.currentTimeMillis());
        String randomPart = UUID.randomUUID().toString().substring(0, 8);
        return String.format("qbit-%d-%s-%s", userId, timestamp, randomPart);
    }
}
