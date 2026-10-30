package com.gdghost.notice;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** 공지 목록 · 상세 · 확인했어요 · 홈 공지 묶음 */
@IntegrationTest
class NoticeIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember staff;
    TestMember member;
    Cookie memberLogin;

    @BeforeEach
    void setUp() {
        staff = fixtures.staff();
        jdbc.update("UPDATE member SET staff_title = '회장' WHERE id = ?", staff.id());
        member = fixtures.member();
        memberLogin = fixtures.loginCookie(member);
    }

    @Test
    void 목록은_고정_먼저_그_안에서_최신순이고_읽음_확인은_보는_사람_기준이다() throws Exception {
        long oldPinned = pinned(fixtures.notice(staff.id()));
        long plain = fixtures.notice(staff.id());
        long newPinned = pinned(fixtures.notice(staff.id()));
        long newest = fixtures.notice(staff.id());
        long deleted = fixtures.notice(staff.id());
        jdbc.update("UPDATE notice SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deleted);
        jdbc.update("INSERT INTO notice_read (notice_id, member_id) VALUES (?, ?)", plain, member.id());
        jdbc.update("INSERT INTO notice_read (notice_id, member_id, confirmed_at) VALUES (?, ?, UTC_TIMESTAMP(6))",
                newPinned, member.id());
        jdbc.update("INSERT INTO notice_read (notice_id, member_id, confirmed_at) VALUES (?, ?, UTC_TIMESTAMP(6))",
                newest, staff.id());
        comment(plain, member.id(), false);
        comment(plain, member.id(), false);
        comment(plain, member.id(), true);

        String body = mvc.perform(get("/api/v1/notices").cookie(memberLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id",
                        contains((int) newPinned, (int) oldPinned, (int) newest, (int) plain)))
                .andExpect(jsonPath("$.items[0].isPinned").value(true))
                .andExpect(jsonPath("$.items[0].read").value(true))
                .andExpect(jsonPath("$.items[0].confirmed").value(true))
                .andExpect(jsonPath("$.items[1].read").value(false))
                .andExpect(jsonPath("$.items[2].read").value(false))
                .andExpect(jsonPath("$.items[2].confirmed").value(false))
                .andExpect(jsonPath("$.items[3].read").value(true))
                .andExpect(jsonPath("$.items[3].confirmed").value(false))
                .andExpect(jsonPath("$.items[3].commentCount").value(2))
                .andExpect(jsonPath("$.items[3].author.id").value(staff.id()))
                .andExpect(jsonPath("$.items[3].author.staffTitle").value("회장"))
                .andExpect(jsonPath("$.items[3].body").doesNotExist())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat((Object) JsonPath.read(body, "$.nextCursor")).isNull();
    }

    @Test
    void 커서로_고정과_일반의_경계를_넘어_이어_읽는다() throws Exception {
        long plain1 = fixtures.notice(staff.id());
        long pinned1 = pinned(fixtures.notice(staff.id()));
        long plain2 = fixtures.notice(staff.id());
        long pinned2 = pinned(fixtures.notice(staff.id()));
        long plain3 = fixtures.notice(staff.id());

        String first = mvc.perform(get("/api/v1/notices").param("size", "3").cookie(memberLogin))
                .andExpect(jsonPath("$.items[*].id", contains((int) pinned2, (int) pinned1, (int) plain3)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/v1/notices").param("size", "3")
                        .param("cursor", JsonPath.<String>read(first, "$.nextCursor")).cookie(memberLogin))
                .andExpect(jsonPath("$.items[*].id", contains((int) plain2, (int) plain1)))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void 해석할_수_없는_커서는_400() throws Exception {
        for (String cursor : List.of("!!!", "MTI", "Mjo1", "MTph")) { // 깨진 base64 · "12" · "2:5" · "1:a"
            mvc.perform(get("/api/v1/notices").param("cursor", cursor).cookie(memberLogin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("cursor"));
        }
    }

    @Test
    void 상세를_열면_읽음으로_남고_다시_열어도_처음_시각을_유지한다() throws Exception {
        long notice = fixtures.notice(staff.id());

        mvc.perform(get("/api/v1/notices/{id}", notice).cookie(memberLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notice))
                .andExpect(jsonPath("$.body").exists())
                .andExpect(jsonPath("$.author.staffTitle").value("회장"))
                .andExpect(jsonPath("$.confirmed").value(false))
                .andExpect(jsonPath("$.comments").isEmpty())
                .andExpect(jsonPath("$.stats").isEmpty());
        Timestamp firstRead = readAt(notice, member.id());
        assertThat(firstRead).isNotNull();
        assertThat(confirmedAt(notice, member.id())).isNull();

        mvc.perform(get("/api/v1/notices/{id}", notice).cookie(memberLogin)).andExpect(status().isOk());
        assertThat(readAt(notice, member.id())).isEqualTo(firstRead);
    }

    @Test
    void 운영진과_개발자는_상세에서_확인_집계를_받는다() throws Exception {
        long notice = fixtures.notice(staff.id());
        mvc.perform(put("/api/v1/notices/{id}/confirmation", notice).with(csrfToken()).cookie(memberLogin));

        mvc.perform(get("/api/v1/notices/{id}", notice).cookie(fixtures.loginCookie(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.memberCount").value(2))
                .andExpect(jsonPath("$.stats.readCount").value(2))
                .andExpect(jsonPath("$.stats.confirmedCount").value(1));
        mvc.perform(get("/api/v1/notices/{id}", notice).cookie(fixtures.loginCookie(fixtures.dev())))
                .andExpect(jsonPath("$.stats.memberCount").value(3))
                .andExpect(jsonPath("$.stats.readCount").value(3));
    }

    @Test
    void 없거나_지운_공지의_상세와_확인은_404이고_읽음을_남기지_않는다() throws Exception {
        long deleted = fixtures.notice(staff.id());
        jdbc.update("UPDATE notice SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deleted);

        for (long id : new long[] { deleted, deleted + 1000 }) {
            mvc.perform(get("/api/v1/notices/{id}", id).cookie(memberLogin))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
            mvc.perform(put("/api/v1/notices/{id}/confirmation", id).with(csrfToken()).cookie(memberLogin))
                    .andExpect(status().isNotFound());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notice_read", Long.class)).isZero();
    }

    @Test
    void 확인했어요는_읽음도_남기고_다시_눌러도_처음_시각_그대로다() throws Exception {
        long notice = fixtures.notice(staff.id());

        String first = mvc.perform(put("/api/v1/notices/{id}/confirmation", notice).with(csrfToken())
                        .cookie(memberLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(true))
                .andExpect(jsonPath("$.confirmedAt").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(readAt(notice, member.id())).isNotNull();

        mvc.perform(put("/api/v1/notices/{id}/confirmation", notice).with(csrfToken()).cookie(memberLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedAt").value(JsonPath.<String>read(first, "$.confirmedAt")));
        mvc.perform(get("/api/v1/notices/{id}", notice).cookie(memberLogin))
                .andExpect(jsonPath("$.confirmed").value(true));
    }

    @Test
    void 먼저_읽고_나서_확인하면_읽은_시각은_그대로_두고_확인만_더한다() throws Exception {
        long notice = fixtures.notice(staff.id());
        mvc.perform(get("/api/v1/notices/{id}", notice).cookie(memberLogin));
        jdbc.update("UPDATE notice_read SET read_at = read_at - INTERVAL 1 HOUR WHERE notice_id = ?", notice);
        Timestamp read = readAt(notice, member.id());

        mvc.perform(put("/api/v1/notices/{id}/confirmation", notice).with(csrfToken()).cookie(memberLogin))
                .andExpect(status().isOk());

        assertThat(readAt(notice, member.id())).isEqualTo(read);
        assertThat(confirmedAt(notice, member.id())).isAfter(read);
    }

    @Test
    void 홈은_고정_공지_최신_5개와_마감_전_미확인_필독_수를_준다() throws Exception {
        long[] pinned = new long[6];
        for (int i = 0; i < pinned.length; i++) {
            pinned[i] = pinned(fixtures.notice(staff.id()));
        }
        fixtures.notice(staff.id()); // 고정 안 함
        long deletedPinned = pinned(fixtures.notice(staff.id()));
        jdbc.update("UPDATE notice SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deletedPinned);
        jdbc.update("INSERT INTO notice_read (notice_id, member_id) VALUES (?, ?)", pinned[5], member.id());

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        mustRead(pinned[5], null); // 마감 없음 → 셈
        mustRead(pinned[4], today); // 오늘 마감 → 셈
        mustRead(pinned[3], today.minusDays(1)); // 마감 지남 → 안 셈
        mustRead(pinned[2], today.plusDays(3)); // 확인함 → 안 셈
        jdbc.update("INSERT INTO notice_read (notice_id, member_id, confirmed_at) VALUES (?, ?, UTC_TIMESTAMP(6))",
                pinned[2], member.id());
        mustRead(deletedPinned, null); // 지움 → 안 셈

        mvc.perform(get("/api/v1/home").cookie(memberLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinnedNotices[*].id", contains((int) pinned[5], (int) pinned[4],
                        (int) pinned[3], (int) pinned[2], (int) pinned[1])))
                .andExpect(jsonPath("$.pinnedNotices[0].read").value(true))
                .andExpect(jsonPath("$.pinnedNotices[0].isMustRead").value(true))
                .andExpect(jsonPath("$.pinnedNotices[1].read").value(false))
                .andExpect(jsonPath("$.pinnedNotices[1].dueDate").value(today.toString()))
                .andExpect(jsonPath("$.unconfirmedMustReadCount").value(2));

        mvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinnedNotices[*].id", contains((int) pinned[5], (int) pinned[4],
                        (int) pinned[3], (int) pinned[2], (int) pinned[1])))
                .andExpect(jsonPath("$.pinnedNotices[0].title").exists())
                .andExpect(jsonPath("$.pinnedNotices[0].isMustRead").doesNotExist())
                .andExpect(jsonPath("$.pinnedNotices[0].read").doesNotExist());
    }

    private long pinned(long noticeId) {
        jdbc.update("UPDATE notice SET is_pinned = TRUE WHERE id = ?", noticeId);
        return noticeId;
    }

    private void mustRead(long noticeId, LocalDate dueDate) {
        jdbc.update("UPDATE notice SET is_must_read = TRUE, due_date = ? WHERE id = ?", dueDate, noticeId);
    }

    private void comment(long noticeId, long authorId, boolean deleted) {
        jdbc.update("INSERT INTO notice_comment (notice_id, author_id, body, deleted_at) VALUES (?, ?, '댓글', "
                + (deleted ? "UTC_TIMESTAMP(6)" : "NULL") + ")", noticeId, authorId);
    }

    private Timestamp readAt(long noticeId, long memberId) {
        return readColumn("read_at", noticeId, memberId);
    }

    private Timestamp confirmedAt(long noticeId, long memberId) {
        return readColumn("confirmed_at", noticeId, memberId);
    }

    /** 행이 없어도 null */
    private Timestamp readColumn(String column, long noticeId, long memberId) {
        List<Timestamp> values = jdbc.query("SELECT " + column + " FROM notice_read WHERE notice_id = ? AND member_id = ?",
                (rs, i) -> rs.getTimestamp(1), noticeId, memberId);
        return values.isEmpty() ? null : values.getFirst();
    }

}
