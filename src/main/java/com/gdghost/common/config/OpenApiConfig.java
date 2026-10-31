package com.gdghost.common.config;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gdghost.common.idempotency.Idempotent;
import com.gdghost.common.idempotency.IdempotencyAspect;
import com.gdghost.common.security.SessionCookie;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger 문서 정보. 로그인은 HttpOnly 쿠키라 Swagger에서 로그인 API를 부르면
 * 브라우저가 쿠키를 저장하고 이후 요청에 자동으로 붙인다.
 */
@Configuration
public class OpenApiConfig {

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
                                .name(SessionCookie.NAME)));
    }

    /** @Idempotent는 애스펙트가 헤더를 읽어서 문서에 저절로 나오지 않는다 — 없으면 Swagger에서 부를 수 없다 */
    @Bean
    OperationCustomizer idempotencyHeader() {
        return (operation, handler) -> {
            if (handler.hasMethodAnnotation(Idempotent.class)) {
                operation.addParametersItem(new HeaderParameter()
                        .name(IdempotencyAspect.HEADER)
                        .required(true)
                        .description("요청마다 새 값(UUID 권장). 같은 값으로 다시 보내면 첫 응답을 그대로 돌려준다")
                        .schema(new StringSchema()));
            }
            return operation;
        };
    }

}
