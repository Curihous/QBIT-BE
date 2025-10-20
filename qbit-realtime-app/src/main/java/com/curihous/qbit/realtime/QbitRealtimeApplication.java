package com.curihous.qbit.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

@SpringBootApplication(
    scanBasePackages = {
        "com.curihous.qbit.realtime",
        "com.curihous.qbit.common",
        "com.curihous.qbit.infra"
    },
    exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    }
)
public class QbitRealtimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(QbitRealtimeApplication.class, args);
    }
}

