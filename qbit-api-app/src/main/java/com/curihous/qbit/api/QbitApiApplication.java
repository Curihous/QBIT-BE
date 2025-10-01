package com.curihous.qbit.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.curihous.qbit")
@EnableJpaRepositories(basePackages = "com.curihous.qbit.domain")
@EntityScan(basePackages = "com.curihous.qbit.domain")
public class QbitApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(com.curihous.qbit.api.QbitApiApplication.class, args);
    }
}
