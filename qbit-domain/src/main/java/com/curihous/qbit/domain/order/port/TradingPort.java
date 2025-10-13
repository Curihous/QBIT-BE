package com.curihous.qbit.domain.order.port;

import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 주식 거래(주문) 처리를 위한 Port 인터페이스
 * (Hexagonal Architecture - Port)
 */
public interface TradingPort {
    
    // 주문 생성
    OrderRequest createOrder(User user, CreateOrderCommand request);
    
    // 주문 수정
    OrderUpdateResult updateOrder(User user, Long orderId, UpdateOrderCommand request);
    
    // 내 주문 목록 조회 
    Page<OrderRequest> getMyOrders(User user, Pageable pageable);
    
    // 특정 주문 조회
    OrderRequest getOrder(User user, Long orderId);
    
    // 주문 취소
    void cancelOrder(User user, Long orderId);
    
    // 포지션(보유 주식) 목록 조회 
    Page<PositionInfo> getPositions(User user, Pageable pageable);
    
    // 계정 정보 조회
    AccountInfo getAccountInfo(User user);
    
    record CreateOrderCommand(
        String symbol,           // 종목 심볼
        String assetClass,       // 자산 클래스 (us_equity, crypto)
        String quantity,         // 수량
        String side,             // 매수/매도 (buy/sell)
        String type,             // 주문 유형 (market/limit/stop/stop_limit)
        String timeInForce,      // 주문 유효기간 (day/gtc/ioc/fok)
        String limitPrice,       // 지정가 (limit/stop_limit 시)
        String stopPrice,        // 손절가 (stop/stop_limit 시)
        String clientOrderId     // 클라이언트 주문 ID (선택)
    ) {}
    
    record UpdateOrderCommand(
        String quantity,         // 수정할 수량 (선택)
        String limitPrice,       // 수정할 지정가 (선택)
        String stopPrice,        // 수정할 손절가 (선택)
        String timeInForce,      // 수정할 주문 유효기간 (선택)
        String clientOrderId     // 수정할 클라이언트 주문 ID (선택)
    ) {}
    
    record OrderUpdateResult(
        String alpacaOrderId,        // Alpaca 주문 ID
        String symbol,               // 종목 심볼
        String quantity,              // 수량
        String filledQuantity,       // 체결된 수량
        String side,                 // 매수/매도
        String type,                 // 주문 유형
        String timeInForce,          // 주문 유효기간
        String limitPrice,           // 지정가
        String stopPrice,            // 손절가
        String filledAvgPrice,       // 평균 체결가
        String status,               // 주문 상태
        String clientOrderId,        // 클라이언트 주문 ID
        String createdAt,            // 생성 시간
        String submittedAt,          // 제출 시간
        String filledAt,             // 체결 시간
        String canceledAt,           // 취소 시간
        String replacedAt,           // 대체 시간
        String replacedBy,           // 대체된 주문 ID
        String replaces              // 대체하는 주문 ID
    ) {}
    
    record PositionInfo(
        String symbol,               // 종목 심볼
        String quantity,             // 보유 수량
        String avgEntryPrice,        // 평균 매수가
        String marketValue,          // 시장 가치
        String costBasis,            // 원가 기준
        String unrealizedPl,         // 미실현 손익
        String unrealizedPlpc,       // 미실현 손익률
        String currentPrice,         // 현재 가격
        String side                  // 포지션 방향 (long/short)
    ) {}
    
    record AccountInfo(
        String accountNumber,        // 계정 번호
        String status,               // 계정 상태
        String currency,             // 통화
        String buyingPower,          // 매수 가능 금액
        String cash,                 // 현금
        String portfolioValue,       // 포트폴리오 가치
        String equity,               // 자산 가치
        String lastEquity,           // 전일 자산 가치
        String longMarketValue,      // 롱 포지션 시장 가치
        String shortMarketValue      // 숏 포지션 시장 가치
    ) {}
}

