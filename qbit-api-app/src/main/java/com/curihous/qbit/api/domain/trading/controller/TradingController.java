package com.curihous.qbit.api.domain.trading.controller;

import com.curihous.qbit.api.domain.trading.dto.request.CreateOrderRequestDto;
import com.curihous.qbit.api.domain.trading.dto.request.UpdateOrderRequestDto;
import com.curihous.qbit.api.domain.trading.dto.response.OrderCreatedResponseDto;
import com.curihous.qbit.api.domain.trading.dto.response.OrderDetailResponseDto;
import com.curihous.qbit.api.domain.trading.dto.response.OrderUpdateResponseDto;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "Trading", description = "모의 주식 거래 관련 API입니다.")
@RestController
@RequestMapping("/trading")
@RequiredArgsConstructor
public class TradingController {

    private final TradingPort tradingPort;
    private final StockPort stockPort;
    private final UserSecurityFacade userSecurityFacade;
    
    @Value("${stock.sync.us-equity}")
    private boolean allowUsEquity;
    
    @Value("${stock.sync.crypto}")
    private boolean allowCrypto;

    @Operation(summary = "주문 생성", description = "Alpaca API를 통해 모의 주식 주문을 생성합니다.")
    @PostMapping("/orders")
    public ResponseEntity<OrderCreatedResponseDto> createOrder(@Valid @RequestBody CreateOrderRequestDto request) {
        User user = userSecurityFacade.getCurrentUser();
        
        // 자산 클래스 허용 여부 체크
        Stock stock = stockPort.getOrFetchStock(user, request.symbol());
        validateAssetClassAllowed(stock);
        
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
        
        OrderRequest orderRequest = tradingPort.createOrder(user, command);
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

    @Operation(summary = "내 주문 목록 조회", description = "사용자의 모든 주문 내역을 조회합니다.")
    @GetMapping("/orders")
    public ResponseEntity<List<OrderDetailResponseDto>> getMyOrders() {
        User user = userSecurityFacade.getCurrentUser();
        List<OrderRequest> orders = tradingPort.getMyOrders(user); // TODO: 페이징 처리
        List<OrderDetailResponseDto> response = orders.stream()
                .map(OrderDetailResponseDto::from)
                .collect(Collectors.toList());
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
