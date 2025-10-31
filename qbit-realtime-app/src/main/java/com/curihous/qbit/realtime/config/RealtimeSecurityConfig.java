package com.curihous.qbit.realtime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security 설정
 * 
 * WebSocket 엔드포인트(/ws/**) 허용
 * CSRF 비활성화
 */
@Configuration
@EnableWebSecurity
public class RealtimeSecurityConfig {

    @Bean
    public SecurityFilterChain realtimeFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/ws/**").permitAll()
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf.disable());
        
        return http.build();
    }
}
