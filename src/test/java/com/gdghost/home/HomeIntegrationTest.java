package com.gdghost.home;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

/** 홈 */
@IntegrationTest
class HomeIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember author;
    long[] questions = new long[4];

    @BeforeEach
    void setUp() {
        author = fixtures.member();
        for (int i = 0; i < questions.length; i++) {
            questions[i] = fixtures.question(author.id(), false);
        }
        long deleted = fixtures.question(author.id(), false);
        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?", deleted);
    }

    @Test
    void 부원은_최근_질문_3개와_빈_묶음을_받는다() throws Exception {
        mvc.perform(get("/api/v1/home").cookie(fixtures.loginCookie(fixtures.member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentQuestions[*].id",
                        contains((int) questions[3], (int) questions[2], (int) questions[1])))
                .andExpect(jsonPath("$.recentQuestions[0].state").value("WAITING"))
                .andExpect(jsonPath("$.recentQuestions[0].answererPreview").isArray())
                .andExpect(jsonPath("$.recentQuestions[0].author.id").value(author.id()))
                .andExpect(jsonPath("$.pinnedNotices").isEmpty())
                .andExpect(jsonPath("$.unconfirmedMustReadCount").value(0))
                .andExpect(jsonPath("$.upcomingSchedules").isEmpty())
                .andExpect(jsonPath("$.popularShares").isEmpty());
    }

    @Test
    void 비회원은_제목까지만_받고_개인_값이_없다() throws Exception {
        mvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentQuestions", hasSize(3)))
                .andExpect(jsonPath("$.recentQuestions[0].title").exists())
                .andExpect(jsonPath("$.recentQuestions[0].author.nickname").value(author.nickname()))
                .andExpect(jsonPath("$.recentQuestions[0].author.id").doesNotExist())
                .andExpect(jsonPath("$.recentQuestions[0].answerCount").value(0))
                .andExpect(jsonPath("$.recentQuestions[0].answererPreview").doesNotExist())
                .andExpect(jsonPath("$.recentQuestions[0].viewCount").doesNotExist())
                .andExpect(jsonPath("$.unconfirmedMustReadCount").doesNotExist())
                .andExpect(jsonPath("$.pinnedNotices").isEmpty());
    }

    @Test
    void 인기_공유는_최근_7일_저장_순으로_3개() throws Exception {
        long top = fixtures.share(author.id());
        long second = fixtures.share(author.id());
        long third = fixtures.share(author.id());
        long fourth = fixtures.share(author.id());
        long old = fixtures.share(author.id());
        long deleted = fixtures.share(author.id());
        long a = fixtures.member().id();
        long b = fixtures.member().id();
        long c = fixtures.member().id();
        for (long member : new long[] { a, b, c }) {
            saveShare(top, member, 0);
            saveShare(old, member, 8);
            saveShare(deleted, member, 0);
        }
        saveShare(second, a, 0);
        saveShare(second, b, 0);
        saveShare(third, a, 0);
        saveShare(fourth, b, 0);
        jdbc.update("UPDATE share SET save_count = 100 WHERE id = ?", old);
        jdbc.update("UPDATE share SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?", deleted);

        mvc.perform(get("/api/v1/home").cookie(fixtures.loginCookie(fixtures.member())))
                .andExpect(jsonPath("$.popularShares[*].id", contains((int) top, (int) second, (int) fourth)))
                .andExpect(jsonPath("$.popularShares[0].author.id").value(author.id()));
        mvc.perform(get("/api/v1/home"))
                .andExpect(jsonPath("$.popularShares[*].id", contains((int) top, (int) second, (int) fourth)))
                .andExpect(jsonPath("$.popularShares[0].title").exists())
                .andExpect(jsonPath("$.popularShares[0].category").value("TIP"))
                .andExpect(jsonPath("$.popularShares[0].author").doesNotExist())
                .andExpect(jsonPath("$.popularShares[0].saved").doesNotExist());
    }

    private void saveShare(long share, long member, int daysAgo) {
        jdbc.update("""
                INSERT INTO share_save (share_id, member_id, created_at)
                VALUES (?, ?, UTC_TIMESTAMP(6) - INTERVAL ? DAY)
                """, share, member, daysAgo);
    }

}
