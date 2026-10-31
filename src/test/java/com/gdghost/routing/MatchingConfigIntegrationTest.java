package com.gdghost.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gdghost.question.QuestionCategory;
import com.gdghost.support.IntegrationTest;

@IntegrationTest
class MatchingConfigIntegrationTest {

    @Autowired
    MatchingConfig config;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 모든_분류에_매핑이_있고_이름은_실제_경험_태그다() {
        List<String> tags = jdbc.queryForList("SELECT name FROM tag WHERE kind = 'EXPERIENCE'", String.class);

        assertThat(config.categoryExperience()).containsOnlyKeys(QuestionCategory.values());
        config.categoryExperience().values().forEach(names -> assertThat(tags).containsAll(names));
        assertThat(config.minScore()).isEqualTo(0.15);
    }

}
