package com.gdghost.feed;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class FeedLikeIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember author;
    Cookie authorLogin;
    Cookie login;
    long post;

    @BeforeEach
    void setUp() {
        author = fixtures.member();
        authorLogin = fixtures.loginCookie(author);
        login = fixtures.loginCookie(fixtures.member());
        post = fixtures.feedPost(author.id());
    }

    private ResultActions like(Cookie cookie, long id) throws Exception {
        return mvc.perform(put("/api/v1/feed-posts/{id}/like", id).cookie(cookie).with(csrfToken()));
    }

    private ResultActions unlike(Cookie cookie, long id) throws Exception {
        return mvc.perform(delete("/api/v1/feed-posts/{id}/like", id).cookie(cookie).with(csrfToken()));
    }

    private long storedCount() {
        return jdbc.queryForObject("SELECT like_count FROM feed_post WHERE id = ?", Long.class, post);
    }

    @Test
    void 좋아요는_멱등이고_수를_한_번만_올린다() throws Exception {
        like(login, post).andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
        like(login, post).andExpect(jsonPath("$.likeCount").value(1));
        like(authorLogin, post).andExpect(jsonPath("$.likeCount").value(2));

        assertThat(storedCount()).isEqualTo(2);
        mvc.perform(get("/api/v1/feed-posts/{id}", post).cookie(login))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(2));
    }

    @Test
    void 취소하면_수가_줄고_누르지_않았으면_그대로다() throws Exception {
        like(authorLogin, post);
        unlike(login, post).andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(1));

        like(login, post);
        unlike(login, post).andExpect(jsonPath("$.likeCount").value(1));
        unlike(login, post).andExpect(jsonPath("$.likeCount").value(1));
        assertThat(storedCount()).isEqualTo(1);
    }

    @Test
    void 없거나_지운_글은_좋아요할_수_없다() throws Exception {
        like(login, 999_999).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        jdbc.update("UPDATE feed_post SET deleted_at = NOW(6), deleted_by = 'STAFF' WHERE id = ?", post);
        like(login, post).andExpect(status().isNotFound());
    }

    @Test
    void 지운_글이나_없는_글도_취소는_200() throws Exception {
        like(login, post);
        jdbc.update("UPDATE feed_post SET deleted_at = NOW(6), deleted_by = 'AUTHOR' WHERE id = ?", post);

        unlike(login, post).andExpect(status().isOk()).andExpect(jsonPath("$.likeCount").value(0));
        unlike(login, 999_999).andExpect(status().isOk()).andExpect(jsonPath("$.likeCount").value(0));
    }

    @Test
    void 좋아요는_알림을_보내지_않는다() throws Exception {
        like(login, post);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE member_id = ?", Integer.class,
                author.id())).isZero();
    }

}
