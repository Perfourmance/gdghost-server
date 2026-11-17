package com.gdghost.member;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.gdghost.support.IntegrationTest;

@IntegrationTest
class DepartmentIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 비회원도_단과대학별_학과_36개를_받는다() throws Exception {
        mvc.perform(get("/api/v1/departments"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=3600"))
                .andExpect(jsonPath("$.colleges", hasSize(6)))
                .andExpect(jsonPath("$.colleges[*].departments[*]", hasSize(36)))
                .andExpect(jsonPath("$.colleges[4].name").value("융합공과대학"))
                .andExpect(jsonPath("$.colleges[4].departments[4].name").value("컴퓨터과학전공"))
                .andExpect(jsonPath("$.colleges[4].departments[4].division").value("SW융합학부"))
                .andExpect(jsonPath("$.colleges[1].departments[4].name").value("공간환경학부"))
                .andExpect(jsonPath("$.colleges[1].departments[4].division").doesNotExist());
    }

}
