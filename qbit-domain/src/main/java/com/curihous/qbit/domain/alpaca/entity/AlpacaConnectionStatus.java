package com.curihous.qbit.domain.alpaca.entity;

public enum AlpacaConnectionStatus {
    ACTIVE,       // 활성 상태
    DISCONNECTED, // 연결 해제
    EXPIRED,      // 만료됨
    ERROR         // 오류
}
