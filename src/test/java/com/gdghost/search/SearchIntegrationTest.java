package com.gdghost.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class SearchIntegrationTest {

    private static final String URL = "/api/v1/search";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember me;
    TestMember author;
    Cookie login;

    @BeforeEach
    void setUp() {
        me = fixtures.member();
        author = fixtures.member();
        login = fixtures.loginCookie(me);
    }

    private ResultActions search(String q, String type) throws Exception {
        return mvc.perform(get(URL).param("q", q).param("type", type).cookie(login));
    }

    private long question(String title, String body) {
        long id = fixtures.question(author.id(), false);
        jdbc.update("UPDATE question SET title = ?, body = ? WHERE id = ?", title, body, id);
        return id;
    }

    private long share(String title, String body) {
        long id = fixtures.share(author.id());
        jdbc.update("UPDATE share SET title = ?, body = ? WHERE id = ?", title, body, id);
        return id;
    }

    private long schedule(String title, String memo, String place) {
        Map<String, Object> row = new HashMap<>();
        row.put("author_id", author.id());
        row.put("kind", "CONTEST");
        row.put("title", title);
        row.put("start_date", "2026-12-01");
        row.put("memo", memo);
        row.put("place", place);
        return new SimpleJdbcInsert(jdbc).withTableName("schedule")
                .usingColumns("author_id", "kind", "title", "start_date", "memo", "place")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(row).longValue();
    }

    private void softDelete(String table, long id) {
        jdbc.update("UPDATE " + table + " SET deleted_at = NOW(6), deleted_by = 'STAFF' WHERE id = ?", id);
    }

    @Test
    void ALL은_종류별_상위_5개와_전체_건수를_준다() throws Exception {
        for (int i = 0; i < 6; i++) {
            question("순대국 질문 " + i, null);
        }
        long shareId = share("순대국 지도", "학교 앞 가게들입니다");
        long scheduleId = schedule("순대국 모임", null, null);
        jdbc.update("UPDATE member SET status_note = '순대국 먹는 중' WHERE id = ?", author.id());

        mvc.perform(get(URL).param("q", "순대국").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.items", hasSize(5)))
                .andExpect(jsonPath("$.questions.total").value(6))
                .andExpect(jsonPath("$.shares.items[0].id").value(shareId))
                .andExpect(jsonPath("$.shares.total").value(1))
                .andExpect(jsonPath("$.schedules.items[0].id").value(scheduleId))
                .andExpect(jsonPath("$.schedules.total").value(1))
                .andExpect(jsonPath("$.members.items[0].id").value(author.id()))
                .andExpect(jsonPath("$.members.total").value(1));
    }

    @Test
    void 결과가_없으면_빈_묶음() throws Exception {
        mvc.perform(get(URL).param("q", "없는말").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.items", hasSize(0)))
                .andExpect(jsonPath("$.questions.total").value(0))
                .andExpect(jsonPath("$.shares.total").value(0))
                .andExpect(jsonPath("$.schedules.total").value(0));
    }

    @Test
    void 종류를_고르면_커서_페이지로_넘어간다() throws Exception {
        long first = question("커피 추천", null);
        long second = question("커피 원두", null);
        long third = question("커피 머신", null);

        String page = mvc.perform(get(URL).param("q", "커피").param("type", "QUESTION").param("size", "2").cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) third, (int) second)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get(URL).param("q", "커피").param("type", "QUESTION").param("size", "2")
                .param("cursor", JsonPath.<String>read(page, "$.nextCursor")).cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) first)))
                .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get(URL).param("q", "커피").param("type", "QUESTION").param("cursor", "!!!").cookie(login))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 질문은_제목과_본문에서_찾고_지운_글은_뺀다() throws Exception {
        long byTitle = question("인턴 후기", null);
        long byBody = question("질문", "인턴 지원 시기가 궁금해요");
        softDelete("question", question("인턴 숨김", null));

        search("인턴", "QUESTION")
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder((int) byTitle, (int) byBody)));
    }

    @Test
    void 익명_질문은_작성자_닉네임으로_찾히지_않고_작성자가_가려진다() throws Exception {
        long anonymous = fixtures.question(author.id(), true);
        jdbc.update("UPDATE question SET title = '비밀 고민' WHERE id = ?", anonymous);

        search(author.nickname(), "QUESTION").andExpect(jsonPath("$.items", hasSize(0)));
        String body = search("비밀", "QUESTION")
                .andExpect(jsonPath("$.items[0].id").value(anonymous))
                .andExpect(jsonPath("$.items[0].author.anonymous").value(true))
                .andExpect(jsonPath("$.items[0].author.id").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(author.nickname());
    }

    @Test
    void 공유는_제목_본문_태그에서_찾고_목록_모양으로_준다() throws Exception {
        long byTitle = share("자바 책 추천", "좋은 책 모음입니다");
        long byBody = share("책 추천", "자바 입문서 위주입니다");
        long byTag = share("책 모음", "입문서 위주입니다");
        jdbc.update("INSERT INTO share_tag (share_id, name) VALUES (?, '자바')", byTag);
        softDelete("share", share("자바 숨김", "숨긴 글입니다"));

        search("자바", "SHARE")
                .andExpect(jsonPath("$.items[*].id", contains((int) byTag, (int) byBody, (int) byTitle)))
                .andExpect(jsonPath("$.items[0].tags[0]").value("자바"))
                .andExpect(jsonPath("$.items[0].body").doesNotExist());
    }

    @Test
    void 일정은_제목_메모_장소에서_찾는다() throws Exception {
        long byTitle = schedule("해커톤 안내", null, null);
        long byMemo = schedule("대회", "해커톤 준비물", null);
        long byPlace = schedule("모임", null, "해커톤 라운지");
        jdbc.update("UPDATE schedule SET deleted_at = NOW(6) WHERE id = ?", schedule("해커톤 취소", null, null));

        search("해커톤", "SCHEDULE")
                .andExpect(jsonPath("$.items[*].id", contains((int) byPlace, (int) byMemo, (int) byTitle)));
    }

    @Test
    void 부원은_닉네임_이름_근황_태그로_찾는다() throws Exception {
        TestMember byName = fixtures.member();
        TestMember byNote = fixtures.member();
        TestMember byTag = fixtures.member();
        // tag는 시드 표라 테스트마다 비워지지 않아 있는 태그를 쓴다
        Map<String, Object> tag = jdbc.queryForMap(
                "SELECT id, name FROM tag WHERE kind = 'EXPERIENCE' AND CHAR_LENGTH(name) >= 2 ORDER BY id LIMIT 1");
        String word = (String) tag.get("name");
        jdbc.update("UPDATE member SET name = ? WHERE id = ?", word, byName.id());
        jdbc.update("UPDATE member SET status_note = ? WHERE id = ?", word + " 공부 중", byNote.id());
        jdbc.update("INSERT INTO member_tag (member_id, tag_id) VALUES (?, ?)", byTag.id(), tag.get("id"));

        search(word, "MEMBER")
                .andExpect(jsonPath("$.items[*].id", contains((int) byTag.id(), (int) byNote.id(), (int) byName.id())));
        search(byName.nickname(), "MEMBER")
                .andExpect(jsonPath("$.items[*].id", contains((int) byName.id())));
    }

    @Test
    void 숨김_탈퇴_정지_부원은_빠진다() throws Exception {
        TestMember hidden = fixtures.member();
        TestMember suspended = fixtures.suspended();
        TestMember withdrawn = fixtures.withdrawn();
        TestMember visible = fixtures.member();
        jdbc.update("UPDATE member SET status_note = '마라탕 탐방' WHERE id IN (?, ?, ?, ?)", hidden.id(),
                suspended.id(), withdrawn.id(), visible.id());
        jdbc.update("UPDATE member SET hidden_from_list = TRUE WHERE id = ?", hidden.id());

        search("마라탕", "MEMBER").andExpect(jsonPath("$.items[*].id", contains((int) visible.id())));
        mvc.perform(get(URL).param("q", "마라탕").cookie(login))
                .andExpect(jsonPath("$.members.total").value(1));
    }

    @Test
    void 영어_불용어가_든_단어도_찾는다() throws Exception {
        long data = question("data 분석 질문", null);
        long about = question("about 페이지 질문", null);

        search("data", "QUESTION").andExpect(jsonPath("$.items[*].id", contains((int) data)));
        search("about", "QUESTION").andExpect(jsonPath("$.items[*].id", contains((int) about)));
        search("at", "QUESTION").andExpect(jsonPath("$.items[*].id", contains((int) data)));
    }

    @Test
    void 연산자_문자가_있어도_500이_나지_않는다() throws Exception {
        question("검색 대상 글", null);

        for (String q : List.of("+검색", "-검색", "검색*", "(검색", "검색)", "\"검색", "\"\"", "@검색", "~검색", "<검색", "검\\색", "%%",
                "''", "a'b")) {
            // 분당 30회 제한에 걸리지 않게 검색어마다 다른 부원으로
            mvc.perform(get(URL).param("q", q).cookie(fixtures.loginCookie(fixtures.member())))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void 태그_검색의_특수문자는_글자_그대로다() throws Exception {
        long tagged = share("다른 글", "본문입니다 충분히 길게");
        jdbc.update("INSERT INTO share_tag (share_id, name) VALUES (?, '100%')", tagged);
        long other = share("또 다른 글", "본문입니다 충분히 길게");
        jdbc.update("INSERT INTO share_tag (share_id, name) VALUES (?, '혼밥')", other);

        search("0%", "SHARE").andExpect(jsonPath("$.items[*].id", contains((int) tagged)));
        search("%%", "SHARE").andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void 검색어는_필수이고_2에서_30자() throws Exception {
        mvc.perform(get(URL).cookie(login)).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("q", " 가 ").cookie(login))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("q"));
        mvc.perform(get(URL).param("q", "가".repeat(31)).cookie(login)).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("q", "가나").param("type", "FEED").cookie(login))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 비회원은_401() throws Exception {
        mvc.perform(get(URL).param("q", "검색")).andExpect(status().isUnauthorized());
    }

    @Test
    void 부원당_분당_30회() throws Exception {
        for (int i = 0; i < 30; i++) {
            mvc.perform(get(URL).param("q", "검색").cookie(login)).andExpect(status().isOk());
        }
        mvc.perform(get(URL).param("q", "검색").cookie(login))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

}
