package com.curihous.qbit.api.domain.trading.controller;

import com.curihous.qbit.api.domain.trading.dto.request.CreateOrderRequestDto;
import com.curihous.qbit.api.domain.trading.dto.request.UpdateOrderRequestDto;
import com.curihous.qbit.api.domain.trading.dto.response.OrderCreatedResponseDto;
import com.curihous.qbit.api.domain.trading.dto.response.OrderDetailResponseDto;
import com.curihous.qbit.api.domain.trading.dto.response.OrderUpdateResponseDto;
import com.curihous.qbit.infra.alpaca.client.AlpacaTradingClient;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaOrderResponse;
import com.curihous.qbit.domain.alpaca.service.AlpacaOAuthConnectionService;
import com.curihous.qbit.domain.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.domain.alpaca.entity.AlpacaConnectionStatus;
import com.curihous.qbit.common.dto.PaginatedResponseDto;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.common.util.CryptoSymbolConverter;
import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.order.port.TradingPort;
import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.stock.port.StockPort;
import com.curihous.qbit.domain.user.entity.User;
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
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Tag(name = "Trading", description = "모의 주식 거래 관련 API입니다.")
@RestController
@RequestMapping("/trading")
@RequiredArgsConstructor
public class TradingController {

    private final TradingPort tradingPort;
    private final StockPort stockPort;
    private final UserSecurityFacade userSecurityFacade;
    private final AlpacaTradingClient alpacaTradingClient;
    private final AlpacaOAuthConnectionService alpacaOAuthConnectionService;
    
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
        Stock stock = stockPort.getOrFetchStock(user, request.symbol());
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

    @Operation(
        summary = "[DEBUG] Alpaca로 직접 내 주문 목록 조회", 
        description = "Alpaca API를 직접 호출하여 주문 목록을 조회합니다."
    )
    @GetMapping("/debug/orders")
    public ResponseEntity<List<AlpacaOrderResponse>> debugAlpacaOrders(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit,
            @RequestParam(value = "direction", required = false, defaultValue = "desc") String direction,
            @RequestParam(value = "nested", required = false, defaultValue = "true") Boolean nested
    ) {
        User user = userSecurityFacade.getCurrentUser();
        
        log.info("Alpaca 직접 조회 디버깅 시작: userId={}, status={}, limit={}, direction={}, nested={}", 
                user.getId(), status, limit, direction, nested);
        
        // Alpaca 연결 확인
        Optional<AlpacaOAuthConnection> connectionOpt = alpacaOAuthConnectionService.findByUserId(user.getId());
        if (connectionOpt.isEmpty()) {
            log.warn("Alpaca 연결이 없는 사용자: userId={}", user.getId());
            throw new QbitException(ErrorCode.ALPACA_NOT_CONNECTED);
        }
        
        AlpacaOAuthConnection connection = connectionOpt.get();
        if (!connection.getAlpacaConnectionStatus().equals(AlpacaConnectionStatus.ACTIVE)) {
            log.warn("비활성화된 Alpaca 연결: userId={}, status={}", user.getId(), connection.getAlpacaConnectionStatus());
            throw new QbitException(ErrorCode.ALPACA_TOKEN_EXPIRED);
        }
        
        // Alpaca API 직접 호출
        String authorization = "Bearer " + connection.getAccessToken();
        log.info("Alpaca API 직접 호출: userId={}, alpacaUserId={}, token={}...", 
                user.getId(), connection.getAlpacaUserId(), 
                connection.getAccessToken().substring(0, Math.min(20, connection.getAccessToken().length())));
        
        List<AlpacaOrderResponse> alpacaOrders = alpacaTradingClient.getOrders(
                authorization, status, limit, direction, nested);
        
        log.info("Alpaca 직접 조회 완료: userId={}, 주문 수={}", user.getId(), alpacaOrders.size());
        
        // 각 주문의 상세 정보 로그
        for (AlpacaOrderResponse order : alpacaOrders) {
            log.info("조회된 주문: id={}, symbol={}, status={}, side={}, qty={}, filled_qty={}, created_at={}", 
                    order.id(), order.symbol(), order.status(), order.side(), 
                    order.quantity(), order.filledQuantity(), order.createdAt());
        }
        
        return ResponseEntity.ok(alpacaOrders);
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
    
    // 클라이언트 주문 ID 자동 생성
    private String generateClientOrderId(Long userId) {
        // 형식: qbit-{userId}-{timestamp}-{random}
        // 예시: qbit-123-1697123456789-a1b2c3d4
        String timestamp = String.valueOf(System.currentTimeMillis());
        String randomPart = UUID.randomUUID().toString().substring(0, 8);
        return String.format("qbit-%d-%s-%s", userId, timestamp, randomPart);
    }
}
