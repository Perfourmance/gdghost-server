package com.gdghost.share;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class ShareSaveIntegrationTest {

    private static final String SAVED = "/api/v1/me/saved-shares";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember author;
    TestMember member;
    Cookie login;
    long share;

    @BeforeEach
    void setUp() {
        author = fixtures.member();
        member = fixtures.member();
        login = fixtures.loginCookie(member);
        share = fixtures.share(author.id());
    }

    private ResultActions save(Cookie cookie, long id) throws Exception {
        return mvc.perform(put("/api/v1/shares/{id}/save", id).cookie(cookie).with(csrfToken()));
    }

    private ResultActions unsave(Cookie cookie, long id) throws Exception {
        return mvc.perform(delete("/api/v1/shares/{id}/save", id).cookie(cookie).with(csrfToken()));
    }

    private void softDelete(long id, String by) {
        jdbc.update("UPDATE share SET deleted_at = UTC_TIMESTAMP(6), deleted_by = ? WHERE id = ?", by, id);
    }

    private long storedCount() {
        return jdbc.queryForObject("SELECT save_count FROM share WHERE id = ?", Long.class, share);
    }

    @Test
    void 저장은_멱등이고_수를_한_번만_올린다() throws Exception {
        save(login, share).andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.saveCount").value(1));
        save(login, share).andExpect(status().isOk())
                .andExpect(jsonPath("$.saveCount").value(1));
        save(fixtures.loginCookie(author), share).andExpect(jsonPath("$.saveCount").value(2));

        assertThat(storedCount()).isEqualTo(2);
        mvc.perform(get("/api/v1/shares/{id}", share).cookie(login))
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.saveCount").value(2));
    }

    @Test
    void 저장한_적이_없으면_취소해도_수가_그대로다() throws Exception {
        save(fixtures.loginCookie(author), share);

        unsave(login, share).andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.saveCount").value(1));
        assertThat(storedCount()).isEqualTo(1);
    }

    @Test
    void 저장을_취소하면_수가_줄고_다시_저장할_수_있다() throws Exception {
        save(login, share);

        unsave(login, share).andExpect(jsonPath("$.saveCount").value(0));
        unsave(login, share).andExpect(status().isOk()).andExpect(jsonPath("$.saveCount").value(0));
        save(login, share).andExpect(jsonPath("$.saveCount").value(1));
    }

    @Test
    void 없거나_지운_글은_저장할_수_없다() throws Exception {
        save(login, 999_999).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        softDelete(share, "STAFF");
        save(login, share).andExpect(status().isNotFound());
    }

    @Test
    void 지운_글이나_없는_글도_취소는_200() throws Exception {
        save(login, share);
        softDelete(share, "AUTHOR");

        unsave(login, share).andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(false));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM share_save WHERE share_id = ?", Integer.class, share))
                .isZero();
        unsave(login, 999_999).andExpect(status().isOk()).andExpect(jsonPath("$.saveCount").value(0));
    }

    @Test
    void 저장_목록은_최근_저장한_순이고_커서로_넘어간다() throws Exception {
        long second = fixtures.share(author.id());
        long third = fixtures.share(author.id());
        // 글 순서와 저장 순서를 다르게, second와 third는 같은 시각
        jdbc.update("""
                INSERT INTO share_save (share_id, member_id, created_at)
                VALUES (?, ?, '2026-10-03 00:00:00.123456'), (?, ?, '2026-10-01 00:00:00.000000'),
                       (?, ?, '2026-10-01 00:00:00.000000')
                """, share, member.id(), second, member.id(), third, member.id());
        jdbc.update("INSERT INTO share_tag (share_id, name) VALUES (?, '혼밥')", share);

        String first = mvc.perform(get(SAVED).param("size", "2").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) share, (int) third)))
                .andExpect(jsonPath("$.items[0].saved").value(true))
                .andExpect(jsonPath("$.items[0].tags[0]").value("혼밥"))
                .andExpect(jsonPath("$.items[0].savedAt").value("2026-10-03T00:00:00.123456Z"))
                .andExpect(jsonPath("$.items[0].body").doesNotExist())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get(SAVED).param("size", "2").param("cursor", JsonPath.<String>read(first, "$.nextCursor"))
                .cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) second)))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void 저장_목록은_내_것만이고_지운_글은_빠진다() throws Exception {
        save(login, share);
        long hidden = fixtures.share(author.id());
        save(login, hidden);
        softDelete(hidden, "STAFF");
        save(fixtures.loginCookie(author), fixtures.share(author.id()));

        mvc.perform(get(SAVED).cookie(login))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(share));
    }

    @Test
    void 잘못된_커서는_400() throws Exception {
        mvc.perform(get(SAVED).param("cursor", "MTIz").cookie(login))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cursor"));
    }

    @Test
    void 수정해도_저장_수가_덮어써지지_않는다() throws Exception {
        save(login, share);
        mvc.perform(patch("/api/v1/shares/{id}", share).cookie(fixtures.loginCookie(author)).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"새 제목입니다\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saveCount").value(1));
        assertThat(storedCount()).isEqualTo(1);
    }

}
