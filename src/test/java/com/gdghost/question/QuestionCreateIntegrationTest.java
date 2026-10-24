package com.gdghost.question;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.idempotency.IdempotencyAspect;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** 질문 작성 */
@IntegrationTest
class QuestionCreateIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember me;
    Cookie login;

    @BeforeEach
    void setUp() {
        me = fixtures.member();
        login = fixtures.loginCookie(me);
    }

    @Test
    void 만들면_201과_Location_상세와_같은_모양을_준다() throws Exception {
        MockHttpServletResponse response = create(UUID.randomUUID().toString(), """
                {"category":"JOB","title":"  신입 이력서에 학점 쓰는 게 맞나요  ","body":"   "}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("JOB"))
                .andExpect(jsonPath("$.title").value("신입 이력서에 학점 쓰는 게 맞나요"))
                .andExpect(jsonPath("$.body").doesNotExist())
                .andExpect(jsonPath("$.anonymous").value(false))
                .andExpect(jsonPath("$.author.id").value(me.id()))
                .andExpect(jsonPath("$.author.nickname").value(me.nickname()))
                .andExpect(jsonPath("$.author.withdrawn").value(false))
                .andExpect(jsonPath("$.state").value("WAITING"))
                .andExpect(jsonPath("$.viewCount").value(0))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.canAnswer").value(false))
                .andExpect(jsonPath("$.canPostAnonymously").value(false))
                .andExpect(jsonPath("$.answers").isEmpty())
                .andExpect(jsonPath("$.routing").isEmpty())
                .andExpect(jsonPath("$.directDelivery").isEmpty())
                .andReturn().getResponse();
        String body = response.getContentAsString();
        String location = response.getHeader("Location");

        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        assertThat(location).isEqualTo("/api/v1/questions/" + id);
        assertThat(jdbc.queryForObject("SELECT author_id FROM question WHERE id = ?", Long.class, id))
                .isEqualTo(me.id());
    }

    @Test
    void 익명이면_작성자_id_없이_label과_mine만_주고_글쓴이_번호_0을_남긴다() throws Exception {
        String body = create(UUID.randomUUID().toString(), """
                {"category":"CAREER","title":"익명 질문","body":"본문","anonymous":true}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.anonymous").value(true))
                .andExpect(jsonPath("$.author.anonymous").value(true))
                .andExpect(jsonPath("$.author.label").value("익명"))
                .andExpect(jsonPath("$.author.mine").value(true))
                .andExpect(jsonPath("$.author.id").doesNotExist())
                .andExpect(jsonPath("$.author.nickname").doesNotExist())
                .andExpect(jsonPath("$.canPostAnonymously").value(true))
                .andReturn().getResponse().getContentAsString();

        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        assertThat(jdbc.queryForMap("SELECT member_id, alias_no FROM question_anon_alias WHERE question_id = ?", id))
                .containsEntry("member_id", me.id())
                .containsEntry("alias_no", 0);
    }

    @Test
    void 제목이_짧거나_길거나_분류가_없으면_400() throws Exception {
        create(UUID.randomUUID().toString(), "{\"category\":\"JOB\",\"title\":\" 가 \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        create(UUID.randomUUID().toString(), "{\"category\":\"JOB\",\"title\":\"" + "가".repeat(81) + "\"}")
                .andExpect(status().isBadRequest());
        create(UUID.randomUUID().toString(), "{\"title\":\"제목입니다\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("category"));
        create(UUID.randomUUID().toString(), "{\"category\":\"JOB\",\"title\":\"제목입니다\",\"body\":\""
                + "가".repeat(3001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("body"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question", Integer.class)).isZero();
    }

    @Test
    void 같은_키로_다시_보내면_하나만_만든다() throws Exception {
        String key = UUID.randomUUID().toString();
        String content = "{\"category\":\"JOB\",\"title\":\"제목입니다\"}";
        String first = create(key, content).andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        create(key, content)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", first));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question", Integer.class)).isOne();
    }

    @Test
    void 키가_없으면_400() throws Exception {
        mvc.perform(post("/api/v1/questions").cookie(login).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"JOB\",\"title\":\"제목입니다\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(IdempotencyAspect.HEADER));
    }

    @Test
    void 분당_3회를_넘으면_429() throws Exception {
        for (int i = 0; i < 3; i++) {
            create(UUID.randomUUID().toString(), "{\"category\":\"JOB\",\"title\":\"제목 " + i + "\"}")
                    .andExpect(status().isCreated());
        }

        create(UUID.randomUUID().toString(), "{\"category\":\"JOB\",\"title\":\"제목 3\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }

    private ResultActions create(String key, String content) throws Exception {
        return mvc.perform(post("/api/v1/questions").cookie(login).with(csrfToken())
                .header(IdempotencyAspect.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON).content(content));
    }

}
