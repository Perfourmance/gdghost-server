package com.gdghost.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.gdghost.common.config.OpenApiConfig;

import jakarta.servlet.http.Cookie;

/**
 * 테스트 데이터. 엔티티가 바뀌어도 테스트가 덜 흔들리게 JDBC로 직접 넣는다.
 */
public class Fixtures {

    public static final String PASSWORD = "password1234!";

    // BCrypt는 한 번에 ~100ms라 미리 한 번만 계산
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final SecureRandom RANDOM = new SecureRandom();

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

    /**
     * 로그인 상태 쿠키. 쿠키에는 원래 토큰, DB에는 SHA-256 해시만 둔다.
     */
    public Cookie loginCookie(TestMember member) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        jdbc.update("""
                INSERT INTO login_token (member_id, token_hash, remember, expires_at, last_used_at)
                VALUES (?, ?, TRUE, UTC_TIMESTAMP(6) + INTERVAL 30 DAY, UTC_TIMESTAMP(6))
                """, member.id(), sha256(token));
        return new Cookie(OpenApiConfig.SESSION_COOKIE, token);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
