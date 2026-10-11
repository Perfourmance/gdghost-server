package com.gdghost.common.security;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * login_token 발급 · 확인 · 폐기. 로그인 · 로그아웃 API(M2)와 인증 필터가 쓴다.
 */
@Service
public class LoginTokenService {

    static final Duration LIFETIME = Duration.ofDays(30);
    static final Duration EXTEND_INTERVAL = Duration.ofHours(24);

    private final JdbcTemplate jdbc;

    public LoginTokenService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Session(AuthMember member, boolean remember, boolean extended) {
    }

    private record Row(long id, long memberId, Role role, boolean remember,
                       LocalDateTime expiresAt, LocalDateTime lastUsedAt, boolean blocked) {
    }

    /** @return 쿠키에 넣을 토큰 원문 */
    public String issue(long memberId, boolean remember, String userAgent) {
        String token = TokenHasher.newToken();
        LocalDateTime now = now();
        jdbc.update("""
                INSERT INTO login_token (member_id, token_hash, remember, expires_at, last_used_at, user_agent)
                VALUES (?, ?, ?, ?, ?, ?)
                """, memberId, TokenHasher.sha256(token), remember, now.plus(LIFETIME), now, truncate(userAgent));
        return token;
    }

    public void revoke(String token) {
        jdbc.update("DELETE FROM login_token WHERE token_hash = ?", TokenHasher.sha256(token));
    }

    /**
     * 만료 · 정지 · 탈퇴면 토큰을 지우고 empty.
     * 마지막 연장(last_used_at)에서 24시간이 지났을 때만 연장 — 매 요청 DB 쓰기 방지.
     */
    public Optional<Session> authenticate(String token) {
        Optional<Row> found = jdbc.query("""
                SELECT t.id, t.member_id, t.remember, t.expires_at, t.last_used_at, m.role,
                       (m.suspended_at IS NOT NULL OR m.withdrawn_at IS NOT NULL) AS blocked
                FROM login_token t
                JOIN member m ON m.id = t.member_id
                WHERE t.token_hash = ?
                """, (rs, i) -> new Row(
                rs.getLong("id"),
                rs.getLong("member_id"),
                Role.valueOf(rs.getString("role")),
                rs.getBoolean("remember"),
                rs.getObject("expires_at", LocalDateTime.class),
                rs.getObject("last_used_at", LocalDateTime.class),
                rs.getBoolean("blocked")), TokenHasher.sha256(token)).stream().findFirst();
        if (found.isEmpty()) {
            return Optional.empty();
        }

        Row row = found.get();
        LocalDateTime now = now();
        if (row.blocked() || !row.expiresAt().isAfter(now)) {
            jdbc.update("DELETE FROM login_token WHERE id = ?", row.id());
            return Optional.empty();
        }

        boolean extend = !row.lastUsedAt().plus(EXTEND_INTERVAL).isAfter(now);
        if (extend) {
            jdbc.update("UPDATE login_token SET expires_at = ?, last_used_at = ? WHERE id = ?",
                    now.plus(LIFETIME), now, row.id());
        }
        return Optional.of(new Session(new AuthMember(row.memberId(), row.role()), row.remember(), extend));
    }

    // DB 시각은 UTC
    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static String truncate(String userAgent) {
        return userAgent == null || userAgent.length() <= 255 ? userAgent : userAgent.substring(0, 255);
    }

}
