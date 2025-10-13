package com.curihous.qbit.infra.binance.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.client.WebSocketClient;

@Slf4j
@Configuration
public class BinanceWebSocketConfig {

    @Bean
    public WebSocketClient binanceWebSocketClient() {
        return new StandardWebSocketClient();
    }
}
