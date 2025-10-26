package com.curihous.qbit.common.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 로그인 시 주문 동기화 이벤트
 * 
 * 사용 지점: AlpacaOrderSyncService.syncOrdersOnLogin, AlpacaTradeUpdatesManager
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginOrderSyncEvent implements Serializable {
    private Long userId;
    private String userEmail;
    private String accessToken;  // Alpaca OAuth access token
    
    public LoginOrderSyncEvent(Long userId, String userEmail) {
        this(userId, userEmail, null);
    }
}
