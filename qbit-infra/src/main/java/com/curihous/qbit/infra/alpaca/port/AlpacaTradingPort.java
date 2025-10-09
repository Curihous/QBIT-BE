package com.curihous.qbit.infra.alpaca.port;

import com.curihous.qbit.infra.alpaca.dto.request.CreateOrderRequest;
import com.curihous.qbit.infra.alpaca.dto.request.UpdateOrderRequest;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaAssetResponse;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaOrderResponse;

import java.util.List;

// Port-Adapter 패턴(헥사고날 아키텍처)
public interface AlpacaTradingPort {

    // 주문 생성
    AlpacaOrderResponse createOrder(String authorization, CreateOrderRequest request);

    // 주문 수정
    AlpacaOrderResponse updateOrder(String authorization, String orderId, UpdateOrderRequest request);

    // 종목 목록 조회
    List<AlpacaAssetResponse> getAssets(String authorization, String status, String assetClass);

    // 특정 종목 조회
    AlpacaAssetResponse getAsset(String authorization, String symbol);
}
