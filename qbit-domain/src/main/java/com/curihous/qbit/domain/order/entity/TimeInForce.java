package com.curihous.qbit.domain.order.entity;

// 주문 유효기간
public enum TimeInForce {
    DAY,  // 당일: 장 마감까지 유효
    GTC,  // Good Till Canceled: 취소할 때까지 유효
    IOC,  // Immediate Or Cancel: 즉시 체결 가능한 수량만 체결, 나머지 취소
    FOK   // Fill Or Kill: 전량 즉시 체결 가능하면 체결, 아니면 전체 취소
}

