package com.curihous.qbit.infra.alpaca.client;

import com.curihous.qbit.infra.alpaca.config.AlpacaClientConfig;
import com.curihous.qbit.infra.alpaca.dto.request.CreateOrderRequest;
import com.curihous.qbit.infra.alpaca.dto.request.UpdateOrderRequest;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaAccountResponse;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaAssetResponse;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaOrderResponse;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaPositionResponse;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaPortfolioHistoryResponse;
import com.curihous.qbit.infra.alpaca.port.AlpacaTradingPort;
import feign.QueryMap;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Alpaca Trading API 클라이언트
@FeignClient(
    name = "alpaca-trading-client",
    url = "https://paper-api.alpaca.markets",
    configuration = AlpacaClientConfig.class
)
public interface AlpacaTradingClient extends AlpacaTradingPort {

    // ============== Account API ==============

    // 계정 정보 조회
    @GetMapping("/v2/account")
    AlpacaAccountResponse getAccount(@RequestHeader("Authorization") String authorization);

    // ============== Orders API ==============

    // 주문 생성
    @PostMapping("/v2/orders")
    AlpacaOrderResponse createOrder(
        @RequestHeader("Authorization") String authorization,
        @RequestBody CreateOrderRequest request
    );

    // 주문 목록 조회
    @GetMapping("/v2/orders")
    List<AlpacaOrderResponse> getOrders(
        @RequestHeader("Authorization") String authorization,
        @QueryMap(encoded = true) Map<String, Object> queryParams
    );

    // 특정 주문 조회
    @GetMapping("/v2/orders/{order_id}")
    AlpacaOrderResponse getOrder(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("order_id") String orderId
    );

    // 주문 수정
    @PatchMapping("/v2/orders/{order_id}")
    AlpacaOrderResponse updateOrder(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("order_id") String orderId,
        @RequestBody UpdateOrderRequest request
    );

    // 주문 취소
    @DeleteMapping("/v2/orders/{order_id}")
    void cancelOrder(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("order_id") String orderId
    );

    // ============== Positions API ==============

    // 보유 포지션 조회
    @GetMapping("/v2/positions")
    List<AlpacaPositionResponse> getPositions(@RequestHeader("Authorization") String authorization);

    // 특정 종목 포지션 조회
    @GetMapping("/v2/positions/{symbol}")
    AlpacaPositionResponse getPosition(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("symbol") String symbol
    );

    // 포지션 청산
    @DeleteMapping("/v2/positions/{symbol}")
    AlpacaOrderResponse closePosition(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("symbol") String symbol
    );

    // ============== Assets API ==============

    // 종목 목록 조회
    @GetMapping("/v2/assets")
    List<AlpacaAssetResponse> getAssets(
        @RequestHeader("Authorization") String authorization,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "asset_class", required = false) String assetClass
    );

    // 특정 종목 조회
    @GetMapping("/v2/assets/{symbol}")
    AlpacaAssetResponse getAsset(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("symbol") String symbol
    );

    // ============== Portfolio History API ==============

    @GetMapping("/v2/account/portfolio/history")
    AlpacaPortfolioHistoryResponse getPortfolioHistory(
        @RequestHeader("Authorization") String authorization,
        @RequestParam(value = "period", required = false) String period,
        @RequestParam(value = "timeframe", required = false) String timeframe
    );
}

