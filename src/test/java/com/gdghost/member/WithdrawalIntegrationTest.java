package com.gdghost.member;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.security.SessionCookie;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/** 탈퇴 */
@IntegrationTest
class WithdrawalIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember member;
    Cookie login;

    @BeforeEach
    void setUp() {
        member = fixtures.member();
        login = fixtures.loginCookie(member);
    }

    @Test
    void 개인정보를_비우고_모든_기기에서_로그아웃한다() throws Exception {
        Cookie laptop = fixtures.loginCookie(member);
        jdbc.update("""
                UPDATE member SET student_no = '25', bio = '소개', status_note = '근황',
                                  status_note_updated_at = UTC_TIMESTAMP(6), accept_coffee = TRUE, coffee_memo = '메모'
                WHERE id = ?
                """, member.id());
        jdbc.update("INSERT INTO member_contact (member_id, type, value, is_public) VALUES (?, 'KAKAO', 'k', TRUE)",
                member.id());
        jdbc.update("INSERT INTO member_tag (member_id, tag_id) SELECT ?, id FROM tag WHERE kind = 'INTEREST' LIMIT 1",
                member.id());
        jdbc.update("INSERT INTO member_embedding (member_id, vector, model, source_hash) VALUES (?, '[]', 'm', ?)",
                member.id(), "0".repeat(64));
        jdbc.update("""
                INSERT INTO push_subscription (member_id, endpoint, endpoint_hash, p256dh, auth)
                VALUES (?, 'https://push', ?, 'p', 'a')
                """, member.id(), "1".repeat(64));

        withdraw(Fixtures.PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(SessionCookie.NAME, 0));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM member WHERE id = ?", member.id());
        assertThat(row.get("withdrawn_at")).isNotNull();
        for (String column : List.of("login_id", "email", "nickname", "name", "student_no", "bio", "status_note",
                "status_note_updated_at", "coffee_memo")) {
            assertThat(row.get(column)).as(column).isNull();
        }
        assertThat(row.get("accept_coffee")).isEqualTo(false);
        for (String table : List.of("member_contact", "member_tag", "member_embedding", "push_subscription",
                "login_token")) {
            assertThat(count(table, "member_id")).as(table).isZero();
        }
        mvc.perform(get("/api/v1/auth/me").cookie(laptop)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/members/{id}", member.id()).cookie(fixtures.loginCookie(fixtures.member())))
                .andExpect(status().isNotFound());
    }

    @Test
    void 아이디_이메일_닉네임은_다른_사람이_다시_쓸_수_있다() throws Exception {
        withdraw(Fixtures.PASSWORD).andExpect(status().isNoContent());

        // UNIQUE 위반이면 예외
        assertThat(jdbc.update("UPDATE member SET login_id = ?, email = ?, nickname = ? WHERE id = ?",
                member.loginId(), member.loginId() + "@test.gdghost", member.nickname(), fixtures.member().id()))
                .isOne();
    }

    @Test
    void 질문_답변_피드_댓글은_남기고_피드는_지운다() throws Exception {
        TestMember other = fixtures.member();
        long question = fixtures.question(member.id(), false);
        fixtures.answer(fixtures.question(other.id(), false), member.id(), false, null);
        long myFeed = feedPost(member.id());
        long otherFeed = feedPost(other.id());
        jdbc.update("INSERT INTO feed_comment (post_id, author_id, body) VALUES (?, ?, '댓글')", otherFeed, member.id());

        withdraw(Fixtures.PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM question WHERE id = ?", Boolean.class,
                question)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer WHERE author_id = ? AND deleted_at IS NULL",
                Integer.class, member.id())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_comment WHERE author_id = ? AND deleted_at IS NULL",
                Integer.class, member.id())).isOne();
        assertThat(jdbc.queryForMap("SELECT deleted_at, deleted_by FROM feed_post WHERE id = ?", myFeed))
                .containsEntry("deleted_by", "AUTHOR")
                .extractingByKey("deleted_at").isNotNull();
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM feed_post WHERE id = ?", Boolean.class,
                otherFeed)).isTrue();
    }

    @Test
    void 이메일_기록의_주소를_비운다() throws Exception {
        String email = member.loginId() + "@test.gdghost";
        jdbc.update("""
                INSERT INTO email_verification (email, member_id, code_hash, purpose, expires_at)
                VALUES (?, NULL, 'h', 'SIGNUP', UTC_TIMESTAMP(6)), ('old@test.gdghost', ?, 'h', 'EMAIL_CHANGE', UTC_TIMESTAMP(6)),
                       ('other@test.gdghost', NULL, 'h', 'SIGNUP', UTC_TIMESTAMP(6))
                """, email, member.id());
        jdbc.update("""
                INSERT INTO email_change_log (member_id, old_email, new_email, changed_by_type, changed_by)
                VALUES (?, 'old@test.gdghost', ?, 'SELF', ?)
                """, member.id(), email, member.id());

        withdraw(Fixtures.PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbc.queryForList("SELECT email FROM email_verification WHERE email IS NOT NULL", String.class))
                .containsExactly("other@test.gdghost");
        assertThat(jdbc.queryForObject("""
                SELECT old_email IS NULL AND new_email IS NULL FROM email_change_log WHERE member_id = ?
                """, Boolean.class, member.id())).isTrue();
    }

    @Test
    void 응답_대기_커피챗과_보내지_않은_라우팅은_정리한다() throws Exception {
        TestMember other = fixtures.member();
        long sent = coffeeChat(member.id(), other.id(), "REQUESTED");
        long received = coffeeChat(other.id(), member.id(), "REQUESTED");
        long accepted = coffeeChat(other.id(), member.id(), "ACCEPTED");
        long question = fixtures.question(other.id(), false);
        long candidate = routing(question, member.id(), "CANDIDATE");
        long delivered = routing(question, member.id(), "SENT");

        withdraw(Fixtures.PASSWORD).andExpect(status().isNoContent());

        assertThat(state("coffee_chat", sent)).isEqualTo("EXPIRED");
        assertThat(state("coffee_chat", received)).isEqualTo("EXPIRED");
        assertThat(state("coffee_chat", accepted)).isEqualTo("ACCEPTED");
        assertThat(state("routing", candidate)).isEqualTo("CANCELED");
        assertThat(state("routing", delivered)).isEqualTo("SENT");
    }

    @Test
    void 비밀번호가_틀리면_400이고_아무것도_안_바뀐다() throws Exception {
        withdraw("wrong1234!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_WRONG"));
        withdraw("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NULL FROM member WHERE id = ?", Boolean.class,
                member.id())).isTrue();
        mvc.perform(get("/api/v1/auth/me").cookie(login)).andExpect(status().isOk());
    }

    @Test
    void 혼자_남은_운영진은_탈퇴할_수_없다() throws Exception {
        TestMember staff = fixtures.staff();
        login = fixtures.loginCookie(staff);
        fixtures.withdrawn();
        jdbc.update("UPDATE member SET role = 'STAFF' WHERE withdrawn_at IS NOT NULL");

        withdraw(Fixtures.PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_STAFF"));

        fixtures.staff();
        withdraw(Fixtures.PASSWORD).andExpect(status().isNoContent());
    }

    private ResultActions withdraw(String password) throws Exception {
        return mvc.perform(post("/api/v1/me/withdrawal").cookie(login).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}"));
    }

    private int count(String table, String column) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class,
                member.id());
    }

    private String state(String table, long id) {
        return jdbc.queryForObject("SELECT state FROM " + table + " WHERE id = ?", String.class, id);
    }

    private long feedPost(long authorId) {
        jdbc.update("INSERT INTO feed_post (author_id, category, body) VALUES (?, 'DAILY', '피드')", authorId);
        return jdbc.queryForObject("SELECT MAX(id) FROM feed_post WHERE author_id = ?", Long.class, authorId);
    }

    private long coffeeChat(long requesterId, long receiverId, String state) {
        jdbc.update("INSERT INTO coffee_chat (requester_id, receiver_id, purpose, state) VALUES (?, ?, '목적', ?)",
                requesterId, receiverId, state);
        return jdbc.queryForObject("SELECT MAX(id) FROM coffee_chat", Long.class);
    }

    private long routing(long questionId, long memberId, String state) {
        jdbc.update("INSERT INTO routing (question_id, member_id, stage, state) VALUES (?, ?, 'MEMBER', ?)",
                questionId, memberId, state);
        return jdbc.queryForObject("SELECT MAX(id) FROM routing", Long.class);
    }

}
