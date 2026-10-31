package com.gdghost.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.gdghost.support.IntegrationTest;

class SwaggerIntegrationTest {

    @Nested
    @IntegrationTest
    class 개발_환경 {

        @Autowired
        MockMvc mvc;

        @Test
        void API_문서가_로그인_없이_열린다() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").value("GDGhost API"))
                    .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.in").value("cookie"));
        }

        @Test
        void 멱등_API에는_Idempotency_Key_헤더가_문서에_있다() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.paths['/api/v1/questions'].post.parameters[0].name")
                            .value("Idempotency-Key"))
                    .andExpect(jsonPath("$.paths['/api/v1/questions'].post.parameters[0].required").value(true));
        }

        @Test
        void Swagger_화면이_로그인_없이_열린다() throws Exception {
            mvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isOk());
        }

        @Test
        void 그_밖의_주소는_로그인이_필요하다() throws Exception {
            mvc.perform(get("/api/v1/anything"))
                    .andExpect(status().isUnauthorized());
        }

    }

    @Nested
    @IntegrationTest
    @TestPropertySource(properties = {
            // prod 설정 파일의 ${DB_*} · ${MAIL_*} · ${DISCORD_*} · ${VAPID_*} · Workers AI 자리 채우기. 실제 DB 연결은 Testcontainers가 덮어쓴다
            "DB_URL=jdbc:mysql://unused", "DB_USERNAME=unused", "DB_PASSWORD=unused",
            "MAIL_USERNAME=unused", "MAIL_PASSWORD=unused", "WEB_URL=https://unused",
            "DISCORD_MEMBER_WEBHOOK_URL=https://unused", "DISCORD_STAFF_WEBHOOK_URL=https://unused",
            "DISCORD_DEV_WEBHOOK_URL=https://unused",
            "VAPID_PUBLIC_KEY=BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
            "VAPID_PRIVATE_KEY=yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw",
            "CLOUDFLARE_ACCOUNT_ID=unused", "WORKERS_AI_API_TOKEN=unused",
    })
    @ActiveProfiles("prod")
    class 운영_환경 {

        @Autowired
        MockMvc mvc;

        @Test
        void API_문서가_꺼져_있다() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void Swagger_화면이_꺼져_있다() throws Exception {
            mvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isNotFound());
        }

    }

}
