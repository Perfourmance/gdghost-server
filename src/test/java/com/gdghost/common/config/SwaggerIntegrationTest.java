package com.gdghost.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.gdghost.TestcontainersConfiguration;

class SwaggerIntegrationTest {

    @Nested
    @Import(TestcontainersConfiguration.class)
    @SpringBootTest
    @AutoConfigureMockMvc
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
        void Swagger_화면이_로그인_없이_열린다() throws Exception {
            mvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isOk());
        }

        @Test
        void 그_밖의_주소는_막힌다() throws Exception {
            mvc.perform(get("/api/v1/anything"))
                    .andExpect(status().isForbidden());
        }

    }

    @Nested
    @Import(TestcontainersConfiguration.class)
    @SpringBootTest(properties = {
            // prod 설정 파일의 ${DB_*} 자리 채우기. 실제 연결은 Testcontainers가 덮어쓴다
            "DB_URL=jdbc:mysql://unused", "DB_USERNAME=unused", "DB_PASSWORD=unused",
    })
    @ActiveProfiles("prod")
    @AutoConfigureMockMvc
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
