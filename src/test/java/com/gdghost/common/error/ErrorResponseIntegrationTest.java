package com.gdghost.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.gdghost.support.IntegrationTest;

@IntegrationTest
class ErrorResponseIntegrationTest {

    @Autowired
    MockMvc mvc;

    // 쿠키 인증(#11) 전이라 보안 필터는 가짜 로그인으로 통과시킨다
    @Test
    @WithMockUser
    void 없는_주소는_404_NOT_FOUND() throws Exception {
        mvc.perform(get("/api/v1/no-such-api"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/api/v1/no-such-api"));
    }

}
