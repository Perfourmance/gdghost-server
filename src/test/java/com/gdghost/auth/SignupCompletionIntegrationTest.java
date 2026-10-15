package com.gdghost.auth;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.gdghost.common.security.SessionCookie;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class SignupCompletionIntegrationTest {

    private static final String EMAIL = "serim@gmail.com";
    // 요청 제한(IP당 시간당 10회)이 테스트끼리 섞이지 않게
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SignupSessionStore sessions;

    @Autowired
    PasswordEncoder passwordEncoder;

    String ip;
    long codeId;
    String token;
    Body body;

    @BeforeEach
    void setUp() {
        ip = "10.20.0." + IP_SEQUENCE.incrementAndGet();
        codeId = fixtures.inviteCode(7);
        token = sessions.open(codeId, 7).token();
        sessions.markVerified(token, EMAIL);
        body = new Body();
        body.interests = List.of(tag("INTEREST", "백엔드"));
        body.skills = List.of(tag("SKILL", "Spring"));
        body.experiences = List.of(tag("EXPERIENCE", "인턴"));
        body.statusNote = "백엔드 인턴 근무 중";
        body.bio = "취업 준비 과정을 이야기해 드릴 수 있어요";
    }

    @Test
    void 계정을_만들고_바로_로그인한다() throws Exception {
        MvcResult result = signup()
                .andExpect(status().isCreated())
                .andExpect(cookie().exists(SessionCookie.NAME))
                .andExpect(cookie().maxAge(SessionCookie.NAME, 30 * 24 * 60 * 60))
                .andExpect(jsonPath("$.loginId").value("serim01"))
                .andExpect(jsonPath("$.nickname").value("세림"))
                .andExpect(jsonPath("$.generation").value(7))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.profileCompleteness.filled").value(5))
                .andExpect(jsonPath("$.profileCompleteness.total").value(6))
                .andReturn();

        Map<String, Object> member = jdbc.queryForMap("SELECT * FROM member WHERE login_id = 'serim01'");
        assertThat(member).containsEntry("email", EMAIL)
                .containsEntry("generation", 7)
                .containsEntry("invite_code_id", codeId)
                .containsEntry("role", "MEMBER")
                .containsEntry("accept_question", true)
                .containsEntry("accept_coffee", false);
        assertThat(member.get("status_note_updated_at")).isNotNull();
        assertThat(passwordEncoder.matches("gdgoc1234!", (String) member.get("password_hash"))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_tag WHERE member_id = ?", Integer.class,
                member.get("id"))).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT used_count FROM invite_code WHERE id = ?", Integer.class, codeId))
                .isEqualTo(1);

        // 받은 쿠키로 로그인 상태 — 자기 닉네임은 사용 가능으로 나온다
        Cookie login = result.getResponse().getCookie(SessionCookie.NAME);
        mvc.perform(get("/api/v1/auth/nicknames/availability").param("nickname", "세림").cookie(login))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void 다른_계정으로_로그인돼_있던_기기면_예전_토큰을_지운다() throws Exception {
        TestMember other = fixtures.member();
        Cookie previous = fixtures.loginCookie(other);

        mvc.perform(signupRequest().cookie(previous)).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_token WHERE member_id = ?", Integer.class,
                other.id())).isZero();
    }

    @Test
    void 필수만_넣으면_완성도_1() throws Exception {
        body.skills = null;
        body.experiences = null;
        body.statusNote = null;
        body.bio = "  ";

        signup()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profileCompleteness.filled").value(1));
        assertThat(jdbc.queryForMap("SELECT bio, status_note_updated_at FROM member"))
                .containsEntry("bio", null)
                .containsEntry("status_note_updated_at", null);
    }

    @Test
    void 같은_가입_토큰으로_두_번_가입할_수_없다() throws Exception {
        signup().andExpect(status().isCreated());

        assertThatThrownBy(() -> sessions.get(token)).hasMessageContaining("가입 시간");
    }

    @Test
    void 이메일_인증_전이면_400() throws Exception {
        token = sessions.open(codeId, 7).token();

        signup()
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SIGNUP_SESSION_EXPIRED"));
    }

    @Test
    void 그사이_코드가_폐기되거나_소진되면_400이고_아무것도_남지_않는다() throws Exception {
        jdbc.update("UPDATE invite_code SET revoked_at = UTC_TIMESTAMP(6) WHERE id = ?", codeId);
        signup().andExpect(jsonPath("$.code").value("INVITE_CODE_INVALID"));

        jdbc.update("UPDATE invite_code SET revoked_at = NULL, used_count = max_uses WHERE id = ?", codeId);
        signup().andExpect(jsonPath("$.code").value("INVITE_CODE_INVALID"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE login_id = 'serim01'", Integer.class))
                .isZero();
    }

    @Test
    void 아이디_닉네임_이메일이_이미_있으면_409이고_사용_횟수는_그대로() throws Exception {
        TestMember other = fixtures.member();

        body.loginId = other.loginId().toUpperCase();
        signup().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOGIN_ID_TAKEN"));

        body.loginId = "serim01";
        body.nickname = other.nickname();
        signup().andExpect(jsonPath("$.code").value("NICKNAME_TAKEN"));

        body.nickname = "세림";
        jdbc.update("UPDATE member SET email = ? WHERE id = ?", EMAIL, other.id());
        signup().andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));

        assertThat(jdbc.queryForObject("SELECT used_count FROM invite_code WHERE id = ?", Integer.class, codeId))
                .isZero();
    }

    @Test
    void 익명으로_시작하는_닉네임은_400() throws Exception {
        body.nickname = "익명이";

        signup().andExpect(jsonPath("$.code").value("NICKNAME_RESERVED"));
    }

    @Test
    void 비밀번호에_특수문자가_없으면_400() throws Exception {
        body.password = "gdgoc12345";

        signup()
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY"));
    }

    @Test
    void 고르지_않은_분야의_세부_기술은_400() throws Exception {
        body.skills = List.of(tag("SKILL", "React"));

        signup()
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("profile.skillTagIds"));
    }

    @Test
    void 세부_기술이_분야당_3개를_넘으면_400() throws Exception {
        body.skills = List.of(tag("SKILL", "Spring"), tag("SKILL", "Node.js"), tag("SKILL", "Go"),
                tag("SKILL", "테스트"));

        signup()
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("profile.skillTagIds"));
    }

    @Test
    void 관심_분야가_없거나_5개거나_다른_종류면_400() throws Exception {
        body.skills = List.of();
        for (List<Long> interests : List.of(List.<Long>of(),
                jdbc.queryForList("SELECT id FROM tag WHERE kind = 'INTEREST' LIMIT 5", Long.class),
                List.of(tag("EXPERIENCE", "인턴")))) {
            body.interests = interests;
            signup()
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("profile.interestTagIds"));
        }
    }

    @Test
    void 같은_태그를_두_번_보내면_400() throws Exception {
        long spring = tag("SKILL", "Spring");
        body.skills = List.of(spring, spring);

        signup().andExpect(jsonPath("$.errors[0].field").value("profile.skillTagIds"));
    }

    private long tag(String kind, String name) {
        return jdbc.queryForObject("SELECT id FROM tag WHERE kind = ? AND name = ? ORDER BY id LIMIT 1", Long.class,
                kind, name);
    }

    private ResultActions signup() throws Exception {
        return mvc.perform(signupRequest());
    }

    private MockHttpServletRequestBuilder signupRequest() {
        return post("/api/v1/auth/signup")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.json(token));
    }

    static class Body {

        String loginId = "serim01";
        String password = "gdgoc1234!";
        String nickname = "세림";
        List<Long> interests;
        List<Long> skills;
        List<Long> experiences;
        String statusNote;
        String bio;

        String json(String token) {
            return """
                    {"signupToken": "%s", "loginId": "%s", "password": "%s",
                     "profile": {"name": "오세림", "nickname": "%s", "position": "DEV", "studentNo": "24",
                                 "department": "컴퓨터과학과", "status": "ENROLLED",
                                 "interestTagIds": %s, "skillTagIds": %s, "experienceTagIds": %s,
                                 "statusNote": %s, "bio": %s}}
                    """.formatted(token, loginId, password, nickname, array(interests), array(skills),
                    array(experiences), string(statusNote), string(bio));
        }

        private static String array(List<Long> ids) {
            return ids == null ? "null"
                    : ids.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
        }

        private static String string(String value) {
            return value == null ? "null" : "\"" + value + "\"";
        }

    }

}
