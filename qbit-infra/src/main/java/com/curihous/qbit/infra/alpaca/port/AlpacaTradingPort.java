package com.curihous.qbit.infra.alpaca.port;

import com.curihous.qbit.infra.alpaca.dto.request.CreateOrderRequest;
import com.curihous.qbit.infra.alpaca.dto.request.UpdateOrderRequest;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaAccountResponse;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaAssetResponse;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaOrderResponse;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaPositionResponse;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaPortfolioHistoryResponse;
import java.util.Map;
import java.util.List;

// Port-Adapter 패턴(헥사고날 아키텍처)
public interface AlpacaTradingPort {

    // 주문 생성
    AlpacaOrderResponse createOrder(String authorization, CreateOrderRequest request);

    // 주문 수정
    AlpacaOrderResponse updateOrder(String authorization, String orderId, UpdateOrderRequest request);
    
    // 주문 취소
    void cancelOrder(String authorization, String orderId);

    // 종목 목록 조회
    List<AlpacaAssetResponse> getAssets(String authorization, String status, String assetClass);

    // 특정 종목 조회
    AlpacaAssetResponse getAsset(String authorization, String symbol);
    
    // 계정 정보 조회
    AlpacaAccountResponse getAccount(String authorization);
    
    // 주문 목록 조회
    List<AlpacaOrderResponse> getOrders(String authorization, Map<String, String> queryParams);

    // 특정 주문 조회
    AlpacaOrderResponse getOrder(String authorization, String orderId);
    
    // 포지션 목록 조회
    List<AlpacaPositionResponse> getPositions(String authorization);
    
    // 특정 종목 포지션 조회
    AlpacaPositionResponse getPosition(String authorization, String symbol);

    // 포트폴리오 히스토리 조회
    AlpacaPortfolioHistoryResponse getPortfolioHistory(String authorization, String period, String timeframe);
}
