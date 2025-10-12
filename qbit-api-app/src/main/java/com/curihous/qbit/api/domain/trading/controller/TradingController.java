package com.curihous.qbit.api.domain.trading.controller;

import com.curihous.qbit.api.domain.trading.dto.request.CreateOrderRequestDto;
import com.curihous.qbit.api.domain.trading.dto.request.UpdateOrderRequestDto;
import com.curihous.qbit.api.domain.trading.dto.response.*;
import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.order.port.TradingPort;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.infra.security.facade.UserSecurityFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Trading", description = "모의 주식 거래 관련 API입니다.")
@RestController
@RequestMapping("/trading")
@RequiredArgsConstructor
public class TradingController {

    private final TradingPort tradingPort;
    private final UserSecurityFacade userSecurityFacade;

    @Operation(summary = "주문 생성", description = "모의 주식 주문을 생성합니다")
    @PostMapping("/orders")
    public ResponseEntity<OrderCreatedResponseDto> createOrder(@Valid @RequestBody CreateOrderRequestDto request) {
        User user = userSecurityFacade.getCurrentUser();
        
        TradingPort.CreateOrderCommand command = new TradingPort.CreateOrderCommand(
            request.symbol(),
            request.quantity(),
            request.side(),
            request.type(),
            request.timeInForce(),
            request.limitPrice(),
            request.stopPrice(),
            request.clientOrderId()
        );
        
        OrderRequest orderRequest = tradingPort.createOrder(user, command);
        return ResponseEntity.ok(OrderCreatedResponseDto.from(orderRequest));
    }

    @Operation(summary = "주문 수정", description = "미체결 또는 부분 체결된 주문의 수량, 가격 등을 수정합니다")
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
            request.clientOrderId()
        );
        
        TradingPort.OrderUpdateResult result = tradingPort.updateOrder(user, orderId, command);
        return ResponseEntity.ok(OrderUpdateResponseDto.from(result));
    }

    @Operation(summary = "내 주문 목록 조회", description = "사용자의 모든 주문 내역을 조회합니다")
    @GetMapping("/orders")
    public ResponseEntity<List<OrderDetailResponseDto>> getMyOrders() {
        User user = userSecurityFacade.getCurrentUser();
        List<OrderRequest> orders = tradingPort.getMyOrders(user); // TODO: 페이징 처리
        List<OrderDetailResponseDto> response = orders.stream()
                .map(OrderDetailResponseDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "주문 상세 조회", description = "특정 주문의 상세 정보를 조회합니다")
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
        return ResponseEntity.ok().build();
    }

}
