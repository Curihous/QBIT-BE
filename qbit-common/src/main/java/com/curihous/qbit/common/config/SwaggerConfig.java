package com.curihous.qbit.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${info.app.name:QBIT Backend}")
    private String appName;

    @Value("${info.app.version:1.0.0}")
    private String appVersion;

    @Value("${info.app.description:QBIT 백엔드 API}")
    private String appDescription;

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
                        .title(appName)
                        .description(appDescription)
                        .version(appVersion))
                .servers(List.of(
                        new Server().url("https://api.qbit.o-r.kr").description("운영 서버"),
                        new Server().url("http://localhost:8080").description("로컬 개발 서버")

                ))
                .addSecurityItem(securityRequirement)
                .components(components);
    }
    
    // Swagger 태그 순서 커스터마이징
    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
                .group("인증")
                .pathsToMatch("/auth/**")
                .build();
    }
    
    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("사용자")
                .pathsToMatch("/users/**")
                .build();
    }

    @Bean
    public GroupedOpenApi alpacaApi() {
        return GroupedOpenApi.builder()
                .group("Alpaca")
                .pathsToMatch("/alpaca/**")
                .build();
    }

    @Bean
    public GroupedOpenApi stockApi() {
        return GroupedOpenApi.builder()
                .group("종목")
                .pathsToMatch("/stocks/**")
                .build();
    }

    @Bean
    public GroupedOpenApi indexApi() {
        return GroupedOpenApi.builder()
                .group("지수")
                .pathsToMatch("/indices/**")
                .build();
    }

    @Bean
    public GroupedOpenApi tradingApi() {
        return GroupedOpenApi.builder()
                .group("거래")
                .pathsToMatch("/trading/**")
                .build();
    }
    
    @Bean
    public GroupedOpenApi portfolioApi() {
        return GroupedOpenApi.builder()
                .group("포트폴리오")
                .pathsToMatch("/portfolios/**")
                .build();
    }
}
