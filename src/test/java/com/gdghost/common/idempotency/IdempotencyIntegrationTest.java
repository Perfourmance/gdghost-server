package com.gdghost.common.idempotency;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.support.Fixtures;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

@IntegrationTest
@Import(IdempotencyIntegrationTest.ProbeController.class)
class IdempotencyIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    Cookie login;

    @BeforeEach
    void setUp() {
        login = fixtures.loginCookie(fixtures.member());
    }

    @Test
    void 키가_없으면_400() throws Exception {
        mvc.perform(post("/api/v1/probe/items").cookie(login).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
    }

    @Test
    void 같은_키_같은_내용이면_두_번_만들지_않고_처음_응답을_준다() throws Exception {
        String id = create(login, "k1", "a").andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        create(login, "k1", "a")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", id));
    }

    @Test
    void 같은_키에_다른_내용이면_409() throws Exception {
        create(login, "k2", "a").andExpect(status().isCreated());

        create(login, "k2", "b")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void 다른_부원의_같은_키는_따로_만든다() throws Exception {
        String first = create(login, "k3", "a").andReturn().getResponse().getHeader("Location");

        create(fixtures.loginCookie(fixtures.member()), "k3", "a")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", not(first)));
    }

    @Test
    void 실패한_요청의_키는_다시_쓸_수_있다() throws Exception {
        create(login, "k4", "fail").andExpect(status().isBadRequest());

        create(login, "k4", "a").andExpect(status().isCreated());
    }

    private ResultActions create(Cookie cookie, String key, String title) throws Exception {
        return mvc.perform(post("/api/v1/probe/items").cookie(cookie).with(csrfToken())
                .header(IdempotencyAspect.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"));
    }

    @RestController
    static class ProbeController {

        private final AtomicLong sequence = new AtomicLong();

        record Item(String title) {
        }

        @Idempotent
        @PostMapping("/api/v1/probe/items")
        ResponseEntity<Item> create(@RequestBody Item item) {
            if (item.title().equals("fail")) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            return ResponseEntity.created(URI.create("/api/v1/probe/items/" + sequence.incrementAndGet())).body(item);
        }

    }

}
