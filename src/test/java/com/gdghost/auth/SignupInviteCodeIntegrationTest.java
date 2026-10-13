package com.gdghost.auth;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.ratelimit.RateLimiter;
import com.gdghost.common.security.TokenHasher;
import com.gdghost.support.Fixtures;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class SignupInviteCodeIntegrationTest {

    // 실패 횟수는 서버 메모리에 기기 · IP별로 쌓이므로 테스트마다 다른 기기 · IP
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RateLimiter rateLimiter;

    String ip;
    String device;

    @BeforeEach
    void setUp() {
        ip = "10.19.0." + IP_SEQUENCE.incrementAndGet();
        device = TokenHasher.newToken();
    }

    @Test
    void 유효한_코드면_가입_토큰을_주고_사용_횟수는_그대로() throws Exception {
        String code = codeOf(fixtures.inviteCode(5));

        check("  " + code.toLowerCase() + " ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signupToken").value(startsWith("st_")))
                .andExpect(jsonPath("$.generation").value(5))
                .andExpect(jsonPath("$.expiresAt").exists());

        assertThat(jdbc.queryForObject("SELECT used_count FROM invite_code", Integer.class)).isZero();
    }

    @Test
    void 없음_폐기_만료_소진은_같은_에러() throws Exception {
        long revoked = fixtures.inviteCode(5);
        long expired = fixtures.inviteCode(5);
        long exhausted = fixtures.inviteCode(5);
        jdbc.update("UPDATE invite_code SET revoked_at = UTC_TIMESTAMP(6) WHERE id = ?", revoked);
        jdbc.update("UPDATE invite_code SET expires_at = ? WHERE id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), expired);
        jdbc.update("UPDATE invite_code SET used_count = max_uses WHERE id = ?", exhausted);

        for (String code : new String[] {"NOPE2345", codeOf(revoked), codeOf(expired), codeOf(exhausted)}) {
            check(code)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVITE_CODE_INVALID"))
                    .andExpect(jsonPath("$.detail").value("쓸 수 없는 초대 코드입니다."));
        }
    }

    @Test
    void 기기_쿠키가_없으면_에러_응답에도_발급한다() throws Exception {
        device = null;

        check("NOPE2345")
                .andExpect(status().isBadRequest())
                .andExpect(cookie().exists(SignupDeviceCookie.NAME))
                .andExpect(cookie().httpOnly(SignupDeviceCookie.NAME, true));
    }

    @Test
    void 이상한_기기_쿠키는_새로_발급한다() throws Exception {
        device = "short";

        check("NOPE2345").andExpect(cookie().exists(SignupDeviceCookie.NAME));
    }

    @Test
    void 같은_기기에서_5회_틀리면_맞는_코드도_잠긴다() throws Exception {
        String code = codeOf(fixtures.inviteCode(5));
        for (int i = 0; i < 5; i++) {
            check("NOPE2345").andExpect(jsonPath("$.code").value("INVITE_CODE_INVALID"));
        }

        check(code)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_LOCKED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void 같은_와이파이여도_다른_기기는_잠기지_않는다() throws Exception {
        String code = codeOf(fixtures.inviteCode(5));
        for (int i = 0; i < 5; i++) {
            check("NOPE2345");
        }

        device = TokenHasher.newToken();
        check(code).andExpect(status().isOk());
    }

    @Test
    void 쿠키를_버리며_틀려도_같은_IP_50회면_잠긴다() throws Exception {
        String code = codeOf(fixtures.inviteCode(5));
        // 분당 20회 제한보다 많아 요청 대신 실패 기록을 직접 채운다
        for (int i = 0; i < 50; i++) {
            rateLimiter.tryAcquire(SignupService.ipLockKey(ip), 50, Duration.ofMinutes(10));
        }

        device = TokenHasher.newToken();
        check(code)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_LOCKED"));
    }

    @Test
    void 성공을_포함해_같은_IP_분당_20회를_넘으면_429() throws Exception {
        String code = codeOf(fixtures.inviteCode(5));
        for (int i = 0; i < 20; i++) {
            device = TokenHasher.newToken();
            check(code).andExpect(status().isOk());
        }

        check(code)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void 성공은_실패_횟수에_넣지_않는다() throws Exception {
        String code = codeOf(fixtures.inviteCode(5));
        for (int i = 0; i < 4; i++) {
            check("NOPE2345");
        }
        check(code).andExpect(status().isOk());
        check(code).andExpect(status().isOk());

        check("NOPE2345").andExpect(jsonPath("$.code").value("INVITE_CODE_INVALID"));
    }

    private String codeOf(long id) {
        return jdbc.queryForObject("SELECT code FROM invite_code WHERE id = ?", String.class, id);
    }

    private ResultActions check(String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/signup/invite-code")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    if (device != null) {
                        request.setCookies(new Cookie(SignupDeviceCookie.NAME, device));
                    }
                    return request;
                })
                .with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

}
