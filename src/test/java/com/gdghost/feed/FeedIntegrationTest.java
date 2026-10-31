package com.gdghost.feed;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.idempotency.IdempotencyAspect;
import com.gdghost.support.FakeObjectStorage;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class FeedIntegrationTest {

    private static final String URL = "/api/v1/feed-posts";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeObjectStorage storage;

    TestMember author;
    TestMember other;
    Cookie login;
    Cookie otherLogin;

    @BeforeEach
    void setUp() {
        storage.clear();
        author = fixtures.member();
        other = fixtures.member();
        login = fixtures.loginCookie(author);
        otherLogin = fixtures.loginCookie(other);
    }

    private ResultActions create(Cookie cookie, String key, String body) throws Exception {
        return mvc.perform(post(URL).cookie(cookie).with(csrfToken()).header(IdempotencyAspect.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions create(String body) throws Exception {
        return create(login, UUID.randomUUID().toString(), body);
    }

    private ResultActions remove(Cookie cookie, long id) throws Exception {
        return mvc.perform(delete(URL + "/{id}", id).cookie(cookie).with(csrfToken()));
    }

    private String photo(TestMember member, String purpose) {
        String key = "%s/2026/10/%d/%s.jpg".formatted(purpose, member.id(), UUID.randomUUID().toString().replace("-", ""));
        storage.put(key, 1000, Instant.now());
        return key;
    }

    private void attach(long postId, String key) {
        jdbc.update("INSERT INTO attachment (owner_type, owner_id, object_key, sort_order) VALUES ('FEED', ?, ?, 0)",
                postId, key);
    }

    private int attachmentCount(long postId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM attachment WHERE owner_type = 'FEED' AND owner_id = ?",
                Integer.class, postId);
    }

    private void softDelete(long postId, String by) {
        jdbc.update("UPDATE feed_post SET deleted_at = NOW(6), deleted_by = ? WHERE id = ?", by, postId);
    }

    private void comment(long postId, long authorId, boolean deleted) {
        jdbc.update("INSERT INTO feed_comment (post_id, author_id, body, deleted_at) VALUES (?, ?, '댓글', ?)", postId,
                authorId, deleted ? LocalDateTime.now() : null);
    }

    @Test
    void 글을_쓰면_항목_모양으로_돌려준다() throws Exception {
        String key = photo(author, "feed");
        create("{\"category\":\"DAILY\",\"body\":\"  오늘 순대국  \",\"photoKeys\":[\"" + key + "\"]}")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.body").value("오늘 순대국"))
                .andExpect(jsonPath("$.category").value("DAILY"))
                .andExpect(jsonPath("$.author.id").value(author.id()))
                .andExpect(jsonPath("$.photos[0].objectKey").value(key))
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.commentCount").value(0))
                .andExpect(jsonPath("$.mine").value(true));
    }

    @Test
    void 같은_멱등_키는_한_번만_저장한다() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = "{\"category\":\"DAILY\",\"body\":\"본문\"}";
        create(login, key, body).andExpect(status().isCreated());
        create(login, key, body).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_post", Integer.class)).isEqualTo(1);
    }

    @Test
    void 잘못된_입력은_400() throws Exception {
        for (String invalid : List.of(
                "{\"body\":\"본문\"}",
                "{\"category\":\"NEWS\",\"body\":\"본문\"}",
                "{\"category\":\"DAILY\",\"body\":\"   \"}",
                "{\"category\":\"DAILY\",\"body\":\"" + "가".repeat(1001) + "\"}",
                "{\"category\":\"DAILY\",\"body\":\"본문\",\"photoKeys\":[\"a\",\"b\",\"c\",\"d\"]}")) {
            create(fixtures.loginCookie(fixtures.member()), UUID.randomUUID().toString(), invalid)
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void 남의_사진이나_다른_용도의_사진이면_400이고_글이_남지_않는다() throws Exception {
        for (String key : List.of(photo(other, "feed"), photo(author, "share"))) {
            create("{\"category\":\"DAILY\",\"body\":\"본문\",\"photoKeys\":[\"" + key + "\"]}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UPLOAD_NOT_FOUND"));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_post", Integer.class)).isZero();
    }

    @Test
    void 글쓰기는_분당_3회() throws Exception {
        String body = "{\"category\":\"DAILY\",\"body\":\"본문\"}";
        for (int i = 0; i < 3; i++) {
            create(body).andExpect(status().isCreated());
        }
        create(body).andExpect(status().isTooManyRequests());
    }

    @Test
    void 목록은_최신순이고_커서로_넘어가며_지운_글을_뺀다() throws Exception {
        long first = fixtures.feedPost(author.id());
        long second = fixtures.feedPost(author.id());
        long third = fixtures.feedPost(author.id());
        softDelete(fixtures.feedPost(author.id()), "AUTHOR");
        softDelete(fixtures.feedPost(author.id()), "STAFF");

        String page = mvc.perform(get(URL).param("size", "2").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) third, (int) second)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get(URL).param("size", "2").param("cursor", JsonPath.<String>read(page, "$.nextCursor"))
                .cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) first)))
                .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get(URL).param("cursor", "!!!").cookie(login)).andExpect(status().isBadRequest());
    }

    @Test
    void 목록은_분류로_거르고_좋아요_댓글_수_사진을_채운다() throws Exception {
        long post = fixtures.feedPost(author.id());
        long brag = fixtures.feedPost(author.id());
        jdbc.update("UPDATE feed_post SET category = 'BRAG' WHERE id = ?", brag);
        jdbc.update("UPDATE feed_post SET like_count = 1 WHERE id = ?", post);
        jdbc.update("INSERT INTO feed_like (post_id, member_id) VALUES (?, ?)", post, other.id());
        comment(post, other.id(), false);
        comment(post, other.id(), true);
        String key = photo(author, "feed");
        attach(post, key);

        mvc.perform(get(URL).param("category", "DAILY").cookie(otherLogin))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(post))
                .andExpect(jsonPath("$.items[0].liked").value(true))
                .andExpect(jsonPath("$.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.items[0].commentCount").value(1))
                .andExpect(jsonPath("$.items[0].mine").value(false))
                .andExpect(jsonPath("$.items[0].photos[0].url", containsString(key)));
        mvc.perform(get(URL).param("category", "DAILY").cookie(login))
                .andExpect(jsonPath("$.items[0].liked").value(false))
                .andExpect(jsonPath("$.items[0].mine").value(true));
    }

    @Test
    void 숨긴_글의_사진_주소는_나가지_않는다() throws Exception {
        long hidden = fixtures.feedPost(author.id());
        String key = photo(author, "feed");
        attach(hidden, key);
        softDelete(hidden, "STAFF");

        String body = mvc.perform(get(URL).cookie(login)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(key);
    }

    @Test
    void 상세는_항목_모양이고_없거나_지운_글은_404() throws Exception {
        long post = fixtures.feedPost(author.id());
        long hidden = fixtures.feedPost(author.id());
        softDelete(hidden, "STAFF");

        mvc.perform(get(URL + "/{id}", post).cookie(otherLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(post))
                .andExpect(jsonPath("$.mine").value(false))
                .andExpect(jsonPath("$.comments").doesNotExist());
        mvc.perform(get(URL + "/{id}", hidden).cookie(login)).andExpect(status().isNotFound());
        mvc.perform(get(URL + "/{id}", 999_999).cookie(login)).andExpect(status().isNotFound());
    }

    @Test
    void 삭제하면_소프트_삭제되고_사진_연결이_끊긴다() throws Exception {
        long post = fixtures.feedPost(author.id());
        attach(post, photo(author, "feed"));

        remove(otherLogin, post).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHOR"));
        assertThat(attachmentCount(post)).isEqualTo(1);

        remove(login, post).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT deleted_by FROM feed_post WHERE id = ?", String.class, post))
                .isEqualTo("AUTHOR");
        assertThat(attachmentCount(post)).isZero();
        remove(login, post).andExpect(status().isNotFound());
    }

    private long report(long post) throws Exception {
        String response = mvc.perform(post("/api/v1/reports").cookie(otherLogin).with(csrfToken())
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"FEED\",\"targetId\":" + post + ",\"reason\":\"광고\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions handle(Cookie staff, long reportId, String action) throws Exception {
        return mvc.perform(patch("/api/v1/ops/reports/{id}", reportId).cookie(staff).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"" + action + "\"}"));
    }

    @Test
    void 신고로_숨기고_되돌리면_사진도_다시_보인다() throws Exception {
        long post = fixtures.feedPost(author.id());
        String key = photo(author, "feed");
        attach(post, key);
        Cookie staff = fixtures.loginCookie(fixtures.staff());
        long reportId = report(post);

        handle(staff, reportId, "HIDE").andExpect(status().isOk())
                .andExpect(jsonPath("$.targetPreview").exists())
                .andExpect(jsonPath("$.targetLink").value("/feed/" + post))
                .andExpect(jsonPath("$.targetDeletedBy").value("STAFF"));
        mvc.perform(get(URL + "/{id}", post).cookie(login)).andExpect(status().isNotFound());
        assertThat(attachmentCount(post)).isEqualTo(1);

        handle(staff, reportId, "RESTORE").andExpect(status().isOk());
        mvc.perform(get(URL + "/{id}", post).cookie(login))
                .andExpect(jsonPath("$.photos[0].objectKey").value(key));
    }

    @Test
    void 작성자가_지운_글은_되돌릴_수_없다() throws Exception {
        long post = fixtures.feedPost(author.id());
        Cookie staff = fixtures.loginCookie(fixtures.staff());
        long reportId = report(post);
        remove(login, post).andExpect(status().isNoContent());

        handle(staff, reportId, "HIDE").andExpect(status().isOk());
        handle(staff, reportId, "RESTORE").andExpect(status().isConflict());
    }

    @Test
    void 탈퇴하면_글이_지워지고_사진_연결이_끊긴다() throws Exception {
        long post = fixtures.feedPost(author.id());
        attach(post, photo(author, "feed"));

        mvc.perform(post("/api/v1/me/withdrawal").cookie(login).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + Fixtures.PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT deleted_by FROM feed_post WHERE id = ?", String.class, post))
                .isEqualTo("AUTHOR");
        assertThat(attachmentCount(post)).isZero();
    }

}
