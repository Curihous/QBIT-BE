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

    private final AlpacaTradeUpdatesManager alpacaTradeUpdatesManager;
    private final WebSocketClient alpacaWebSocketClient;

    @PostConstruct
    public void init() {
        alpacaTradeUpdatesManager.initialize(alpacaWebSocketClient);
    }
}

