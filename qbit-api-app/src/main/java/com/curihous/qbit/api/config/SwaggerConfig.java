package com.curihous.qbit.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        String jwt = "Bearer Authentication";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwt);
        Components components = new Components().addSecuritySchemes(jwt, new SecurityScheme()
                .name(jwt)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
        );

        return new OpenAPI()
                .info(new Info()
                        .title("QBIT API")
                        .description("QBIT 백엔드 API 문서")
                        .version("v1.0.0"))
                .servers(List.of(
                        // TODO: 배포 이후 변경
                        new Server().url("http://localhost:8080").description("로컬 개발 서버"),
                        new Server().url("https://api.qbit.o-r.kr").description("운영 서버")
                ))
                .addSecurityItem(securityRequirement)
                .components(components);
    }
}
