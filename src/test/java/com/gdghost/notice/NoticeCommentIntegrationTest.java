package com.gdghost.notice;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.idempotency.IdempotencyAspect;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** 공지 댓글 작성 · 삭제 · 상세 표시 */
@IntegrationTest
class NoticeCommentIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember staff;
    TestMember member;
    Cookie memberLogin;
    long notice;

    @BeforeEach
    void setUp() {
        staff = fixtures.staff();
        member = fixtures.member();
        memberLogin = fixtures.loginCookie(member);
        notice = fixtures.notice(staff.id());
    }

    @Test
    void 작성하면_201과_상세_댓글_항목_모양을_준다() throws Exception {
        String body = create(memberLogin, notice, UUID.randomUUID().toString(), "  이번엔 못 가요 ㅠ  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.author.id").value(member.id()))
                .andExpect(jsonPath("$.author.nickname").value(member.nickname()))
                .andExpect(jsonPath("$.author.generation").value(5))
                .andExpect(jsonPath("$.author.withdrawn").value(false))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<String>read(body, "$.body")).isEqualTo("이번엔 못 가요 ㅠ");
    }

    @Test
    void 상세는_지운_댓글을_빼고_오래된_순으로_전부_보여주고_mine은_보는_사람_기준이다() throws Exception {
        long first = fixtures.noticeComment(notice, member.id());
        long hidden = fixtures.noticeComment(notice, staff.id());
        long second = fixtures.noticeComment(notice, staff.id());
        long removed = fixtures.noticeComment(notice, member.id());
        jdbc.update("UPDATE notice_comment SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'STAFF' WHERE id = ?",
                hidden);
        jdbc.update("UPDATE notice_comment SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?",
                removed);
        TestMember gone = fixtures.member();
        long third = fixtures.noticeComment(notice, gone.id());
        jdbc.update("""
                UPDATE member SET login_id = NULL, email = NULL, nickname = NULL, name = NULL,
                                  withdrawn_at = UTC_TIMESTAMP(6)
                WHERE id = ?
                """, gone.id());

        mvc.perform(get("/api/v1/notices/{id}", notice).cookie(memberLogin))
                .andExpect(jsonPath("$.comments[*].id", contains((int) first, (int) second, (int) third)))
                .andExpect(jsonPath("$.comments[*].mine", contains(true, false, false)))
                .andExpect(jsonPath("$.comments[2].author.id").isEmpty())
                .andExpect(jsonPath("$.comments[2].author.nickname").value("탈퇴한 부원"))
                .andExpect(jsonPath("$.comments[2].author.generation").isEmpty())
                .andExpect(jsonPath("$.comments[2].author.withdrawn").value(true));
        mvc.perform(get("/api/v1/notices/{id}", notice).cookie(fixtures.loginCookie(staff)))
                .andExpect(jsonPath("$.comments[*].mine", contains(false, true, false)));
        mvc.perform(get("/api/v1/notices").cookie(memberLogin))
                .andExpect(jsonPath("$.items[0].commentCount").value(3));
    }

    @Test
    void 본문이_비었거나_500자를_넘으면_400이고_저장하지_않는다() throws Exception {
        for (String body : new String[] { "", "   ", "가".repeat(501) }) {
            create(memberLogin, notice, UUID.randomUUID().toString(), body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("body"));
        }
        create(memberLogin, notice, UUID.randomUUID().toString(), "가".repeat(500)).andExpect(status().isCreated());
        assertThat(commentCount()).isEqualTo(1);
    }

    @Test
    void 없거나_지운_공지에는_404() throws Exception {
        long deleted = fixtures.notice(staff.id());
        jdbc.update("UPDATE notice SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deleted);

        for (long id : new long[] { deleted, deleted + 1000 }) {
            create(memberLogin, id, UUID.randomUUID().toString(), "댓글")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
        assertThat(commentCount()).isZero();
    }

    @Test
    void 같은_Idempotency_Key로_다시_보내면_한_번만_저장한다() throws Exception {
        String key = UUID.randomUUID().toString();
        create(memberLogin, notice, key, "댓글").andExpect(status().isCreated());
        create(memberLogin, notice, key, "댓글").andExpect(status().isCreated());

        assertThat(commentCount()).isEqualTo(1);
    }

    @Test
    void 부원당_분당_5회를_넘으면_429() throws Exception {
        for (int i = 0; i < 5; i++) {
            create(memberLogin, notice, UUID.randomUUID().toString(), "댓글 " + i).andExpect(status().isCreated());
        }

        create(memberLogin, notice, UUID.randomUUID().toString(), "댓글")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        assertThat(commentCount()).isEqualTo(5);
    }

    @Test
    void 본인_댓글을_지우면_204이고_상세에서_빠진다() throws Exception {
        long comment = fixtures.noticeComment(notice, member.id());

        remove(memberLogin, comment).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT deleted_by FROM notice_comment WHERE id = ?", String.class, comment))
                .isEqualTo("AUTHOR");
        mvc.perform(get("/api/v1/notices/{id}", notice).cookie(memberLogin))
                .andExpect(jsonPath("$.comments").isEmpty());
        remove(memberLogin, comment).andExpect(status().isNotFound());
    }

    @Test
    void 남의_댓글은_운영진도_403() throws Exception {
        long comment = fixtures.noticeComment(notice, member.id());

        remove(fixtures.loginCookie(fixtures.member()), comment)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHOR"));
        remove(fixtures.loginCookie(staff), comment)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHOR"));
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM notice_comment WHERE id = ?", Boolean.class,
                comment)).isTrue();
    }

    @Test
    void 없는_댓글이나_공지가_지워진_댓글은_404를_403보다_먼저() throws Exception {
        long comment = fixtures.noticeComment(notice, staff.id());
        jdbc.update("UPDATE notice SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", notice);

        remove(memberLogin, comment).andExpect(status().isNotFound());
        remove(memberLogin, comment + 1000).andExpect(status().isNotFound());
    }

    private ResultActions create(Cookie cookie, long noticeId, String key, String body) throws Exception {
        return mvc.perform(post("/api/v1/notices/{id}/comments", noticeId).cookie(cookie).with(csrfToken())
                .header(IdempotencyAspect.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + body + "\"}"));
    }

    private ResultActions remove(Cookie cookie, long commentId) throws Exception {
        return mvc.perform(delete("/api/v1/notice-comments/{id}", commentId).cookie(cookie).with(csrfToken()));
    }

    private long commentCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notice_comment", Long.class);
    }

}
