package com.gdghost.routing;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.embedding.EmbeddingStore;
import com.gdghost.support.FakeEmbeddingClient;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

/** 넘기기의 다음 발송은 시간에 따라 달라서(22–08시) 응답값보다 행을 본다. 시간 고정 흐름은 RoutingIntegrationTest */
@IntegrationTest
class RoutingApiIntegrationTest {

    private static final String MODEL = "@cf/baai/bge-m3";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeEmbeddingClient client;

    @Autowired
    EmbeddingStore embeddings;

    private TestMember me;

    @BeforeEach
    void setUp() {
        client.clear();
        me = fixtures.member();
    }

    @Test
    void 받은_질문을_넘기면_PASSED이고_다시_넘길_수_없다() throws Exception {
        long question = fixtures.question(fixtures.member().id(), false);
        routing(question, me.id(), "SENT");

        pass(question).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PASSED"))
                .andExpect(jsonPath("$.nextCandidateNotified").value(false));

        Map<String, Object> row = jdbc.queryForMap("SELECT state, responded_at FROM routing WHERE question_id = ?",
                question);
        assertThat(row.get("state")).isEqualTo("PASSED");
        assertThat(row.get("responded_at")).isNotNull();
        pass(question).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROUTING_NOT_ASSIGNED"));
    }

    @Test
    void 받지_않았거나_운영진_단계면_403이고_없는_질문은_404다() throws Exception {
        long question = fixtures.question(fixtures.member().id(), false);
        pass(question).andExpect(status().isForbidden());

        routing(question, me.id(), "CANDIDATE");
        pass(question).andExpect(status().isForbidden());

        long staffOnly = fixtures.question(fixtures.member().id(), false);
        jdbc.update("INSERT INTO routing (question_id, member_id, stage, state) VALUES (?, ?, 'STAFF', 'SENT')",
                staffOnly, me.id());
        pass(staffOnly).andExpect(status().isForbidden());

        pass(question + 1000).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        long deleted = fixtures.question(fixtures.member().id(), false);
        routing(deleted, me.id(), "SENT");
        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'STAFF' WHERE id = ?", deleted);
        pass(deleted).andExpect(status().isNotFound());
    }

    @Test
    void 넘긴_만큼만_보충하고_질문당_5명을_넘지_않는다() {
        RoutingReader.Waiting first = new RoutingReader.Waiting(1, 0, 0, 0, 0);
        RoutingReader.Waiting silent = new RoutingReader.Waiting(1, 3, 0, 0, 3);
        RoutingReader.Waiting onePass = new RoutingReader.Waiting(1, 3, 1, 0, 3);
        RoutingReader.Waiting replaced = new RoutingReader.Waiting(1, 4, 1, 1, 4);
        RoutingReader.Waiting atLimit = new RoutingReader.Waiting(1, 5, 3, 2, 5);
        RoutingReader.Waiting afterDirect = new RoutingReader.Waiting(1, 0, 0, 0, 1);
        MatchingConfig config = new MatchingConfig("v", null, null, null, null, 0.15, 3, 5, 24, false, Map.of());

        assertThat(first.slots(config)).isEqualTo(3);
        assertThat(silent.slots(config)).isZero();
        assertThat(onePass.slots(config)).isEqualTo(1);
        assertThat(replaced.slots(config)).isZero();
        assertThat(atLimit.slots(config)).isZero();
        assertThat(afterDirect.slots(config)).isEqualTo(3);
    }

