package com.curihous.qbit.realtime;

import com.curihous.qbit.infra.security.jwt.JwtUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(
    scanBasePackages = {
        "com.curihous.qbit.realtime",
        "com.curihous.qbit.common"
    },
    exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    }
)
@Import(JwtUtil.class)  // JWT 인증만 사용
public class QbitRealtimeApplication {

    public static void main(String[] args) {
        System.setProperty("jdk.tls.client.protocols", "TLSv1.2"); 
        System.setProperty("https.protocols", "TLSv1.2");
        
        SpringApplication.run(QbitRealtimeApplication.class, args);
    }
}

