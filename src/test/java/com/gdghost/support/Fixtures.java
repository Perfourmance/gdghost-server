package com.gdghost.support;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.gdghost.common.security.SessionCookie;
import com.gdghost.common.security.TokenHasher;

import jakarta.servlet.http.Cookie;

/**
 * 테스트 데이터. 엔티티가 바뀌어도 테스트가 덜 흔들리게 JDBC로 직접 넣는다.
 */
public class Fixtures {

    public static final String PASSWORD = "password1234!";

    // BCrypt는 한 번에 ~100ms라 미리 한 번만 계산
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert memberInsert;

    public Fixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.memberInsert = new SimpleJdbcInsert(jdbc)
                .withTableName("member")
                // 지정하지 않으면 모든 컬럼에 NULL을 넣어 DB 기본값(accept_question 등)을 덮어씀
                .usingColumns("login_id", "password_hash", "email", "name", "nickname",
                        "generation", "position", "status", "role")
                .usingGeneratedKeyColumns("id");
    }

    public record TestMember(long id, String loginId, String nickname) {
    }

    public TestMember member() {
        return insertMember("MEMBER");
    }

    public TestMember staff() {
        return insertMember("STAFF");
    }

    public TestMember dev() {
        return insertMember("DEV");
    }

    public TestMember suspended() {
        TestMember member = member();
        jdbc.update("""
                UPDATE member SET suspended_at = UTC_TIMESTAMP(6), suspended_reason = '테스트'
                WHERE id = ?
                """, member.id());
        return member;
    }

    /** 탈퇴하면 UNIQUE 컬럼(login_id · email · nickname)을 비운다 — ERD 규칙 */
    public TestMember withdrawn() {
        TestMember member = member();
        jdbc.update("""
                UPDATE member SET login_id = NULL, email = NULL, nickname = NULL, name = NULL,
                                  withdrawn_at = UTC_TIMESTAMP(6)
                WHERE id = ?
                """, member.id());
        return new TestMember(member.id(), null, null);
    }

    /** 쓸 수 있는 초대 코드 (만료 30일 뒤 · 10회) */
    public long inviteCode(int generation) {
        int n = SEQUENCE.incrementAndGet();
        return new SimpleJdbcInsert(jdbc)
                .withTableName("invite_code")
                .usingColumns("code", "generation", "max_uses", "expires_at")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(Map.of(
                        "code", "TEST" + n,
                        "generation", generation,
                        "max_uses", 10,
                        "expires_at", LocalDateTime.now(ZoneOffset.UTC).plusDays(30)))
                .longValue();
    }

    /** 질문 (카테고리 JOB, 제목 '질문 N') */
    public long question(long authorId, boolean anonymous) {
        int n = SEQUENCE.incrementAndGet();
        return new SimpleJdbcInsert(jdbc)
                .withTableName("question")
                .usingColumns("author_id", "category", "title", "body", "is_anonymous")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(Map.of(
                        "author_id", authorId,
                        "category", "JOB",
                        "title", "질문 " + n,
                        "body", "질문 본문 " + n,
                        "is_anonymous", anonymous))
                .longValue();
    }

    /** 답변. parentId가 있으면 답글 */
    public long answer(long questionId, long authorId, boolean anonymous, Long parentId) {
        Map<String, Object> values = new HashMap<>();
        values.put("question_id", questionId);
        values.put("parent_id", parentId);
        values.put("depth", parentId == null ? 0 : 1);
        values.put("author_id", authorId);
        values.put("body", "답변 본문");
        values.put("is_anonymous", anonymous);
        return new SimpleJdbcInsert(jdbc)
                .withTableName("answer")
                .usingColumns("question_id", "parent_id", "depth", "author_id", "body", "is_anonymous")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(values)
                .longValue();
    }

    /** 공지 (제목 '공지 N'). 고정 · 필독 없이 상시 (DB 기본값) */
    public long notice(long authorId) {
        int n = SEQUENCE.incrementAndGet();
        return new SimpleJdbcInsert(jdbc)
                .withTableName("notice")
                .usingColumns("author_id", "title", "body")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(Map.of(
                        "author_id", authorId,
                        "title", "공지 " + n,
                        "body", "공지 본문 " + n))
                .longValue();
    }

    /** 공지 댓글 (본문 '공지 댓글 N') */
    public long noticeComment(long noticeId, long authorId) {
        int n = SEQUENCE.incrementAndGet();
        return new SimpleJdbcInsert(jdbc)
                .withTableName("notice_comment")
                .usingColumns("notice_id", "author_id", "body")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(Map.of(
                        "notice_id", noticeId,
                        "author_id", authorId,
                        "body", "공지 댓글 " + n))
                .longValue();
    }

    /** OPEN 신고 (사유 '신고 사유') */
    public long report(long reporterId, String targetType, long targetId) {
        return new SimpleJdbcInsert(jdbc)
                .withTableName("report")
                .usingColumns("reporter_id", "target_type", "target_id", "reason")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(Map.of(
                        "reporter_id", reporterId,
                        "target_type", targetType,
                        "target_id", targetId,
                        "reason", "신고 사유"))
                .longValue();
    }

    /**
     * 로그인 상태 쿠키. 쿠키에는 원래 토큰, DB에는 SHA-256 해시만 둔다.
     */
    public Cookie loginCookie(TestMember member) {
        String token = TokenHasher.newToken();
        jdbc.update("""
                INSERT INTO login_token (member_id, token_hash, remember, expires_at, last_used_at)
                VALUES (?, ?, TRUE, UTC_TIMESTAMP(6) + INTERVAL 30 DAY, UTC_TIMESTAMP(6))
                """, member.id(), TokenHasher.sha256(token));
        return new Cookie(SessionCookie.NAME, token);
    }

    private TestMember insertMember(String role) {
        int n = SEQUENCE.incrementAndGet();
        String loginId = "user" + n;
        String nickname = "부원" + n;
        Number id = memberInsert.executeAndReturnKey(Map.of(
                "login_id", loginId,
                "password_hash", PASSWORD_HASH,
                "email", loginId + "@test.gdghost",
                "name", "테스트" + n,
                "nickname", nickname,
                "generation", 5,
                "position", "DEV",
                "status", "ENROLLED",
                "role", role));
        return new TestMember(id.longValue(), loginId, nickname);
    }

}
