package com.curihous.qbit.domain.order.entity;

// 주문 타입
public enum OrderType {
    MARKET,       // 시장가: 현재 시장 가격으로 즉시 체결
    LIMIT,        // 지정가: 지정한 가격 이하/이상에서만 체결
    STOP,         // 손절: 지정 가격 도달 시 시장가로 체결
    STOP_LIMIT    // 손절 지정가: 지정 가격 도달 시 지정가로 체결
}
