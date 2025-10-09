package com.curihous.qbit.domain.order.entity;

// 주문 상태 (Alpaca API 기준)
public enum OrderStatus {
    NEW,              // 새로운 주문
    PENDING_NEW,      // 주문 생성 대기 중
    ACCEPTED,         // 주문 접수됨
    PARTIALLY_FILLED, // 부분 체결
    FILLED,           // 전량 체결 완료
    DONE_FOR_DAY,     // 당일 거래 종료
    CANCELED,         // 취소됨
    EXPIRED,          // 만료됨
    REPLACED,         // 다른 주문으로 대체됨
    PENDING_CANCEL,   // 취소 대기 중
    PENDING_REPLACE,  // 대체 대기 중
    REJECTED,         // 거부됨
    SUSPENDED,        // 일시 중지됨
    CALCULATED        // 계산됨 (옵션 거래용)
}

