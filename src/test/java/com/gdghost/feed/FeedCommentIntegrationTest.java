package com.gdghost.feed;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.idempotency.IdempotencyAspect;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class FeedCommentIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember author;
    TestMember member;
    TestMember third;
    Cookie authorLogin;
    Cookie login;
    Cookie thirdLogin;
    long feed;

    @BeforeEach
    void setUp() {
        author = fixtures.member();
        member = fixtures.member();
        third = fixtures.member();
        authorLogin = fixtures.loginCookie(author);
        login = fixtures.loginCookie(member);
        thirdLogin = fixtures.loginCookie(third);
        feed = fixtures.feedPost(author.id());
    }

    private ResultActions write(Cookie cookie, long postId, String key, String json) throws Exception {
        return mvc.perform(post("/api/v1/feed-posts/{id}/comments", postId).cookie(cookie).with(csrfToken())
                .header(IdempotencyAspect.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions write(Cookie cookie, String body, Long parentId) throws Exception {
        String parent = parentId == null ? "" : ",\"parentId\":" + parentId;
        return write(cookie, feed, UUID.randomUUID().toString(), "{\"body\":\"" + body + "\"" + parent + "}");
    }

    private long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.id")).longValue();
    }

    private ResultActions list(Cookie cookie) throws Exception {
        return mvc.perform(get("/api/v1/feed-posts/{id}/comments", feed).cookie(cookie));
    }

    private ResultActions remove(Cookie cookie, long id) throws Exception {
        return mvc.perform(delete("/api/v1/feed-comments/{id}", id).cookie(cookie).with(csrfToken()));
    }

    private long insert(long authorId, Long parentId, String body) {
        Map<String, Object> row = new HashMap<>();
        row.put("post_id", feed);
        row.put("parent_id", parentId);
        row.put("depth", parentId == null ? 0 : 1);
        row.put("author_id", authorId);
        row.put("body", body);
        return new SimpleJdbcInsert(jdbc).withTableName("feed_comment")
                .usingColumns("post_id", "parent_id", "depth", "author_id", "body")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(row).longValue();
    }

    private void softDelete(long id, String by) {
        jdbc.update("UPDATE feed_comment SET deleted_at = NOW(6), deleted_by = ? WHERE id = ?", by, id);
    }

    private int notifications(long memberId, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE member_id = ? AND type = ?",
                Integer.class, memberId, type);
    }

    @Test
    void 댓글을_쓰면_글쓴이에게_알리고_목록_항목_모양으로_돌려준다() throws Exception {
        long id = id(write(login, "  여기 맛있죠  ", null)
                .andExpect(jsonPath("$.body").value("여기 맛있죠"))
                .andExpect(jsonPath("$.parentId").isEmpty())
                .andExpect(jsonPath("$.depth").value(0))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.replies", hasSize(0)))
                .andExpect(jsonPath("$.deletedBy").doesNotExist()));

        assertThat(notifications(author.id(), "FEED_COMMENT")).isEqualTo(1);
        var row = jdbc.queryForMap("""
                SELECT title, target_type, target_id, sub_target_type, sub_target_id
                FROM notification WHERE member_id = ?
                """, author.id());
        assertThat((String) row.get("title")).contains(member.nickname());
        assertThat(row.get("target_type")).isEqualTo("FEED_POST");
        assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(feed);
        assertThat(row.get("sub_target_type")).isEqualTo("FEED_COMMENT");
        assertThat(((Number) row.get("sub_target_id")).longValue()).isEqualTo(id);
        mvc.perform(get("/api/v1/feed-posts/{id}", feed).cookie(login))
                .andExpect(jsonPath("$.commentCount").value(1));
    }

    @Test
    void 자기_글에_단_댓글은_알리지_않는다() throws Exception {
        write(authorLogin, "제가 씁니다", null).andExpect(status().isCreated());
        assertThat(notifications(author.id(), "FEED_COMMENT")).isZero();
    }

    @Test
    void 답글은_대상_댓글_작성자에게만_알린다() throws Exception {
        long comment = insert(member.id(), null, "댓글");

        write(thirdLogin, "답글입니다", comment).andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(comment))
                .andExpect(jsonPath("$.depth").value(1))
                .andExpect(jsonPath("$.replies").doesNotExist());

        assertThat(notifications(member.id(), "FEED_REPLY")).isEqualTo(1);
        assertThat(notifications(author.id(), "FEED_COMMENT")).isZero();
        assertThat(notifications(author.id(), "FEED_REPLY")).isZero();
    }

    @Test
    void 내_댓글에_단_답글은_알리지_않는다() throws Exception {
        long comment = insert(member.id(), null, "댓글");
        write(login, "제가 답합니다", comment).andExpect(status().isCreated());
        assertThat(notifications(member.id(), "FEED_REPLY")).isZero();
    }

    @Test
    void 답글에_답하면_부모_댓글에_붙고_닉네임이_앞에_붙으며_답글_작성자에게_알린다() throws Exception {
        long comment = insert(member.id(), null, "댓글");
        long reply = insert(third.id(), comment, "답글");

        write(authorLogin, "다음엔 같이 가요", reply).andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(comment))
                .andExpect(jsonPath("$.depth").value(1))
                .andExpect(jsonPath("$.body").value("@" + third.nickname() + " 다음엔 같이 가요"));

        assertThat(notifications(third.id(), "FEED_REPLY")).isEqualTo(1);
        assertThat(notifications(member.id(), "FEED_REPLY")).isZero();
    }

    @Test
    void 닉네임까지_합쳐_300자를_넘으면_400이고_저장되지_않는다() throws Exception {
        long comment = insert(member.id(), null, "댓글");
        long reply = insert(third.id(), comment, "답글");

        write(authorLogin, "가".repeat(300), reply).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("body"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_comment", Integer.class)).isEqualTo(2);
    }

    @Test
    void 탈퇴한_사람의_답글에_답하면_닉네임을_붙이지_않는다() throws Exception {
        TestMember gone = fixtures.withdrawn();
        long comment = insert(member.id(), null, "댓글");
        long reply = insert(gone.id(), comment, "답글");

        write(authorLogin, "답합니다", reply).andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("답합니다"));
    }

    @Test
    void 글이나_부모가_없으면_404() throws Exception {
        long comment = insert(member.id(), null, "댓글");
        long otherPost = fixtures.feedPost(author.id());

        write(login, 999_999, UUID.randomUUID().toString(), "{\"body\":\"내용\"}").andExpect(status().isNotFound());
        write(login, "내용", 999_999L).andExpect(status().isNotFound());
        write(login, otherPost, UUID.randomUUID().toString(), "{\"body\":\"내용\",\"parentId\":" + comment + "}")
                .andExpect(status().isNotFound());
        softDelete(comment, "AUTHOR");
        write(login, "내용", comment).andExpect(status().isNotFound());
        jdbc.update("UPDATE feed_post SET deleted_at = NOW(6), deleted_by = 'STAFF' WHERE id = ?", feed);
        write(login, "내용", null).andExpect(status().isNotFound());
        list(login).andExpect(status().isNotFound());
    }

    @Test
    void 본문은_1에서_300자() throws Exception {
        write(login, "   ", null).andExpect(status().isBadRequest());
        write(login, "가".repeat(301), null).andExpect(status().isBadRequest());
        write(login, "가".repeat(300), null).andExpect(status().isCreated());
    }

    @Test
    void 작성은_멱등이고_분당_5회() throws Exception {
        String key = UUID.randomUUID().toString();
        write(login, feed, key, "{\"body\":\"한 번\"}").andExpect(status().isCreated());
        write(login, feed, key, "{\"body\":\"한 번\"}").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_comment", Integer.class)).isEqualTo(1);

        for (int i = 0; i < 4; i++) {
            write(login, "내용 " + i, null).andExpect(status().isCreated());
        }
        write(login, "내용", null).andExpect(status().isTooManyRequests());
    }

    @Test
    void 목록은_오래된_순이고_답글을_묶어_커서로_넘어간다() throws Exception {
        long first = insert(member.id(), null, "첫째");
        long second = insert(author.id(), null, "둘째");
        long third2 = insert(member.id(), null, "셋째");
        long replyA = insert(author.id(), first, "답글 가");
        long replyB = insert(member.id(), first, "답글 나");
        long deletedReply = insert(member.id(), first, "지운 답글");
        softDelete(deletedReply, "AUTHOR");

        String page = mvc.perform(get("/api/v1/feed-posts/{id}/comments", feed).param("size", "2").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) first, (int) second)))
                .andExpect(jsonPath("$.items[0].replies[*].id", contains((int) replyA, (int) replyB)))
                .andExpect(jsonPath("$.items[0].mine").value(true))
                .andExpect(jsonPath("$.items[0].replies[0].mine").value(false))
                .andExpect(jsonPath("$.items[0].replies[0].replies").doesNotExist())
                .andExpect(jsonPath("$.items[1].replies", hasSize(0)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/feed-posts/{id}/comments", feed).param("size", "2")
                .param("cursor", JsonPath.<String>read(page, "$.nextCursor")).cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) third2)))
                .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get("/api/v1/feed-posts/{id}/comments", feed).param("cursor", "!!!").cookie(login))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 지운_댓글은_답글이_있을_때만_자리가_남고_내용은_나가지_않는다() throws Exception {
        long withReply = insert(member.id(), null, "비밀 본문");
        insert(third.id(), withReply, "남은 답글");
        long hidden = insert(member.id(), null, "숨긴 본문");
        insert(third.id(), hidden, "남은 답글 둘");
        long alone = insert(member.id(), null, "혼자 지운 글");
        long onlyDeletedReply = insert(member.id(), null, "답글도 지움");
        long gone = insert(third.id(), onlyDeletedReply, "지운 답글");
        softDelete(withReply, "AUTHOR");
        softDelete(hidden, "STAFF");
        softDelete(alone, "AUTHOR");
        softDelete(onlyDeletedReply, "AUTHOR");
        softDelete(gone, "AUTHOR");

        String body = list(login)
                .andExpect(jsonPath("$.items[*].id", contains((int) withReply, (int) hidden)))
                .andExpect(jsonPath("$.items[0].deletedBy").value("AUTHOR"))
                .andExpect(jsonPath("$.items[0].body").isEmpty())
                .andExpect(jsonPath("$.items[0].author").isEmpty())
                .andExpect(jsonPath("$.items[0].mine").value(false))
                .andExpect(jsonPath("$.items[0].replies", hasSize(1)))
                .andExpect(jsonPath("$.items[1].deletedBy").value("STAFF"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("비밀 본문").doesNotContain("숨긴 본문");
    }

    @Test
    void 탈퇴한_작성자는_탈퇴한_부원으로_보인다() throws Exception {
        insert(fixtures.withdrawn().id(), null, "남은 댓글");
        list(login)
                .andExpect(jsonPath("$.items[0].author.nickname").value("탈퇴한 부원"))
                .andExpect(jsonPath("$.items[0].author.id").isEmpty())
                .andExpect(jsonPath("$.items[0].body").value("남은 댓글"));
    }

    @Test
    void 본인_댓글만_지운다() throws Exception {
        long id = insert(member.id(), null, "댓글");

        remove(authorLogin, id).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHOR"));
        remove(fixtures.loginCookie(fixtures.staff()), id).andExpect(status().isForbidden());
        remove(login, id).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT deleted_by FROM feed_comment WHERE id = ?", String.class, id))
                .isEqualTo("AUTHOR");
        remove(login, id).andExpect(status().isNotFound());
        remove(login, 999_999).andExpect(status().isNotFound());
    }

    @Test
    void 글이_지워졌으면_댓글_삭제도_404() throws Exception {
        long id = insert(member.id(), null, "댓글");
        jdbc.update("UPDATE feed_post SET deleted_at = NOW(6), deleted_by = 'AUTHOR' WHERE id = ?", feed);
        remove(login, id).andExpect(status().isNotFound());
    }

    private ResultActions report(Cookie cookie, long id) throws Exception {
        return mvc.perform(post("/api/v1/reports").cookie(cookie).with(csrfToken())
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"FEED_COMMENT\",\"targetId\":" + id + ",\"reason\":\"광고\"}"));
    }

    private ResultActions handle(Cookie staff, long reportId, String action) throws Exception {
        return mvc.perform(patch("/api/v1/ops/reports/{id}", reportId).cookie(staff).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"" + action + "\"}"));
    }

    @Test
    void 신고로_숨기고_되돌린다() throws Exception {
        long id = insert(member.id(), null, "광고 댓글");
        Cookie staff = fixtures.loginCookie(fixtures.staff());
        String created = report(authorLogin, id).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long reportId = ((Number) JsonPath.read(created, "$.id")).longValue();

        handle(staff, reportId, "HIDE").andExpect(status().isOk())
                .andExpect(jsonPath("$.targetPreview").value("광고 댓글"))
                .andExpect(jsonPath("$.targetLink").value("/feed/" + feed + "#comment-" + id))
                .andExpect(jsonPath("$.targetDeletedBy").value("STAFF"));
        list(login).andExpect(jsonPath("$.items", hasSize(0)));
        report(thirdLogin, id).andExpect(status().isNotFound());

        handle(staff, reportId, "RESTORE").andExpect(status().isOk());
        list(login).andExpect(jsonPath("$.items[0].body").value("광고 댓글"));
    }

    @Test
    void 작성자가_지운_댓글은_되돌릴_수_없다() throws Exception {
        long id = insert(member.id(), null, "댓글");
        Cookie staff = fixtures.loginCookie(fixtures.staff());
        String created = report(authorLogin, id).andReturn().getResponse().getContentAsString();
        long reportId = ((Number) JsonPath.read(created, "$.id")).longValue();
        remove(login, id).andExpect(status().isNoContent());

        handle(staff, reportId, "HIDE").andExpect(status().isOk());
        handle(staff, reportId, "RESTORE").andExpect(status().isConflict());
    }

}
