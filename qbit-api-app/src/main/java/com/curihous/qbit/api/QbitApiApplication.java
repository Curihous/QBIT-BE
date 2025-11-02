package com.curihous.qbit.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.curihous.qbit")
@EnableJpaRepositories(basePackages = {"com.curihous.qbit.domain"})
@EntityScan(basePackages = {"com.curihous.qbit.domain"})
@EnableJpaAuditing
@EnableScheduling
@EnableAsync
@EnableFeignClients(basePackages = {
        "com.curihous.qbit.infra.alpaca.client",
        "com.curihous.qbit.infra.yahoo.client",
        "com.curihous.qbit.infra.binance.client",
        "com.curihous.qbit.infra.massive.client"
})
public class QbitApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(com.curihous.qbit.api.QbitApiApplication.class, args);
    }

}
