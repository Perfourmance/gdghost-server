package com.gdghost.embedding;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.gdghost.question.QuestionCategory;
import com.gdghost.question.QuestionCreateRequest;
import com.gdghost.question.QuestionService;
import com.gdghost.support.FakeEmbeddingClient;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

@IntegrationTest
class EmbeddingIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeEmbeddingClient client;

    @Autowired
    QuestionService questions;

    @Autowired
    EmbeddingService embeddings;

    @Autowired
    EmbeddingStore store;

    @Autowired
    EmbeddingRetryJob job;

    private TestMember member;

    @BeforeEach
    void setUp() {
        client.clear();
        member = fixtures.member();
    }

    @Test
    void 질문을_쓰면_벡터를_저장하고_ai_log를_남긴다() {
        long id = created("이력서 고민");

        FakeEmbeddingClient.await(() -> questionRows(id) == 1);
        assertThat(jdbc.queryForObject("SELECT JSON_LENGTH(vector) FROM question_embedding WHERE question_id = ?",
                Integer.class, id)).isEqualTo(1024);
        assertThat(jdbc.queryForObject("SELECT model FROM question_embedding WHERE question_id = ?", String.class, id))
                .isEqualTo("@cf/baai/bge-m3");
        assertThat(client.texts(text -> text.contains("이력서 고민"))).containsExactly("취업·채용 이력서 고민\n본문");
        FakeEmbeddingClient.await(() -> aiLogs("EMBED_QUESTION", id, "SUCCESS") == 1);
    }

    @Test
    void 질문을_고치면_새_글로_다시_계산한다() throws Exception {
        long id = created("고치기 전");
        FakeEmbeddingClient.await(() -> questionRows(id) == 1);

        patchQuestion(id, "{\"title\":\"고친 뒤\"}");

        FakeEmbeddingClient.await(() -> !client.texts(text -> text.contains("고친 뒤")).isEmpty()
                && questionRows(id) == 1);
    }

    @Test
    void 내용이_같은_수정은_벡터를_지우지_않고_부르지_않는다() throws Exception {
        long id = created("그대로");
        FakeEmbeddingClient.await(() -> questionRows(id) == 1);

        patchQuestion(id, "{\"title\":\"그대로\"}");
        job.run();

        assertThat(questionRows(id)).isEqualTo(1);
        assertThat(client.texts(text -> text.contains("그대로"))).hasSize(1);
    }

    @Test
    void 재계산이_실패하면_옛_벡터를_남기지_않고_배치가_채운다() throws Exception {
        long id = created("수정 전");
        FakeEmbeddingClient.await(() -> questionRows(id) == 1);
        client.failWhen(text -> text.contains("수정 후"));

        patchQuestion(id, "{\"title\":\"수정 후\"}");

        FakeEmbeddingClient.await(() -> aiLogs("EMBED_QUESTION", id, "FAILED") == 1);
        assertThat(questionRows(id)).isZero();
        FakeEmbeddingClient.await(
                () -> jdbc.queryForObject("SELECT COUNT(*) FROM error_log", Integer.class) > 0);

        client.failWhen(text -> false);
        job.run();

        assertThat(questionRows(id)).isEqualTo(1);
    }

    @Test
    void 호출_중에_글이_바뀌면_옛_글의_벡터를_넣지_않는다() {
        long id = fixtures.question(member.id(), false);
        client.failWhen(text -> {
            jdbc.update("UPDATE question SET title = '호출 중 수정' WHERE id = ?", id);
            return false;
        });

        assertThat(embeddings.embedQuestion(id)).isEqualTo(EmbeddingService.Outcome.SKIPPED);

        assertThat(questionRows(id)).isZero();
    }

    @Test
    void 프로필이_바뀌면_저장하고_글이_같으면_부르지_않는다() throws Exception {
        patchProfile("{\"bio\":\"취업 이야기해요\"}");
        FakeEmbeddingClient.await(() -> memberRows(member.id()) == 1);
        String hash = jdbc.queryForObject("SELECT source_hash FROM member_embedding WHERE member_id = ?",
                String.class, member.id());
        assertThat(hash).hasSize(64);

        // 닉네임은 글에 들어가지 않는다
        jdbc.update("UPDATE member SET nickname = '새이름' WHERE id = ?", member.id());
        job.run();

        assertThat(client.texts(text -> text.contains("취업 이야기해요"))).containsExactly("소개: 취업 이야기해요");
        FakeEmbeddingClient.await(() -> aiLogs("EMBED_MEMBER", member.id(), "SUCCESS") == 1);

        patchProfile("{\"bio\":\"대학원 이야기해요\"}");

        FakeEmbeddingClient.await(() -> !hash.equals(jdbc.queryForObject(
                "SELECT source_hash FROM member_embedding WHERE member_id = ?", String.class, member.id())));
    }

    @Test
    void 배치는_빠진_것만_채우고_지운_질문과_탈퇴_정지_부원은_뺀다() {
        long missing = fixtures.question(member.id(), false);
        long deleted = fixtures.question(member.id(), false);
        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?", deleted);
        bio(member.id(), "활동 중");
        TestMember suspended = fixtures.suspended();
        bio(suspended.id(), "정지");
        TestMember withdrawn = fixtures.withdrawn();
        bio(withdrawn.id(), "탈퇴");

        job.run();

        assertThat(questionRows(missing)).isEqualTo(1);
        assertThat(questionRows(deleted)).isZero();
        assertThat(memberRows(member.id())).isEqualTo(1);
        assertThat(memberRows(suspended.id())).isZero();
        assertThat(memberRows(withdrawn.id())).isZero();
        assertThat(batchMessage()).isEqualTo("saved=2 badInput=0 failed=0");
    }

    @Test
    void 모델이_다른_벡터는_다시_계산한다() {
        long id = fixtures.question(member.id(), false);
        jdbc.update("INSERT INTO question_embedding (question_id, vector, model) VALUES (?, '[1]', 'old')", id);

        job.run();

        assertThat(jdbc.queryForObject("SELECT model FROM question_embedding WHERE question_id = ?", String.class, id))
                .isEqualTo("@cf/baai/bge-m3");
    }

    @Test
    void 호출이_실패하면_그_회차를_멈춘다() {
        long first = fixtures.question(member.id(), false);
        long second = fixtures.question(member.id(), false);
        client.failWhen(text -> true);

        job.run();

        assertThat(aiLogs("EMBED_QUESTION", first, "FAILED")).isEqualTo(1);
        assertThat(aiLogs("EMBED_QUESTION", second, "FAILED")).isZero();
        assertThat(batchMessage()).isEqualTo("saved=0 badInput=0 failed=1 stopped");
    }

    @Test
    void 글_문제면_건너뛰고_다음_글을_처리한다() {
        long first = fixtures.question(member.id(), false);
        long second = fixtures.question(member.id(), false);
        String firstTitle = jdbc.queryForObject("SELECT title FROM question WHERE id = ?", String.class, first);
        client.rejectWhen(text -> text.contains(firstTitle + "\n"));

        job.run();

        assertThat(questionRows(first)).isZero();
        assertThat(questionRows(second)).isEqualTo(1);
        assertThat(batchMessage()).isEqualTo("saved=1 badInput=1 failed=0");
    }

    @Test
    void 호출_중에_지워지거나_탈퇴한_대상은_저장하지_않는다() {
        long question = fixtures.question(member.id(), false);
        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?", question);
        TestMember withdrawn = fixtures.withdrawn();

        store.saveQuestion(question, new float[1024], "model");
        store.saveMember(withdrawn.id(), new float[1024], "model", "hash");

        assertThat(questionRows(question)).isZero();
        assertThat(memberRows(withdrawn.id())).isZero();
    }

    private long created(String title) {
        return questions.create(member.id(),
                new QuestionCreateRequest(QuestionCategory.JOB, title, "본문", false, null)).question().id();
    }

    private void patchQuestion(long id, String json) throws Exception {
        mvc.perform(patch("/api/v1/questions/{id}", id).cookie(fixtures.loginCookie(member)).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private void patchProfile(String json) throws Exception {
        mvc.perform(patch("/api/v1/me/profile").cookie(fixtures.loginCookie(member)).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private void bio(long memberId, String bio) {
        jdbc.update("UPDATE member SET bio = ? WHERE id = ?", bio, memberId);
    }

    private int questionRows(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM question_embedding WHERE question_id = ?", Integer.class, id);
    }

    private int memberRows(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM member_embedding WHERE member_id = ?", Integer.class, id);
    }

    private int aiLogs(String kind, long target, String state) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ai_log WHERE kind = ? AND target_id = ? AND state = ?",
                Integer.class, kind, target, state);
    }

    private String batchMessage() {
        return jdbc.queryForObject("""
                SELECT message FROM batch_log WHERE job_name = 'embedding_retry' ORDER BY id DESC LIMIT 1
                """, String.class);
    }

}
