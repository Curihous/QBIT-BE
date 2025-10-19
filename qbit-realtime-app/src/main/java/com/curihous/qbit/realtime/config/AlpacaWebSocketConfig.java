package com.curihous.qbit.realtime.config;

import com.curihous.qbit.realtime.handler.TradeUpdatesEventHandler;
import com.curihous.qbit.realtime.websocket.AlpacaTradeUpdatesManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Configuration
public class AlpacaWebSocketConfig {

    @Bean
    public WebSocketClient alpacaWebSocketClient() {
        return new StandardWebSocketClient();
    }

    @Bean
    public AlpacaTradeUpdatesManager alpacaTradeUpdatesManager(
            WebSocketClient alpacaWebSocketClient,
            TradeUpdatesEventHandler eventHandler) {
        AlpacaTradeUpdatesManager manager = new AlpacaTradeUpdatesManager(eventHandler);
        manager.initialize(alpacaWebSocketClient);
        return manager;
    }
}

