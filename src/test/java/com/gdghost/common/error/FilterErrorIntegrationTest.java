package com.gdghost.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.gdghost.TestcontainersConfiguration;
import com.gdghost.common.security.LoginTokenService;
import com.gdghost.common.security.SessionCookie;
import com.gdghost.support.DatabaseCleaner;

/**
 * 필터에서 난 예외는 톰캣이 /error로 다시 보내는데, MockMvc는 이걸 흉내 내지 않아 실제 서버로 검사한다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ExtendWith(DatabaseCleaner.class)
class FilterErrorIntegrationTest {

    @LocalServerPort
    int port;

    @MockitoBean
    LoginTokenService tokens;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 로그인_필터에서_난_예외는_500_INTERNAL_ERROR이고_error_log에_남는다() throws Exception {
        given(tokens.authenticate(anyString())).willThrow(new DataAccessResourceFailureException("DB 연결 끊김"));

        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/auth/me"))
                .header("Cookie", SessionCookie.NAME + "=some-token")
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).contains("\"code\":\"INTERNAL_ERROR\"").doesNotContain("DB 연결 끊김");
        assertThat(jdbc.queryForList("SELECT path FROM error_log", String.class)).containsExactly("/api/v1/auth/me");
    }

}
