package com.gdghost.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gdghost.TestcontainersConfiguration;
import com.gdghost.support.DatabaseCleaner;
import com.gdghost.support.FakeDiscordGateway;
import com.gdghost.support.FakeMailGateway;
import com.gdghost.support.FakePushGateway;
import com.gdghost.support.Fixtures;

/**
 * 실제 Workers AI를 부른다. 평소에는 건너뛰고, 모델을 바꾸거나 호출 코드를 고쳤을 때 직접 돌린다.
 * 키는 .env에서 읽는다: LIVE_EMBEDDING=true ./gradlew test --tests '*EmbeddingLiveTest'
 */
@EnabledIfEnvironmentVariable(named = "LIVE_EMBEDDING", matches = "true")
@SpringBootTest
@Import({TestcontainersConfiguration.class, Fixtures.class, FakeMailGateway.class, FakeDiscordGateway.class,
        FakePushGateway.class})
@ExtendWith(DatabaseCleaner.class)
class EmbeddingLiveTest {

    @Autowired
    EmbeddingClient client;

    @Autowired
    EmbeddingService embeddings;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 질문과_부원_벡터를_실제로_받아_저장한다() {
        assertThat(client).isInstanceOf(WorkersAiEmbeddingClient.class);
        assertThat(client.enabled()).isTrue();
        long member = fixtures.member().id();
        long question = fixtures.question(member, false);
        jdbc.update("UPDATE member SET bio = '신입 백엔드 취업 준비 과정을 이야기해 드려요' WHERE id = ?", member);

        assertThat(embeddings.embedQuestion(question)).isEqualTo(EmbeddingService.Outcome.SAVED);
        assertThat(embeddings.embedMember(member)).isEqualTo(EmbeddingService.Outcome.SAVED);

        assertThat(jdbc.queryForObject("SELECT JSON_LENGTH(vector) FROM question_embedding WHERE question_id = ?",
                Integer.class, question)).isEqualTo(1024);
        assertThat(jdbc.queryForObject("SELECT JSON_LENGTH(vector) FROM member_embedding WHERE member_id = ?",
                Integer.class, member)).isEqualTo(1024);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_log WHERE state = 'SUCCESS'", Integer.class))
                .isEqualTo(2);
    }

}
