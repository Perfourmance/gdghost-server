package com.gdghost.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.gdghost.support.Fixtures;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/**
 * API 설계서(노션) API 목록의 권한 · 비회원 처리를 표(api-permissions.csv)로 두고 한 번에 검사한다.
 * 보안 필터가 컨트롤러보다 먼저 거르므로 아직 만들지 않은 API도 거부 응답은 검사된다.
 */
@IntegrationTest
class ApiPermissionTableTest {

    private static final List<ApiPermission> TABLE = load();

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Test
    void 표가_API_목록과_맞다() {
        assertThat(TABLE).hasSize(128);
        assertThat(TABLE.stream().map(ApiPermission::name).collect(Collectors.toSet())).hasSize(TABLE.size());

        Set<String> accesses = Set.of("PUBLIC", "MEMBER", "STAFF", "DEV");
        for (ApiPermission api : TABLE) {
            assertThat(api.access()).as(api.name()).isIn(accesses);
            assertThat(api.path()).as(api.name()).startsWith("/api/v1/");

            if (api.path().startsWith("/api/v1/ops/")) {
                assertThat(api.access()).as(api.name()).isEqualTo("STAFF");
            }
            switch (api.access()) {
                case "PUBLIC" -> assertThat(api.guest()).as(api.name()).isIn("허용", "제목만");
                case "DEV" -> {
                    assertThat(api.path()).as(api.name()).startsWith("/api/v1/admin/");
                    assertThat(api.guest()).as(api.name()).isEqualTo("404");
                }
                default -> assertThat(api.guest()).as(api.name()).isEqualTo("401");
            }
        }
    }

    @Disabled("#11 쿠키 인증에서 켠다")
    @ParameterizedTest(name = "{0}")
    @MethodSource("guestRejected")
    void 비회원은_401(ApiPermission api) throws Exception {
        mvc.perform(api.toRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Disabled("#13 권한에서 켠다")
    @ParameterizedTest(name = "{0}")
    @MethodSource("staffOnly")
    void 운영진_API는_부원과_개발자에게_403(ApiPermission api) throws Exception {
        for (Cookie cookie : List.of(fixtures.loginCookie(fixtures.member()), fixtures.loginCookie(fixtures.dev()))) {
            mvc.perform(api.toRequest().cookie(cookie))
                    .andExpect(status().isForbidden());
        }
    }

    @Disabled("#13 권한에서 켠다")
    @ParameterizedTest(name = "{0}")
    @MethodSource("devOnly")
    void 어드민_API는_개발자가_아니면_404(ApiPermission api) throws Exception {
        mvc.perform(api.toRequest())
                .andExpect(status().isNotFound());
        for (Cookie cookie : List.of(fixtures.loginCookie(fixtures.member()), fixtures.loginCookie(fixtures.staff()))) {
            mvc.perform(api.toRequest().cookie(cookie))
                    .andExpect(status().isNotFound());
        }
    }

    static Stream<ApiPermission> guestRejected() {
        return TABLE.stream().filter(api -> api.guest().equals("401"));
    }

    static Stream<ApiPermission> staffOnly() {
        return TABLE.stream().filter(api -> api.access().equals("STAFF"));
    }

    static Stream<ApiPermission> devOnly() {
        return TABLE.stream().filter(api -> api.access().equals("DEV"));
    }

    record ApiPermission(String method, String path, String access, String guest, int stage) {

        String name() {
            return method + " " + path;
        }

        /** 경로 변수는 1로 채운다. 쓰기 요청엔 CSRF 토큰을 붙여 CSRF 검사가 401 · 403을 가리지 않게 한다 */
        MockHttpServletRequestBuilder toRequest() {
            MockHttpServletRequestBuilder builder =
                    request(HttpMethod.valueOf(method), path.replaceAll("\\{[^}]+}", "1"));
            return method.equals("GET") ? builder : builder.with(csrf());
        }

        @Override
        public String toString() {
            return name();
        }

    }

    private static List<ApiPermission> load() {
        try (InputStream in = ApiPermissionTableTest.class.getResourceAsStream("/api-permissions.csv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(line -> line.split(","))
                    .map(c -> new ApiPermission(c[0], c[1], c[2], c[3], Integer.parseInt(c[4])))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
