package com.gdghost.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger 문서 정보. 로그인은 HttpOnly 쿠키라 Swagger에서 로그인 API를 부르면
 * 브라우저가 쿠키를 저장하고 이후 요청에 자동으로 붙인다.
 */
@Configuration
public class OpenApiConfig {

    public static final String SESSION_COOKIE = "GDG_SESSION";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("GDGhost API")
                        .description("GDGoC SMU 커뮤니티 GDGhost 백엔드")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes("sessionCookie", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(SESSION_COOKIE)));
    }

}
