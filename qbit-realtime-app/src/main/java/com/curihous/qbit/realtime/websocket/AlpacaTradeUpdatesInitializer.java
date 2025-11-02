package com.curihous.qbit.realtime.websocket;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.WebSocketClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlpacaTradeUpdatesInitializer {

    private final AlpacaTradeUpdatesManager manager;
    private final WebSocketClient alpacaWebSocketClient;

    @PostConstruct
    public void init() {
        manager.initialize(alpacaWebSocketClient);
        log.info("AlpacaTradeUpdatesManager 초기화 완료 (StandardWebSocketClient 사용)");
    }
}

