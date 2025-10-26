package com.curihous.qbit.common.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 로그인 시 주문 동기화 이벤트
 * 
 * 사용 지점: AlpacaOrderSyncService.syncOrdersOnLogin, AlpacaTradeUpdatesManager
 */
@Getter
@RequiredArgsConstructor
public class LoginOrderSyncEvent {
    private final Long userId;
    private final String userEmail;
    private final String accessToken;  // Alpaca OAuth access token
    
    public LoginOrderSyncEvent(Long userId, String userEmail) {
        this(userId, userEmail, null);
    }
}
