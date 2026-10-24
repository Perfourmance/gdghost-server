package com.gdghost.question;

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

import jakarta.servlet.http.Cookie;

/** 질문 상세 */
@IntegrationTest
class QuestionDetailIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember me;
    Cookie login;
    TestMember author;

    @BeforeEach
    void setUp() {
        me = fixtures.member();
        login = fixtures.loginCookie(me);
        author = fixtures.member();
    }

    @Test
    void 본문과_상태_내_질문_여부를_준다() throws Exception {
        long question = fixtures.question(author.id(), false);

        mvc.perform(get("/api/v1/questions/{id}", question).cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(question))
                .andExpect(jsonPath("$.body").exists())
                .andExpect(jsonPath("$.author.id").value(author.id()))
                .andExpect(jsonPath("$.state").value("WAITING"))
                .andExpect(jsonPath("$.adoptedAnswerId").isEmpty())
                .andExpect(jsonPath("$.editedAfterAnswer").value(false))
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.mine").value(false))
                .andExpect(jsonPath("$.canAnswer").value(true))
                .andExpect(jsonPath("$.canPostAnonymously").value(false))
                .andExpect(jsonPath("$.routing").isEmpty());

        mvc.perform(get("/api/v1/questions/{id}", question).cookie(fixtures.loginCookie(author)))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.canAnswer").value(false));
    }

    @Test
    void 없거나_삭제_숨김이면_404() throws Exception {
        long deleted = fixtures.question(author.id(), false);
        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'STAFF' WHERE id = ?", deleted);

        mvc.perform(get("/api/v1/questions/{id}", deleted).cookie(login))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/questions/{id}", deleted + 1000).cookie(login))
                .andExpect(status().isNotFound());
    }

    @Test
    void 조회수는_부원당_하루_한_번이고_글쓴이는_세지_않는다() throws Exception {
        long question = fixtures.question(author.id(), false);

        mvc.perform(get("/api/v1/questions/{id}", question).cookie(login))
                .andExpect(jsonPath("$.viewCount").value(1));
        mvc.perform(get("/api/v1/questions/{id}", question).cookie(login))
                .andExpect(jsonPath("$.viewCount").value(1));
        mvc.perform(get("/api/v1/questions/{id}", question).cookie(fixtures.loginCookie(author)))
                .andExpect(jsonPath("$.viewCount").value(1));
        mvc.perform(get("/api/v1/questions/{id}", question).cookie(fixtures.loginCookie(fixtures.member())))
                .andExpect(jsonPath("$.viewCount").value(2));
    }

    @Test
    void 채택_답변이_맨_위_나머지는_오래된_순이고_답글은_트리로() throws Exception {
        long question = fixtures.question(author.id(), false);
        TestMember a = fixtures.member();
        long older = fixtures.answer(question, a.id(), false, null);
        long adopted = fixtures.answer(question, me.id(), false, null);
        long reply = fixtures.answer(question, author.id(), false, older);
        jdbc.update("UPDATE question SET state = 'ADOPTED', adopted_answer_id = ? WHERE id = ?", adopted, question);

        mvc.perform(get("/api/v1/questions/{id}", question).cookie(login))
                .andExpect(jsonPath("$.adoptedAnswerId").value(adopted))
                .andExpect(jsonPath("$.answers[*].id", contains((int) adopted, (int) older)))
                .andExpect(jsonPath("$.answers[0].adopted").value(true))
                .andExpect(jsonPath("$.answers[0].mine").value(true))
                .andExpect(jsonPath("$.answers[0].depth").value(0))
                .andExpect(jsonPath("$.answers[0].replies").isEmpty())
                .andExpect(jsonPath("$.answers[1].adopted").value(false))
                .andExpect(jsonPath("$.answers[1].mine").value(false))
                .andExpect(jsonPath("$.answers[1].author.id").value(a.id()))
                .andExpect(jsonPath("$.answers[1].body").value("답변 본문"))
                .andExpect(jsonPath("$.answers[1].replies[0].id").value(reply))
                .andExpect(jsonPath("$.answers[1].replies[0].parentId").value(older))
                .andExpect(jsonPath("$.answers[1].replies[0].depth").value(1))
                .andExpect(jsonPath("$.answers[1].deletedBy").doesNotExist());
    }

    @Test
    void depth_3보다_깊은_답글은_depth_3_자리에_붙는다() throws Exception {
        long question = fixtures.question(author.id(), false);
        long d0 = fixtures.answer(question, me.id(), false, null);
        long d1 = fixtures.answer(question, author.id(), false, d0);
        long d2 = fixtures.answer(question, me.id(), false, d1);
        long d3 = fixtures.answer(question, author.id(), false, d2);
        long d4 = fixtures.answer(question, me.id(), false, d3);

        mvc.perform(get("/api/v1/questions/{id}", question).cookie(login))
                .andExpect(jsonPath("$.answers[0].replies[0].replies[0].replies[*].id",
                        contains((int) d3, (int) d4)))
                .andExpect(jsonPath("$.answers[0].replies[0].replies[0].replies[1].depth").value(3))
                .andExpect(jsonPath("$.answers[0].replies[0].replies[0].replies[1].parentId").value(d3))
                .andExpect(jsonPath("$.answers[0].replies[0].replies[0].replies[0].replies").isEmpty());
    }

    @Test
    void 지운_답변은_답글이_남으면_자리만_두고_없으면_뺀다() throws Exception {
        long question = fixtures.question(author.id(), false);
        long withReply = fixtures.answer(question, me.id(), false, null);
        long reply = fixtures.answer(question, author.id(), false, withReply);
        long alone = fixtures.answer(question, me.id(), false, null);
        jdbc.update("UPDATE answer SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'STAFF' WHERE id IN (?, ?)",
                withReply, alone);

        mvc.perform(get("/api/v1/questions/{id}", question).cookie(login))
                .andExpect(jsonPath("$.answers", hasSize(1)))
                .andExpect(jsonPath("$.answers[0].id").value(withReply))
                .andExpect(jsonPath("$.answers[0].deletedBy").value("STAFF"))
                .andExpect(jsonPath("$.answers[0].body").isEmpty())
                .andExpect(jsonPath("$.answers[0].author").isEmpty())
                .andExpect(jsonPath("$.answers[0].replies[0].id").value(reply));
    }

    @Test
    void depth_3에서_지운_답변도_옆자리_답글이_남으면_자리를_둔다() throws Exception {
        long question = fixtures.question(author.id(), false);
        long d0 = fixtures.answer(question, me.id(), false, null);
        long d1 = fixtures.answer(question, author.id(), false, d0);
        long d2 = fixtures.answer(question, me.id(), false, d1);
        long d3 = fixtures.answer(question, author.id(), false, d2);
        long d4 = fixtures.answer(question, me.id(), false, d3);
        long d5 = fixtures.answer(question, author.id(), false, d4);
        long lonely = fixtures.answer(question, author.id(), false, d2);
        jdbc.update("UPDATE answer SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id IN (?, ?, ?)",
                d3, d4, lonely);

        // d5 → d4 → d3 으로 이어진 자리는 남고, 답글 없는 lonely는 빠진다
        mvc.perform(get("/api/v1/questions/{id}", question).cookie(login))
                .andExpect(jsonPath("$.answers[0].replies[0].replies[0].replies[*].id",
                        contains((int) d3, (int) d4, (int) d5)))
                .andExpect(jsonPath("$.answers[0].replies[0].replies[0].replies[0].deletedBy").value("AUTHOR"))
                .andExpect(jsonPath("$.answers[0].replies[0].replies[0].replies[1].body").isEmpty())
                .andExpect(jsonPath("$.answers[0].replies[0].replies[0].replies[2].parentId").value(d4));
    }

    @Test
    void 익명_질문은_글_단위_번호로_표시하고_작성자_id를_주지_않는다() throws Exception {
        long question = fixtures.question(author.id(), true);
        jdbc.update("INSERT INTO question_anon_alias (question_id, member_id, alias_no) VALUES (?, ?, 0), (?, ?, 1)",
                question, author.id(), question, me.id());
        long mine = fixtures.answer(question, me.id(), true, null);
        long byAuthor = fixtures.answer(question, author.id(), true, mine);

        mvc.perform(get("/api/v1/questions/{id}", question).cookie(login))
                .andExpect(jsonPath("$.anonymous").value(true))
                .andExpect(jsonPath("$.canPostAnonymously").value(true))
                .andExpect(jsonPath("$.author.label").value("익명"))
                .andExpect(jsonPath("$.author.id").doesNotExist())
                .andExpect(jsonPath("$.author.mine").doesNotExist())
                .andExpect(jsonPath("$.answers[0].id").value(mine))
                .andExpect(jsonPath("$.answers[0].author.label").value("익명1"))
                .andExpect(jsonPath("$.answers[0].author.mine").value(true))
                .andExpect(jsonPath("$.answers[0].author.id").doesNotExist())
                .andExpect(jsonPath("$.answers[0].replies[0].id").value(byAuthor))
                .andExpect(jsonPath("$.answers[0].replies[0].author.label").value("익명(글쓴이)"))
                .andExpect(jsonPath("$.answers[0].replies[0].author.id").doesNotExist());
    }

    @Test
    void 탈퇴한_작성자는_탈퇴한_부원으로_보인다() throws Exception {
        TestMember withdrawn = fixtures.withdrawn();
        long question = fixtures.question(withdrawn.id(), false);
        fixtures.answer(question, withdrawn.id(), false, null);

        mvc.perform(get("/api/v1/questions/{id}", question).cookie(login))
                .andExpect(jsonPath("$.author.id").isEmpty())
                .andExpect(jsonPath("$.author.nickname").value("탈퇴한 부원"))
                .andExpect(jsonPath("$.author.withdrawn").value(true))
                .andExpect(jsonPath("$.answers[0].author.nickname").value("탈퇴한 부원"));
    }

}