    @Test
    void 선배_찾기는_맞는_선배를_사유와_함께_돌려주고_저장하지_않는다() throws Exception {
        TestMember senior = fixtures.member();
        long interest = tag("INTEREST", "백엔드");
        jdbc.update("INSERT INTO member_tag (member_id, tag_id) VALUES (?, ?)", senior.id(), interest);
        jdbc.update("""
                INSERT INTO member_tag (member_id, tag_id)
                SELECT ?, id FROM tag WHERE kind = 'SKILL' AND name = 'Spring' AND parent_id = ?
                """, senior.id(), interest);
        jdbc.update("INSERT INTO member_tag (member_id, tag_id) VALUES (?, ?)", senior.id(), tag("EXPERIENCE", "인턴"));
        embeddings.saveMember(senior.id(), vector(), MODEL, "hash");
        fixtures.member();

        find("JOB", interest, "신입 백엔드 지원 전에 뭘 더 채워야 할까요").andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andExpect(jsonPath("$.items[0].member.id").value(senior.id()))
                .andExpect(jsonPath("$.items[0].member.nickname").value(senior.nickname()))
                .andExpect(jsonPath("$.items[0].reasons[0]").value("경험: 인턴"))
                .andExpect(jsonPath("$.items[0].reasons[1]").value("백엔드 · Spring"));

        assertThat(client.texts(text -> text.contains("뭘 더 채워야")))
                .containsExactly("취업·채용 백엔드\n신입 백엔드 지원 전에 뭘 더 채워야 할까요");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routing", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_log WHERE kind = 'FIND_SENIOR' AND state = 'SUCCESS'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void 선배_찾기는_받기를_끈_사람과_본인을_빼고_하루_3건은_보지_않는다() throws Exception {
        long interest = tag("INTEREST", "백엔드");
        jdbc.update("INSERT INTO member_tag (member_id, tag_id) VALUES (?, ?)", me.id(), tag("EXPERIENCE", "인턴"));
        TestMember off = experienced();
        jdbc.update("UPDATE member SET accept_question = FALSE WHERE id = ?", off.id());
        TestMember full = experienced();
        for (int i = 0; i < 3; i++) {
            jdbc.update("""
                    INSERT INTO routing (question_id, member_id, stage, state, sent_at)
                    VALUES (?, ?, 'MEMBER', 'SENT', UTC_TIMESTAMP(6))
                    """, fixtures.question(me.id(), false), full.id());
        }

        find("JOB", interest, "이력서에 무엇을 적어야 할까요 궁금해요").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].member.id").value(full.id()));
    }

    @Test
    void AI가_실패하면_규칙_점수로_답하고_fallback을_표시한다() throws Exception {
        experienced();
        client.failWhen(text -> text.contains("실패 시험"));

        find("JOB", tag("INTEREST", "백엔드"), "실패 시험 이력서 고민입니다 알려주세요").andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.items.length()").value(1));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_log WHERE kind = 'FIND_SENIOR' AND state = 'FALLBACK'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void 결과가_없으면_빈_목록이다() throws Exception {
        find("JOB", tag("INTEREST", "백엔드"), "아무도 안 맞는 질문을 해 봅니다").andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void 입력이_잘못되면_400이고_분당_5회를_넘으면_429다() throws Exception {
        long interest = tag("INTEREST", "백엔드");
        find("JOB", interest, "짧아요").andExpect(status().isBadRequest());
        find("JOB", tag("EXPERIENCE", "인턴"), "분야가 아닌 태그를 골랐습니다").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("interestTagId"));

        for (int i = 0; i < 4; i++) {
            find("JOB", interest, "횟수를 세는 질문입니다 " + i).andExpect(status().isOk());
        }
        find("JOB", interest, "횟수를 넘긴 질문입니다").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    private TestMember experienced() {
        TestMember member = fixtures.member();
        jdbc.update("INSERT INTO member_tag (member_id, tag_id) VALUES (?, ?)", member.id(), tag("EXPERIENCE", "인턴"));
        return member;
    }

    private long tag(String kind, String name) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM tag WHERE kind = ? AND name = ? LIMIT 1", Long.class, kind,
                name);
        return ids.getFirst();
    }

    private void routing(long question, long member, String state) {
        jdbc.update("""
                INSERT INTO routing (question_id, member_id, stage, state, sent_at)
                VALUES (?, ?, 'MEMBER', ?, IF(? = 'SENT', UTC_TIMESTAMP(6), NULL))
                """, question, member, state, state);
    }

    private ResultActions pass(long question) throws Exception {
        return mvc.perform(post("/api/v1/questions/{id}/routing/pass", question).cookie(fixtures.loginCookie(me))
                .with(csrfToken()));
    }

    private ResultActions find(String category, long interest, String text) throws Exception {
        return mvc.perform(post("/api/v1/senior-matches").cookie(fixtures.loginCookie(me)).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"%s\",\"interestTagId\":%d,\"text\":\"%s\"}".formatted(category, interest, text)));
    }

    private static float[] vector() {
        float[] vector = new float[1024];
        Arrays.fill(vector, 0.5f);
        return vector;
    }

}
