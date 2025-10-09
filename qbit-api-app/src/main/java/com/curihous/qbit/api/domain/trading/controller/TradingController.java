package com.curihous.qbit.api.domain.trading.controller;

import com.curihous.qbit.api.domain.trading.dto.OrderCreatedResponseDto;
import com.curihous.qbit.api.domain.trading.dto.OrderDetailResponseDto;
import com.curihous.qbit.infra.alpaca.dto.request.CreateOrderRequest;
import com.curihous.qbit.infra.alpaca.dto.request.UpdateOrderRequest;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaOrderResponse;
import com.curihous.qbit.infra.alpaca.service.AlpacaOrderRequestService;
import com.curihous.qbit.domain.order.entity.OrderRequest;
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

@Tag(name = "Trading", description = "모의 주식 거래 관련 API입니다")
@RestController
@RequestMapping("/trading")
@RequiredArgsConstructor
public class TradingController {

    private final AlpacaOrderRequestService alpacaOrderRequestService;
    private final UserSecurityFacade userSecurityFacade;

    @Operation(summary = "주문 생성", description = "Alpaca를 통해 모의 주식 주문을 생성합니다")
    @PostMapping("/orders")
    public ResponseEntity<OrderCreatedResponseDto> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        User user = userSecurityFacade.getCurrentUser();
        OrderRequest orderRequest = alpacaOrderRequestService.createOrder(user, request);
        return ResponseEntity.ok(OrderCreatedResponseDto.from(orderRequest));
    }

    @Operation(summary = "주문 수정", description = "미체결 또는 부분 체결된 주문의 수량, 가격 등을 수정합니다")
    @PatchMapping("/orders/{orderId}")
    public ResponseEntity<AlpacaOrderResponse> updateOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderRequest request
    ) {
        User user = userSecurityFacade.getCurrentUser();
        AlpacaOrderResponse response = alpacaOrderRequestService.updateOrder(user, orderId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "내 주문 목록 조회", description = "사용자의 모든 주문 내역을 조회합니다")
    @GetMapping("/orders")
    public ResponseEntity<List<OrderDetailResponseDto>> getMyOrders() {
        User user = userSecurityFacade.getCurrentUser();
        List<OrderRequest> orders = alpacaOrderRequestService.getMyOrders(user); // TODO: 페이징 처리
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
        OrderRequest order = alpacaOrderRequestService.getOrder(user, orderId);
        return ResponseEntity.ok(OrderDetailResponseDto.from(order));
    }
}
