package com.gdghost.tag;

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
class TagIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 비회원도_분야별_세부_기술과_경험_태그를_순서대로_받는다() throws Exception {
        mvc.perform(get("/api/v1/tags"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=3600"))
                .andExpect(jsonPath("$.interests", hasSize(9)))
                .andExpect(jsonPath("$.interests[0].name").value("백엔드"))
                .andExpect(jsonPath("$.interests[0].sortOrder").value(1))
                .andExpect(jsonPath("$.interests[0].skills", hasSize(8)))
                .andExpect(jsonPath("$.interests[0].skills[0].name").value("Spring"))
                .andExpect(jsonPath("$.interests[8].name").value("기타"))
                .andExpect(jsonPath("$.experiences", hasSize(18)))
                .andExpect(jsonPath("$.experiences[0].name").value("인턴"))
                .andExpect(jsonPath("$.experiences[0].groupName").value("취업 · 커리어"));
    }

}
