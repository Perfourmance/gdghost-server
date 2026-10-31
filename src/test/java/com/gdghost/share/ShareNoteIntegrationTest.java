package com.gdghost.share;

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

@IntegrationTest
class ShareNoteIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember author;
    TestMember member;
    Cookie authorLogin;
    Cookie login;
    long share;

    @BeforeEach
    void setUp() {
        author = fixtures.member();
        member = fixtures.member();
        authorLogin = fixtures.loginCookie(author);
        login = fixtures.loginCookie(member);
        share = fixtures.share(author.id());
    }

    private ResultActions write(Cookie cookie, long shareId, String key, String body) throws Exception {
        return mvc.perform(post("/api/v1/shares/{id}/notes", shareId).cookie(cookie).with(csrfToken())
                .header(IdempotencyAspect.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + body + "\"}"));
    }

    private ResultActions write(Cookie cookie, String body) throws Exception {
        return write(cookie, share, UUID.randomUUID().toString(), body);
    }

    private long noteId(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.id")).longValue();
    }

    private ResultActions remove(Cookie cookie, long noteId) throws Exception {
        return mvc.perform(delete("/api/v1/share-notes/{id}", noteId).cookie(cookie).with(csrfToken()));
    }

    private int notifications(long memberId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE member_id = ? AND type = 'SHARE_NOTE'",
                Integer.class, memberId);
    }

    @Test
    void 덧붙이면_상세_항목_모양을_돌려주고_글쓴이에게_알린다() throws Exception {
        long id = noteId(write(login, "  요즘 가격 올랐어요  ")
                .andExpect(jsonPath("$.body").value("요즘 가격 올랐어요"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.author.id").value(member.id()))
                .andExpect(jsonPath("$.author.nickname").value(member.nickname())));

        mvc.perform(get("/api/v1/shares/{id}", share).cookie(authorLogin))
                .andExpect(jsonPath("$.notes[*].id", contains((int) id)))
                .andExpect(jsonPath("$.notes[0].mine").value(false));
        mvc.perform(get("/api/v1/shares").cookie(authorLogin))
                .andExpect(jsonPath("$.items[0].noteCount").value(1));

        assertThat(notifications(author.id())).isEqualTo(1);
        var row = jdbc.queryForMap("""
                SELECT title, target_type, target_id, sub_target_type, sub_target_id
                FROM notification WHERE member_id = ? AND type = 'SHARE_NOTE'
                """, author.id());
        assertThat((String) row.get("title")).contains(member.nickname());
        assertThat(row.get("target_type")).isEqualTo("SHARE");
        assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(share);
        assertThat(row.get("sub_target_type")).isEqualTo("NOTE");
        assertThat(((Number) row.get("sub_target_id")).longValue()).isEqualTo(id);
    }

    @Test
    void 본인_글에_덧붙이면_알리지_않는다() throws Exception {
        write(authorLogin, "제가 덧붙입니다").andExpect(status().isCreated());
        assertThat(notifications(author.id())).isZero();
    }

    @Test
    void 같은_멱등_키는_한_번만_저장한다() throws Exception {
        String key = UUID.randomUUID().toString();
        write(login, share, key, "한 번만").andExpect(status().isCreated());
        write(login, share, key, "한 번만").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM share_note", Integer.class)).isEqualTo(1);
        assertThat(notifications(author.id())).isEqualTo(1);
    }

    @Test
    void 본문은_1에서_100자() throws Exception {
        write(login, "   ").andExpect(status().isBadRequest());
        write(login, "가".repeat(101)).andExpect(status().isBadRequest());
        write(login, "가".repeat(100)).andExpect(status().isCreated());
    }

    @Test
    void 없거나_지운_글에는_덧붙일_수_없다() throws Exception {
        write(login, 999_999, UUID.randomUUID().toString(), "내용").andExpect(status().isNotFound());
        jdbc.update("UPDATE share SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'STAFF' WHERE id = ?", share);
        write(login, "내용").andExpect(status().isNotFound());
    }

    @Test
    void 덧붙이기는_분당_5회() throws Exception {
        for (int i = 0; i < 5; i++) {
            write(login, "내용 " + i).andExpect(status().isCreated());
        }
        write(login, "내용").andExpect(status().isTooManyRequests());
    }

    @Test
    void 본인_덧붙이기만_지운다() throws Exception {
        long id = noteId(write(login, "지울 글"));

        remove(authorLogin, id).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHOR"));
        remove(fixtures.loginCookie(fixtures.staff()), id).andExpect(status().isForbidden());
        remove(login, id).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT deleted_by FROM share_note WHERE id = ?", String.class, id))
                .isEqualTo("AUTHOR");
        mvc.perform(get("/api/v1/shares/{id}", share).cookie(login)).andExpect(jsonPath("$.notes", hasSize(0)));
        remove(login, id).andExpect(status().isNotFound());
        remove(login, 999_999).andExpect(status().isNotFound());
    }

    @Test
    void 글이_지워졌으면_덧붙이기_삭제도_404() throws Exception {
        long id = noteId(write(login, "내용"));
        jdbc.update("UPDATE share SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?", share);
        remove(login, id).andExpect(status().isNotFound());
    }

    private ResultActions report(Cookie cookie, long noteId) throws Exception {
        return mvc.perform(post("/api/v1/reports").cookie(cookie).with(csrfToken())
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"SHARE_NOTE\",\"targetId\":" + noteId + ",\"reason\":\"광고\"}"));
    }

    private ResultActions handle(Cookie staff, long reportId, String action) throws Exception {
        return mvc.perform(patch("/api/v1/ops/reports/{id}", reportId).cookie(staff).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"" + action + "\"}"));
    }

    @Test
    void 신고로_숨기고_되돌린다() throws Exception {
        long id = noteId(write(login, "광고 글입니다"));
        Cookie staff = fixtures.loginCookie(fixtures.staff());
        String created = report(authorLogin, id).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long reportId = ((Number) JsonPath.read(created, "$.id")).longValue();

        handle(staff, reportId, "HIDE").andExpect(status().isOk())
                .andExpect(jsonPath("$.targetPreview").value("광고 글입니다"))
                .andExpect(jsonPath("$.targetLink").value("/shares/" + share + "#note-" + id))
                .andExpect(jsonPath("$.targetDeletedBy").value("STAFF"));
        mvc.perform(get("/api/v1/shares/{id}", share).cookie(login)).andExpect(jsonPath("$.notes", hasSize(0)));
        report(fixtures.loginCookie(fixtures.member()), id).andExpect(status().isNotFound());

        handle(staff, reportId, "RESTORE").andExpect(status().isOk());
        mvc.perform(get("/api/v1/shares/{id}", share).cookie(login)).andExpect(jsonPath("$.notes", hasSize(1)));
    }

    @Test
    void 작성자가_지운_덧붙이기는_되돌릴_수_없다() throws Exception {
        long id = noteId(write(login, "지울 글"));
        Cookie staff = fixtures.loginCookie(fixtures.staff());
        String created = report(authorLogin, id).andReturn().getResponse().getContentAsString();
        long reportId = ((Number) JsonPath.read(created, "$.id")).longValue();
        remove(login, id).andExpect(status().isNoContent());

        handle(staff, reportId, "HIDE").andExpect(status().isOk());
        handle(staff, reportId, "RESTORE").andExpect(status().isConflict());
    }

}
