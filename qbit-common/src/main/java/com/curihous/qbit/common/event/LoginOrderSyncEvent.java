package com.curihous.qbit.common.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 로그인 시 주문 동기화 이벤트
 * 
 * 사용 지점: AlpacaOrderSyncService.syncOrdersOnLogin
 */
@Getter
@RequiredArgsConstructor
public class LoginOrderSyncEvent {
    private final Long userId;
    private final String userEmail;
}
