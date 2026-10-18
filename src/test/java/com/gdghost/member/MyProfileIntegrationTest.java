package com.gdghost.member;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/** 내 프로필 · 설정 조회, 설정 변경 */
@IntegrationTest
class MyProfileIntegrationTest {

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
    void 내_프로필을_태그_연락_수단_설정과_함께_준다() throws Exception {
        addTag("INTEREST", "백엔드");
        addTag("SKILL", "Spring");
        addTag("EXPERIENCE", "인턴");
        jdbc.update("UPDATE member SET status_note = '인턴 근무 중', status_note_updated_at = UTC_TIMESTAMP(6) WHERE id = ?",
                member.id());
        jdbc.update("INSERT INTO member_contact (member_id, type, value, is_public) VALUES (?, 'KAKAO', 'serim_k', FALSE)",
                member.id());

        mvc.perform(get("/api/v1/me").cookie(login))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.id").value(member.id()))
                .andExpect(jsonPath("$.loginId").value(member.loginId()))
                .andExpect(jsonPath("$.email").value(member.loginId() + "@test.gdghost"))
                .andExpect(jsonPath("$.nickname").value(member.nickname()))
                .andExpect(jsonPath("$.position").value("DEV"))
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.interests", hasSize(1)))
                .andExpect(jsonPath("$.interests[0].name").value("백엔드"))
                .andExpect(jsonPath("$.interests[0].skills[0].name").value("Spring"))
                .andExpect(jsonPath("$.experiences[0].name").value("인턴"))
                .andExpect(jsonPath("$.statusNote").value("인턴 근무 중"))
                .andExpect(jsonPath("$.statusNoteUpdatedAt").isNotEmpty())
                // 비공개여도 본인에게는 준다
                .andExpect(jsonPath("$.contacts[0].type").value("KAKAO"))
                .andExpect(jsonPath("$.contacts[0].value").value("serim_k"))
                .andExpect(jsonPath("$.contacts[0].isPublic").value(false))
                .andExpect(jsonPath("$.settings.acceptQuestion").value(true))
                .andExpect(jsonPath("$.settings.acceptCoffee").value(false))
                .andExpect(jsonPath("$.settings.coffeeMemo").doesNotExist())
                .andExpect(jsonPath("$.settings.hiddenFromList").value(false))
                .andExpect(jsonPath("$.settings.pushOn").value(false))
                .andExpect(jsonPath("$.nicknameChangeAvailableAt").doesNotExist())
                .andExpect(jsonPath("$.emailChangeAvailableAt").doesNotExist())
                .andExpect(jsonPath("$.profileCompleteness.filled").value(5))
                .andExpect(jsonPath("$.profileCompleteness.total").value(6))
                .andExpect(jsonPath("$.profileCompleteness.missing", contains("BIO")));
    }

    @Test
    void 닉네임_이메일을_30일_안에_바꿨으면_다시_바꿀_수_있는_때를_준다() throws Exception {
        jdbc.update("""
                UPDATE member SET nickname_changed_at = '2026-01-01 00:00:00',
                                  email_changed_at = UTC_TIMESTAMP(6) - INTERVAL 10 DAY
                WHERE id = ?
                """, member.id());

        mvc.perform(get("/api/v1/me").cookie(login))
                .andExpect(jsonPath("$.nicknameChangeAvailableAt").doesNotExist())
                .andExpect(jsonPath("$.emailChangeAvailableAt").isNotEmpty());
    }

    @Test
    void 설정은_보낸_필드만_바꾼다() throws Exception {
        patchSettings("{\"acceptQuestion\":false,\"pushOn\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptQuestion").value(false))
                .andExpect(jsonPath("$.pushOn").value(true))
                .andExpect(jsonPath("$.hiddenFromList").value(false));

        patchSettings("{\"hiddenFromList\":true}")
                .andExpect(jsonPath("$.acceptQuestion").value(false))
                .andExpect(jsonPath("$.hiddenFromList").value(true));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT accept_question, push_on, hidden_from_list FROM member WHERE id = ?", member.id());
        assertThat(row).containsEntry("accept_question", false).containsEntry("push_on", true)
                .containsEntry("hidden_from_list", true);
    }

    @Test
    void 커피챗은_메모가_있어야_켤_수_있다() throws Exception {
        patchSettings("{\"acceptCoffee\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("coffeeMemo"));

        patchSettings("{\"acceptCoffee\":true,\"coffeeMemo\":\"  카톡으로 연락 주세요 \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptCoffee").value(true))
                .andExpect(jsonPath("$.coffeeMemo").value("카톡으로 연락 주세요"));
    }

    @Test
    void 커피챗을_켠_채로_메모를_지울_수_없다() throws Exception {
        patchSettings("{\"acceptCoffee\":true,\"coffeeMemo\":\"메모\"}").andExpect(status().isOk());

        patchSettings("{\"coffeeMemo\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 끄면서 지우면 된다
        patchSettings("{\"acceptCoffee\":false,\"coffeeMemo\":\" \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptCoffee").value(false))
                .andExpect(jsonPath("$.coffeeMemo").doesNotExist());
    }

    @Test
    void 커피챗을_꺼도_메모는_남는다() throws Exception {
        patchSettings("{\"acceptCoffee\":true,\"coffeeMemo\":\"메모\"}").andExpect(status().isOk());

        patchSettings("{\"acceptCoffee\":false}")
                .andExpect(jsonPath("$.acceptCoffee").value(false))
                .andExpect(jsonPath("$.coffeeMemo").value("메모"));
    }

    @Test
    void 커피챗_메모는_60자까지() throws Exception {
        patchSettings("{\"coffeeMemo\":\"" + "가".repeat(61) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        patchSettings("{\"coffeeMemo\":\"" + "가".repeat(60) + "\"}").andExpect(status().isOk());
    }

    private void addTag(String kind, String name) {
        jdbc.update("INSERT INTO member_tag (member_id, tag_id) SELECT ?, id FROM tag WHERE kind = ? AND name = ? LIMIT 1",
                member.id(), kind, name);
    }

    private ResultActions patchSettings(String json) throws Exception {
        return mvc.perform(patch("/api/v1/me/settings").cookie(login).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

}
