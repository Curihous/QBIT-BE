package com.curihous.qbit.alpaca.client;

import com.curihous.qbit.alpaca.config.AlpacaClientConfig;
import com.curihous.qbit.alpaca.dto.response.AlpacaAccountResponse;
import com.curihous.qbit.alpaca.dto.response.AlpacaOrderResponse;
import com.curihous.qbit.alpaca.dto.response.AlpacaPositionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(
    name = "alpaca-trading-client",
    url = "https://paper-api.alpaca.markets",
    configuration = AlpacaClientConfig.class
)
public interface AlpacaTradingClient {

    /**
     * 계정 정보 조회
     */
    @GetMapping("/v2/account")
    AlpacaAccountResponse getAccount(@RequestHeader("Authorization") String authorization);

    /**
     * 주문 생성
     */
    @PostMapping("/v2/orders")
    AlpacaOrderResponse createOrder(
        @RequestHeader("Authorization") String authorization,
        @RequestBody com.curihous.qbit.alpaca.dto.request.CreateOrderRequest request
    );

    /**
     * 주문 목록 조회
     */
    @GetMapping("/v2/orders")
    List<AlpacaOrderResponse> getOrders(
        @RequestHeader("Authorization") String authorization,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "limit", required = false) Integer limit,
        @RequestParam(value = "direction", required = false) String direction,
        @RequestParam(value = "nested", required = false) Boolean nested
    );

    /**
     * 특정 주문 조회
     */
    @GetMapping("/v2/orders/{order_id}")
    AlpacaOrderResponse getOrder(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("order_id") String orderId
    );

    /**
     * 주문 수정
     */
    @PatchMapping("/v2/orders/{order_id}")
    AlpacaOrderResponse updateOrder(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("order_id") String orderId,
        @RequestBody com.curihous.qbit.alpaca.dto.request.UpdateOrderRequest request
    );

    /**
     * 주문 취소
     */
    @DeleteMapping("/v2/orders/{order_id}")
    void cancelOrder(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("order_id") String orderId
    );

    /**
     * 보유 포지션 조회
     */
    @GetMapping("/v2/positions")
    List<AlpacaPositionResponse> getPositions(@RequestHeader("Authorization") String authorization);

    /**
     * 특정 종목 포지션 조회
     */
    @GetMapping("/v2/positions/{symbol}")
    AlpacaPositionResponse getPosition(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("symbol") String symbol
    );

    /**
     * 포지션 청산
     */
    @DeleteMapping("/v2/positions/{symbol}")
    AlpacaOrderResponse closePosition(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("symbol") String symbol
    );
}
