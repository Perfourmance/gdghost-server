package com.gdghost.member;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/** 프로필 수정 */
@IntegrationTest
class ProfileUpdateIntegrationTest {

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
    void 보낸_필드만_바꾸고_내_프로필_모양으로_돌려준다() throws Exception {
        jdbc.update("UPDATE member SET department = '컴퓨터과학과', bio = '소개' WHERE id = ?", member.id());

        patchProfile("{\"position\":\"AI\",\"status\":\"GRADUATED\",\"statusNote\":\" 인턴 근무 중 \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.id()))
                .andExpect(jsonPath("$.position").value("AI"))
                .andExpect(jsonPath("$.status").value("GRADUATED"))
                .andExpect(jsonPath("$.statusNote").value("인턴 근무 중"))
                .andExpect(jsonPath("$.statusNoteUpdatedAt").isNotEmpty())
                .andExpect(jsonPath("$.department").value("컴퓨터과학과"))
                .andExpect(jsonPath("$.bio").value("소개"))
                .andExpect(jsonPath("$.nickname").value(member.nickname()));
    }

    @Test
    void 근황이_그대로면_근황_시각을_다시_찍지_않고_빈_문자열이면_지운다() throws Exception {
        jdbc.update("""
                UPDATE member SET status_note = '근황', status_note_updated_at = '2026-01-01 00:00:00', bio = '소개'
                WHERE id = ?
                """, member.id());

        patchProfile("{\"statusNote\":\"근황\"}")
                .andExpect(jsonPath("$.statusNoteUpdatedAt").value("2026-01-01T00:00:00Z"));

        patchProfile("{\"statusNote\":\"\",\"bio\":\" \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusNote").doesNotExist())
                .andExpect(jsonPath("$.statusNoteUpdatedAt").doesNotExist())
                .andExpect(jsonPath("$.bio").doesNotExist());
    }

    @Test
    void 이름_기수_학번은_보내면_400() throws Exception {
        patchProfile("{\"name\":\"새이름\",\"generation\":6,\"studentNo\":\"25\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(3)));
    }

    @Test
    void 학과는_비울_수_없다() throws Exception {
        patchProfile("{\"department\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 닉네임을_바꾸면_30일_동안_다시_못_바꾼다() throws Exception {
        patchProfile("{\"nickname\":\"새닉네임\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.nicknameChangeAvailableAt").isNotEmpty());

        // 같은 닉네임은 변경이 아니다
        patchProfile("{\"nickname\":\"새닉네임\",\"bio\":\"소개\"}").andExpect(status().isOk());

        patchProfile("{\"nickname\":\"또다른닉\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_CHANGE_TOO_SOON"));

        jdbc.update("UPDATE member SET nickname_changed_at = UTC_TIMESTAMP(6) - INTERVAL 30 DAY WHERE id = ?",
                member.id());
        patchProfile("{\"nickname\":\"또다른닉\"}").andExpect(status().isOk());
    }

    @Test
    void 남이_쓰는_닉네임이면_409() throws Exception {
        TestMember other = fixtures.member();

        patchProfile("{\"nickname\":\"" + other.nickname() + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_TAKEN"));
        assertThat(jdbc.queryForObject("SELECT nickname_changed_at IS NULL FROM member WHERE id = ?", Boolean.class,
                member.id())).isTrue();
    }

    @Test
    void 익명으로_시작하는_닉네임은_400() throws Exception {
        patchProfile("{\"nickname\":\"익명부원\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NICKNAME_RESERVED"));
    }

    @Test
    void 태그는_보낸_종류만_통째로_교체한다() throws Exception {
        long backend = tagId("INTEREST", "백엔드");
        long frontend = tagId("INTEREST", "프론트엔드");
        long spring = skillId("백엔드", "Spring");
        long react = skillId("프론트엔드", "React");
        long intern = tagId("EXPERIENCE", "인턴");
        addTags(backend, frontend, spring, react, intern);

        // 관심 분야만 보내면 빠진 분야(프론트엔드)의 세부 기술은 같이 지운다
        patchProfile("{\"interestTagIds\":[" + backend + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests", hasSize(1)))
                .andExpect(jsonPath("$.interests[0].skills[*].name", contains("Spring")))
                .andExpect(jsonPath("$.experiences[*].name", contains("인턴")));

        patchProfile("{\"experienceTagIds\":[]}")
                .andExpect(jsonPath("$.experiences", hasSize(0)))
                .andExpect(jsonPath("$.interests[0].skills", hasSize(1)));

        assertThat(memberTagIds()).containsExactlyInAnyOrder(backend, spring);
    }

    @Test
    void 세부_기술을_모두_해제하면_빈_배열로_지우고_관심_분야는_남는다() throws Exception {
        long backend = tagId("INTEREST", "백엔드");
        long spring = skillId("백엔드", "Spring");
        long dbDesign = skillId("백엔드", "DB 설계");
        long intern = tagId("EXPERIENCE", "인턴");
        addTags(backend, spring, dbDesign, intern);

        // 생략 · null은 '안 바꿈'이라 세부 기술이 그대로 남는다
        patchProfile("{\"bio\":\"소개\"}")
                .andExpect(jsonPath("$.interests[0].skills[*].name", contains("Spring", "DB 설계")));
        patchProfile("{\"skillTagIds\":null}")
                .andExpect(jsonPath("$.interests[0].skills", hasSize(2)));

        patchProfile("{\"skillTagIds\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests[*].name", contains("백엔드")))
                .andExpect(jsonPath("$.interests[0].skills", hasSize(0)))
                .andExpect(jsonPath("$.experiences[*].name", contains("인턴")))
                .andExpect(jsonPath("$.profileCompleteness.missing", contains("SKILL", "STATUS_NOTE", "CONTACT")));

        assertThat(memberTagIds()).containsExactlyInAnyOrder(backend, intern);
    }

    @Test
    void 태그_규칙에_어긋나면_400이고_아무것도_안_바뀐다() throws Exception {
        long backend = tagId("INTEREST", "백엔드");
        addTags(backend);

        patchProfile("{\"interestTagIds\":[],\"bio\":\"소개\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("profile.interestTagIds"));

        patchProfile("{\"skillTagIds\":[" + skillId("프론트엔드", "React") + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("profile.skillTagIds"));

        assertThat(memberTagIds()).containsExactly(backend);
        assertThat(jdbc.queryForObject("SELECT bio FROM member WHERE id = ?", String.class, member.id())).isNull();
    }

    private ResultActions patchProfile(String json) throws Exception {
        return mvc.perform(patch("/api/v1/me/profile").cookie(login).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private long tagId(String kind, String name) {
        return jdbc.queryForObject("SELECT id FROM tag WHERE kind = ? AND name = ? LIMIT 1", Long.class, kind, name);
    }

    private long skillId(String interest, String name) {
        return jdbc.queryForObject("""
                SELECT s.id FROM tag s JOIN tag p ON p.id = s.parent_id
                WHERE s.kind = 'SKILL' AND p.name = ? AND s.name = ?
                """, Long.class, interest, name);
    }

    private void addTags(long... tagIds) {
        for (long tagId : tagIds) {
            jdbc.update("INSERT INTO member_tag (member_id, tag_id) VALUES (?, ?)", member.id(), tagId);
        }
    }

    private List<Long> memberTagIds() {
        return jdbc.queryForList("SELECT tag_id FROM member_tag WHERE member_id = ?", Long.class, member.id());
    }

}
