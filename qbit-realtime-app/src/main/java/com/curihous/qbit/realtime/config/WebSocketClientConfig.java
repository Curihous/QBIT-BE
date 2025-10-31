package com.curihous.qbit.realtime.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Slf4j
@Configuration
public class WebSocketClientConfig {

    @Bean
    public WebSocketClient alpacaWebSocketClient() {
        return new StandardWebSocketClient();
    }
}

