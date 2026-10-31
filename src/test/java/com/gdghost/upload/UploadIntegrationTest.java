package com.gdghost.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.support.FakeObjectStorage;
import com.gdghost.support.Fixtures;
import com.gdghost.support.IntegrationTest;
import com.gdghost.support.TestCsrf;
import com.jayway.jsonpath.JsonPath;

@IntegrationTest
class UploadIntegrationTest {

    private static final String URL = "/api/v1/uploads/presigned-urls";
    private static final long OWNER = 100L;

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    FakeObjectStorage storage;

    @Autowired
    AttachmentService attachments;

    @Autowired
    UploadCleanupJob cleanupJob;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        storage.clear();
    }

    private ResultActions request(Fixtures.TestMember member, String body) throws Exception {
        return mvc.perform(post(URL).cookie(fixtures.loginCookie(member)).with(TestCsrf.csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String files(String purpose, String... files) {
        return """
                {"purpose":"%s","files":[%s]}""".formatted(purpose, String.join(",", files));
    }

    private static String file(String contentType, long size) {
        return """
                {"contentType":"%s","size":%d}""".formatted(contentType, size);
    }

    private String uploaded(UploadPurpose purpose, long memberId, long size) {
        String key = UploadKeys.generate(purpose, memberId, "webp", Instant.now());
        storage.put(key, size, Instant.now());
        return key;
    }

    @Test
    void 사진마다_주소와_키를_준다() throws Exception {
        var member = fixtures.member();

        String body = request(member, files("FEED", file("image/webp", 284000), file("image/jpeg", 1024 * 1024)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].method").value("PUT"))
                .andExpect(jsonPath("$.items[0].headers['Content-Type']").value("image/webp"))
                .andExpect(jsonPath("$.items[0].expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String key = JsonPath.read(body, "$.items[0].objectKey");
        String url = JsonPath.read(body, "$.items[0].uploadUrl");
        assertThat(key).matches("feed/\\d{4}/\\d{2}/%d/[0-9a-f]{32}\\.webp".formatted(member.id()));
        assertThat(url).contains(key).contains("size=284000").contains("ttl=600");
        assertThat((String) JsonPath.read(body, "$.items[1].objectKey")).endsWith(".jpg");
    }

    @Test
    void 허용하지_않는_형식이면_400() throws Exception {
        request(fixtures.member(), files("FEED", file("image/gif", 100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_TYPE_NOT_ALLOWED"));
    }

    @Test
    void 크기가_1MB를_넘으면_400() throws Exception {
        request(fixtures.member(), files("CERT", file("image/png", 1024 * 1024 + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_TOO_LARGE"));
    }

    @Test
    void 파일이_없거나_4개_이상이면_400() throws Exception {
        var member = fixtures.member();
        String one = file("image/png", 1);
        request(member, files("SHARE")).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        request(member, files("SHARE", one, one, one, one)).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        request(member, files("SHARE", file("image/png", 0))).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 분당_파일_20개를_넘으면_429() throws Exception {
        var member = fixtures.member();
        String three = IntStream.range(0, 3).mapToObj(i -> file("image/png", 1)).collect(Collectors.joining(","));
        for (int i = 0; i < 6; i++) {
            request(member, files("FEED", three)).andExpect(status().isOk());
        }
        request(member, files("FEED", file("image/png", 1), file("image/png", 1))).andExpect(status().isOk());

        request(member, files("FEED", file("image/png", 1)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void 있는_사진을_순서대로_붙이고_정각_주소로_보여_준다() {
        long member = fixtures.member().id();
        String first = uploaded(UploadPurpose.FEED, member, 100);
        String second = uploaded(UploadPurpose.FEED, member, UploadService.MAX_SIZE);

        attachments.attach(UploadPurpose.FEED, OWNER, member, List.of(first, second));

        Map<Long, List<Attachment>> photos = attachments.photos(UploadPurpose.FEED, List.of(OWNER, OWNER + 1));
        assertThat(photos).containsOnlyKeys(OWNER);
        assertThat(photos.get(OWNER)).extracting(Attachment::objectKey).containsExactly(first, second);
        assertThat(photos.get(OWNER)).extracting(Attachment::sortOrder).containsExactly(0, 1);
        Attachment photo = photos.get(OWNER).getFirst();
        assertThat(photo.url()).contains("method=GET");
        assertThat(photo.urlExpiresAt().getEpochSecond() % 3600).isZero();
        assertThat(attachments.photos(UploadPurpose.SHARE, List.of(OWNER))).isEmpty();
    }

    private void assertNotFound(UploadPurpose purpose, long member, List<String> keys) {
        assertThatThrownBy(() -> attachments.attach(purpose, OWNER, member, keys))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.UPLOAD_NOT_FOUND));
    }

    @Test
    void 없거나_남의_것이거나_다른_용도거나_이미_쓴_키는_붙이지_않는다() {
        long member = fixtures.member().id();
        long other = fixtures.member().id();

        assertNotFound(UploadPurpose.FEED, member,
                List.of(UploadKeys.generate(UploadPurpose.FEED, member, "webp", Instant.now())));
        assertNotFound(UploadPurpose.FEED, member, List.of(uploaded(UploadPurpose.FEED, other, 1)));
        assertNotFound(UploadPurpose.CERT, member, List.of(uploaded(UploadPurpose.FEED, member, 1)));
        assertNotFound(UploadPurpose.FEED, member, List.of("../secret.webp"));

        String key = uploaded(UploadPurpose.FEED, member, 1);
        assertNotFound(UploadPurpose.FEED, member, List.of(key, key));
        attachments.attach(UploadPurpose.FEED, OWNER, member, List.of(key));
        assertNotFound(UploadPurpose.FEED, member, List.of(key));
    }

    @Test
    void 실제_크기가_1MB를_넘으면_지우고_거절한다() {
        long member = fixtures.member().id();
        String key = uploaded(UploadPurpose.CERT, member, UploadService.MAX_SIZE + 1);

        assertThatThrownBy(() -> attachments.attach(UploadPurpose.CERT, OWNER, member, List.of(key)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.UPLOAD_TOO_LARGE));

        assertThat(storage.exists(key)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment", Integer.class)).isZero();
    }

    @Test
    void 정리_배치는_하루_지난_미연결_사진만_지운다() {
        long member = fixtures.member().id();
        Instant old = Instant.now().minusSeconds(25 * 3600);
        String orphan = UploadKeys.generate(UploadPurpose.FEED, member, "webp", old);
        String attached = UploadKeys.generate(UploadPurpose.FEED, member, "webp", old);
        String recent = uploaded(UploadPurpose.FEED, member, 1);
        String foreign = "backup/keep.zip";
        storage.put(orphan, 1, old);
        storage.put(attached, 1, old);
        storage.put(foreign, 1, old);
        attachments.attach(UploadPurpose.FEED, OWNER, member, List.of(attached));

        cleanupJob.run();

        assertThat(storage.deleted()).containsExactly(orphan);
        assertThat(storage.exists(attached)).isTrue();
        assertThat(storage.exists(recent)).isTrue();
        assertThat(storage.exists(foreign)).isTrue();
    }

}
