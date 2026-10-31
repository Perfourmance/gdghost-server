package com.gdghost.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.support.Fixtures;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

@IntegrationTest
@Import(RateLimitIntegrationTest.ProbeController.class)
class RateLimitIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Test
    void 부원당_횟수를_넘으면_429와_Retry_After() throws Exception {
        Cookie login = fixtures.loginCookie(fixtures.member());
        mvc.perform(get("/api/v1/probe/per-member").cookie(login)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/probe/per-member").cookie(login)).andExpect(status().isOk());

        mvc.perform(get("/api/v1/probe/per-member").cookie(login))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(result -> assertThat(Long.parseLong(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)))
                        .isBetween(1L, 60L));
    }

    @Test
    void 부원마다_따로_센다() throws Exception {
        Cookie first = fixtures.loginCookie(fixtures.member());
        Cookie second = fixtures.loginCookie(fixtures.member());
        mvc.perform(get("/api/v1/probe/per-member").cookie(first));
        mvc.perform(get("/api/v1/probe/per-member").cookie(first));

        mvc.perform(get("/api/v1/probe/per-member").cookie(second)).andExpect(status().isOk());
    }

    @Test
    void IP당_제한은_같은_IP의_부원끼리_합쳐_센다() throws Exception {
        mvc.perform(fromIp("10.0.0.1").cookie(fixtures.loginCookie(fixtures.member()))).andExpect(status().isOk());

        mvc.perform(fromIp("10.0.0.1").cookie(fixtures.loginCookie(fixtures.member())))
                .andExpect(status().isTooManyRequests());
        mvc.perform(fromIp("10.0.0.2").cookie(fixtures.loginCookie(fixtures.member())))
                .andExpect(status().isOk());
    }

    @Test
    void 제한이_여러_개면_각각_센다() throws Exception {
        Cookie login = fixtures.loginCookie(fixtures.member());
        mvc.perform(get("/api/v1/probe/two-limits").cookie(login)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/probe/two-limits").cookie(login)).andExpect(status().isOk());

        // 분당 5회에는 여유가 있고 하루 2회에 걸린다
        mvc.perform(get("/api/v1/probe/two-limits").cookie(login))
                .andExpect(status().isTooManyRequests())
                .andExpect(result -> assertThat(Long.parseLong(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)))
                        .isGreaterThan(3600L));
    }

    private static MockHttpServletRequestBuilder fromIp(String ip) {
        return get("/api/v1/probe/per-ip").with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    @RestController
    static class ProbeController {

        @RateLimit(limit = 2)
        @GetMapping("/api/v1/probe/per-member")
        void perMember() {
        }

        @RateLimit(limit = 5)
        @RateLimit(limit = 2, unit = ChronoUnit.DAYS)
        @GetMapping("/api/v1/probe/two-limits")
        void twoLimits() {
        }

        @RateLimit(limit = 1, per = RateLimit.Per.IP)
        @GetMapping("/api/v1/probe/per-ip")
        void perIp() {
        }

    }

}
