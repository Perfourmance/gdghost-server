package com.gdghost.share;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.idempotency.IdempotencyAspect;
import com.gdghost.support.FakeObjectStorage;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class ShareIntegrationTest {

    private static final String URL = "/api/v1/shares";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeObjectStorage storage;

    TestMember author;
    TestMember other;
    Cookie login;
    Cookie otherLogin;

    @BeforeEach
    void setUp() {
        storage.clear();
        author = fixtures.member();
        other = fixtures.member();
        login = fixtures.loginCookie(author);
        otherLogin = fixtures.loginCookie(other);
    }

    private ResultActions create(Cookie cookie, String key, String body) throws Exception {
        return mvc.perform(post(URL).cookie(cookie).with(csrfToken()).header(IdempotencyAspect.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions create(String body) throws Exception {
        return create(login, UUID.randomUUID().toString(), body);
    }

    private ResultActions update(Cookie cookie, long id, String body) throws Exception {
        return mvc.perform(patch(URL + "/{id}", id).cookie(cookie).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions remove(Cookie cookie, long id) throws Exception {
        return mvc.perform(delete(URL + "/{id}", id).cookie(cookie).with(csrfToken()));
    }

    private static String body(String extra) {
        return """
                {"category":"FOOD","title":"학교 앞 순대국집","body":"가격대는 만원 이하예요"%s}""".formatted(extra);
    }

    /** R2에 올라간 내 사진 */
    private String photo(TestMember member) {
        String key = "share/2026/10/%d/%s.jpg".formatted(member.id(), UUID.randomUUID().toString().replace("-", ""));
        storage.put(key, 1000, Instant.now());
        return key;
    }

    private void attach(long shareId, String key) {
        jdbc.update("INSERT INTO attachment (owner_type, owner_id, object_key, sort_order) VALUES ('SHARE', ?, ?, 0)",
                shareId, key);
    }

    private int attachmentCount(long shareId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM attachment WHERE owner_type = 'SHARE' AND owner_id = ?",
                Integer.class, shareId);
    }

    private void savedBy(long shareId, long count) {
        jdbc.update("UPDATE share SET save_count = ? WHERE id = ?", count, shareId);
    }

    private void hide(long shareId) {
        jdbc.update("UPDATE share SET deleted_at = NOW(6), deleted_by = 'STAFF' WHERE id = ?", shareId);
    }

    private void note(long shareId, long authorId, String body) {
        jdbc.update("INSERT INTO share_note (share_id, author_id, body) VALUES (?, ?, ?)", shareId, authorId, body);
    }

    // 작성

    @Test
    void 작성하면_상세_모양으로_돌려준다() throws Exception {
        String key = photo(author);
        String response = create(body(",\"tags\":[\" 혼밥 \",\"혼밥\",\"Java\",\"java\"],\"photoKeys\":[\"" + key + "\"]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("FOOD"))
                .andExpect(jsonPath("$.tags", hasSize(2)))
                .andExpect(jsonPath("$.photos[0].objectKey").value(key))
                .andExpect(jsonPath("$.photos[0].url", containsString("method=GET")))
                .andExpect(jsonPath("$.author.id").value(author.id()))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.saveCount").value(0))
                .andExpect(jsonPath("$.notes", hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(response, "$.id");
        assertThat(jdbc.queryForObject("SELECT author_id FROM share WHERE id = ?", Long.class, id))
                .isEqualTo(author.id());
    }

    @Test
    void 작성은_위치_헤더와_멱등_키를_쓴다() throws Exception {
        String key = UUID.randomUUID().toString();
        String location = create(login, key, body("")).andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).startsWith(URL + "/");

        create(login, key, body("")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM share", Integer.class)).isEqualTo(1);
    }

    @Test
    void 잘못된_입력은_400() throws Exception {
        for (String invalid : List.of(
                "{\"title\":\"제목입니다\",\"body\":\"본문은 열 글자 이상\"}",
                "{\"category\":\"FOOD\",\"title\":\" 가 \",\"body\":\"본문은 열 글자 이상\"}",
                "{\"category\":\"FOOD\",\"title\":\"제목입니다\",\"body\":\"짧은 본문\"}",
                body(",\"tags\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]"),
                body(",\"tags\":[\"   \"]"),
                body(",\"tags\":[\"열다섯 글자를 넘어가는 아주 긴 태그\"]"),
                body(",\"photoKeys\":[\"a\",\"b\",\"c\",\"d\"]"))) {
            mvc.perform(post(URL).cookie(fixtures.loginCookie(fixtures.member())).with(csrfToken())
                    .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void 남의_사진이나_없는_사진이면_400이고_글이_남지_않는다() throws Exception {
        create(body(",\"photoKeys\":[\"" + photo(other) + "\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_FOUND"));
        String missing = photo(author);
        storage.delete(missing);
        create(body(",\"photoKeys\":[\"" + missing + "\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM share", Integer.class)).isZero();
    }

    @Test
    void 작성은_분당_3회() throws Exception {
        for (int i = 0; i < 3; i++) {
            create(body("")).andExpect(status().isCreated());
        }
        create(body("")).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    // 목록

    @Test
    void 기본은_저장순이고_같은_저장_수도_커서로_빠짐없이_넘어간다() throws Exception {
        long a = fixtures.share(author.id());
        long b = fixtures.share(author.id());
        long c = fixtures.share(author.id());
        long d = fixtures.share(author.id());
        savedBy(a, 5);
        savedBy(b, 3);
        savedBy(c, 3);
        savedBy(d, 1);

        String first = mvc.perform(get(URL).param("size", "2").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) a, (int) c)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get(URL).param("size", "2").param("cursor", JsonPath.<String>read(first, "$.nextCursor"))
                .cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) b, (int) d)))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void 최신순과_분류_필터() throws Exception {
        long a = fixtures.share(author.id());
        long b = fixtures.share(author.id());
        savedBy(a, 9);
        jdbc.update("UPDATE share SET category = 'FOOD' WHERE id = ?", a);

        String first = mvc.perform(get(URL).param("sort", "LATEST").param("size", "1").cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) b)))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get(URL).param("sort", "LATEST").param("size", "1")
                .param("cursor", JsonPath.<String>read(first, "$.nextCursor")).cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) a)));
        mvc.perform(get(URL).param("category", "FOOD").cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) a)));
    }

    @Test
    void 다른_정렬의_커서나_깨진_커서는_400() throws Exception {
        fixtures.share(author.id());
        fixtures.share(author.id());
        String saved = mvc.perform(get(URL).param("size", "1").cookie(login))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(saved, "$.nextCursor");

        mvc.perform(get(URL).param("sort", "LATEST").param("cursor", cursor).cookie(login))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cursor"));
        mvc.perform(get(URL).param("cursor", "!!!").cookie(login)).andExpect(status().isBadRequest());
    }

    @Test
    void 목록_항목은_요약만_담고_지운_글은_뺀다() throws Exception {
        long share = fixtures.share(author.id());
        long deleted = fixtures.share(author.id());
        long hidden = fixtures.share(author.id());
        jdbc.update("UPDATE share SET deleted_at = NOW(6), deleted_by = 'AUTHOR' WHERE id = ?", deleted);
        hide(hidden);
        jdbc.update("INSERT INTO share_tag (share_id, name) VALUES (?, '혼밥')", share);
        jdbc.update("INSERT INTO share_save (share_id, member_id) VALUES (?, ?)", share, other.id());
        note(share, other.id(), "덧붙임");
        note(share, other.id(), "지운 덧붙임");
        jdbc.update("UPDATE share_note SET deleted_at = NOW(6) WHERE body = '지운 덧붙임'");
        attach(share, photo(author));

        mvc.perform(get(URL).cookie(otherLogin))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(share))
                .andExpect(jsonPath("$.items[0].tags[0]").value("혼밥"))
                .andExpect(jsonPath("$.items[0].noteCount").value(1))
                .andExpect(jsonPath("$.items[0].saved").value(true))
                .andExpect(jsonPath("$.items[0].hasPhoto").value(true))
                .andExpect(jsonPath("$.items[0].author.nickname").value(author.nickname()))
                .andExpect(jsonPath("$.items[0].body").doesNotExist())
                .andExpect(jsonPath("$.items[0].photos").doesNotExist());
        mvc.perform(get(URL).cookie(login))
                .andExpect(jsonPath("$.items[0].saved").value(false));
    }

    @Test
    void 검색은_제목과_태그에서_찾고_특수문자는_글자_그대로다() throws Exception {
        long byTitle = fixtures.share(author.id());
        jdbc.update("UPDATE share SET title = '순대국 맛집' WHERE id = ?", byTitle);
        long byTag = fixtures.share(author.id());
        jdbc.update("INSERT INTO share_tag (share_id, name) VALUES (?, '순대국')", byTag);
        long percent = fixtures.share(author.id());
        jdbc.update("UPDATE share SET title = '할인 100% 쿠폰' WHERE id = ?", percent);
        long byBody = fixtures.share(author.id());
        jdbc.update("UPDATE share SET body = '순대국은 본문에만 있어요' WHERE id = ?", byBody);

        mvc.perform(get(URL).param("q", " 순대국 ").cookie(login))
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder((int) byTitle, (int) byTag)));
        mvc.perform(get(URL).param("q", "0%").cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) percent)));
        mvc.perform(get(URL).param("q", "%%").cookie(login))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void 검색어는_2에서_30자() throws Exception {
        mvc.perform(get(URL).param("q", "가").cookie(login))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("q"));
        mvc.perform(get(URL).param("q", "가".repeat(31)).cookie(login)).andExpect(status().isBadRequest());
    }

    @Test
    void 비회원_목록은_제목까지만이고_검색은_401() throws Exception {
        long share = fixtures.share(author.id());
        attach(share, photo(author));
        jdbc.update("INSERT INTO share_tag (share_id, name) VALUES (?, '혼밥')", share);

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(share))
                .andExpect(jsonPath("$.items[0].title").exists())
                .andExpect(jsonPath("$.items[0].author.nickname").value(author.nickname()))
                .andExpect(jsonPath("$.items[0].author.id").doesNotExist())
                .andExpect(jsonPath("$.items[0].tags").doesNotExist())
                .andExpect(jsonPath("$.items[0].saveCount").doesNotExist())
                .andExpect(jsonPath("$.items[0].hasPhoto").doesNotExist());
        mvc.perform(get(URL).param("q", "제목"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 탈퇴한_작성자는_탈퇴한_부원으로_보인다() throws Exception {
        TestMember gone = fixtures.withdrawn();
        long share = fixtures.share(gone.id());

        mvc.perform(get(URL))
                .andExpect(jsonPath("$.items[0].author.nickname").value("탈퇴한 부원"));
        mvc.perform(get(URL).cookie(login))
                .andExpect(jsonPath("$.items[0].author.withdrawn").value(true))
                .andExpect(jsonPath("$.items[0].author.id").isEmpty());
        mvc.perform(get(URL + "/{id}", share).cookie(login))
                .andExpect(jsonPath("$.author.nickname").value("탈퇴한 부원"));
    }

    // 상세

    @Test
    void 상세는_사진_주소와_덧붙이기를_담는다() throws Exception {
        long share = fixtures.share(author.id());
        String key = photo(author);
        attach(share, key);
        note(share, other.id(), "먼저 쓴 글");
        note(share, author.id(), "나중에 쓴 글");
        note(share, other.id(), "지운 글");
        jdbc.update("UPDATE share_note SET deleted_at = NOW(6) WHERE body = '지운 글'");

        mvc.perform(get(URL + "/{id}", share).cookie(otherLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").exists())
                .andExpect(jsonPath("$.mine").value(false))
                .andExpect(jsonPath("$.photos[0].url", containsString(key)))
                .andExpect(jsonPath("$.photos[0].urlExpiresAt").exists())
                .andExpect(jsonPath("$.notes[*].body", contains("먼저 쓴 글", "나중에 쓴 글")))
                .andExpect(jsonPath("$.notes[0].mine").value(true))
                .andExpect(jsonPath("$.notes[0].author.nickname").value(other.nickname()))
                .andExpect(jsonPath("$.notes[1].mine").value(false));
    }

    @Test
    void 없거나_지웠거나_숨긴_글은_본인에게도_404() throws Exception {
        long hidden = fixtures.share(author.id());
        hide(hidden);
        mvc.perform(get(URL + "/{id}", hidden).cookie(login)).andExpect(status().isNotFound());
        mvc.perform(get(URL + "/{id}", 999_999).cookie(login)).andExpect(status().isNotFound());
    }

    // 수정

    @Test
    void 보낸_필드만_바꾸고_저장_수는_그대로다() throws Exception {
        long share = fixtures.share(author.id());
        savedBy(share, 7);
        jdbc.update("INSERT INTO share_tag (share_id, name) VALUES (?, '혼밥')", share);
        String body = jdbc.queryForObject("SELECT body FROM share WHERE id = ?", String.class, share);

        update(login, share, "{\"title\":\"새 제목입니다\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("새 제목입니다"))
                .andExpect(jsonPath("$.body").value(body))
                .andExpect(jsonPath("$.category").value("TIP"))
                .andExpect(jsonPath("$.tags[0]").value("혼밥"))
                .andExpect(jsonPath("$.saveCount").value(7))
                .andExpect(jsonPath("$.updatedAt").exists());
        assertThat(jdbc.queryForObject("SELECT save_count FROM share WHERE id = ?", Long.class, share)).isEqualTo(7);
    }

    @Test
    void 같은_값이면_수정_시각을_찍지_않는다() throws Exception {
        long share = fixtures.share(author.id());
        String title = jdbc.queryForObject("SELECT title FROM share WHERE id = ?", String.class, share);

        update(login, share, "{\"title\":\"" + title + "\",\"tags\":[],\"photoKeys\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").isEmpty());
    }

    @Test
    void 태그와_사진은_통째로_교체하고_빈_배열은_지운다() throws Exception {
        long share = fixtures.share(author.id());
        jdbc.update("INSERT INTO share_tag (share_id, name) VALUES (?, '옛태그')", share);
        String kept = photo(author);
        String removed = photo(author);
        attach(share, kept);
        attach(share, removed);
        String added = photo(author);

        update(login, share, "{\"tags\":[\"새태그\"],\"photoKeys\":[\"" + added + "\",\"" + kept + "\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[*]", contains("새태그")))
                .andExpect(jsonPath("$.photos[*].objectKey", contains(added, kept)))
                .andExpect(jsonPath("$.updatedAt").exists());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment WHERE object_key = ?", Integer.class, removed))
                .isZero();

        update(login, share, "{\"tags\":[],\"photoKeys\":[]}")
                .andExpect(jsonPath("$.tags", hasSize(0)))
                .andExpect(jsonPath("$.photos", hasSize(0)));
        assertThat(attachmentCount(share)).isZero();
    }

    @Test
    void 사진_교체가_실패하면_글도_그대로다() throws Exception {
        long share = fixtures.share(author.id());
        String key = photo(author);
        attach(share, key);
        String title = jdbc.queryForObject("SELECT title FROM share WHERE id = ?", String.class, share);

        update(login, share, "{\"title\":\"새 제목입니다\",\"photoKeys\":[\"" + photo(other) + "\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_FOUND"));

        assertThat(jdbc.queryForObject("SELECT title FROM share WHERE id = ?", String.class, share)).isEqualTo(title);
        assertThat(attachmentCount(share)).isEqualTo(1);
    }

    @Test
    void 수정_에러() throws Exception {
        long share = fixtures.share(author.id());
        long hidden = fixtures.share(other.id());
        hide(hidden);

        update(login, share, "{}").andExpect(status().isBadRequest());
        update(login, share, "{\"title\":\"가\"}").andExpect(status().isBadRequest());
        update(otherLogin, share, "{\"title\":\"새 제목입니다\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHOR"));
        update(login, hidden, "{\"title\":\"새 제목입니다\"}").andExpect(status().isNotFound());
        update(login, 999_999, "{\"title\":\"새 제목입니다\"}").andExpect(status().isNotFound());
    }

    // 삭제

    @Test
    void 삭제하면_소프트_삭제되고_사진_연결이_끊긴다() throws Exception {
        long share = fixtures.share(author.id());
        attach(share, photo(author));

        remove(login, share).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT deleted_by FROM share WHERE id = ?", String.class, share))
                .isEqualTo("AUTHOR");
        assertThat(attachmentCount(share)).isZero();
        remove(login, share).andExpect(status().isNotFound());
    }

    @Test
    void 남의_글은_403이고_사진은_그대로다() throws Exception {
        long share = fixtures.share(author.id());
        attach(share, photo(author));

        remove(otherLogin, share)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHOR"));
        assertThat(attachmentCount(share)).isEqualTo(1);
    }

    // 신고

    private long report(long share) throws Exception {
        String response = mvc.perform(post("/api/v1/reports").cookie(otherLogin).with(csrfToken())
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"SHARE\",\"targetId\":" + share + ",\"reason\":\"광고 글\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions handle(Cookie staff, long reportId, String action) throws Exception {
        return mvc.perform(patch("/api/v1/ops/reports/{id}", reportId).cookie(staff).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"" + action + "\"}"));
    }

    @Test
    void 신고로_숨기고_되돌리면_사진도_다시_보인다() throws Exception {
        long share = fixtures.share(author.id());
        String key = photo(author);
        attach(share, key);
        Cookie staff = fixtures.loginCookie(fixtures.staff());
        long reportId = report(share);

        handle(staff, reportId, "HIDE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetPreview").exists())
                .andExpect(jsonPath("$.targetLink").value("/shares/" + share))
                .andExpect(jsonPath("$.targetDeletedBy").value("STAFF"));
        mvc.perform(get(URL + "/{id}", share).cookie(login)).andExpect(status().isNotFound());
        assertThat(attachmentCount(share)).isEqualTo(1);

        handle(staff, reportId, "RESTORE").andExpect(status().isOk());
        mvc.perform(get(URL + "/{id}", share).cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos[0].objectKey").value(key));
    }

    @Test
    void 작성자가_지운_글은_신고할_수_없고_되돌릴_수도_없다() throws Exception {
        long share = fixtures.share(author.id());
        Cookie staff = fixtures.loginCookie(fixtures.staff());
        long reportId = report(share);

        remove(login, share).andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/reports").cookie(fixtures.loginCookie(fixtures.member())).with(csrfToken())
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"SHARE\",\"targetId\":" + share + ",\"reason\":\"광고 글\"}"))
                .andExpect(status().isNotFound());
        handle(staff, reportId, "HIDE").andExpect(status().isOk());
        handle(staff, reportId, "RESTORE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_HANDLED"));
    }

    // 탈퇴

    @Test
    void 탈퇴하면_글은_남고_사진_연결만_끊긴다() throws Exception {
        long share = fixtures.share(author.id());
        attach(share, photo(author));

        mvc.perform(post("/api/v1/me/withdrawal").cookie(login).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + Fixtures.PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM share WHERE id = ?", Boolean.class, share))
                .isTrue();
        assertThat(attachmentCount(share)).isZero();
    }

}
