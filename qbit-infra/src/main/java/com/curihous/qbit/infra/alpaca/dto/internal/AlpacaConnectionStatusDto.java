package com.curihous.qbit.infra.alpaca.dto.internal;

/**
 * Alpaca 연결 상태 DTO
 * 
 * 사용 API:
 * - GET /auth/alpaca/status
 */
public record AlpacaConnectionStatusDto(
    boolean connected,
    boolean paperTrading,
    String connectionStatus,
    boolean tokenExpired
) {
}
