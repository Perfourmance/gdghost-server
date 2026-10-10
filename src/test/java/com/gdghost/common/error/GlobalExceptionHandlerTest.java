package com.gdghost.common.error;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;

/**
 * 보안 · DB 없이 에러 처리기만 검사한다.
 */
class GlobalExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void 서비스_에러는_Problem_모양으로_응답한다() throws Exception {
        mvc.perform(get("/test/nickname-taken"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://gdghost.dev/errors/nickname-taken"))
                .andExpect(jsonPath("$.title").value("이미 쓰는 닉네임"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("다른 닉네임을 입력해 주세요."))
                .andExpect(jsonPath("$.instance").value("/test/nickname-taken"))
                .andExpect(jsonPath("$.code").value("NICKNAME_TAKEN"))
                .andExpect(jsonPath("$.errors[0].field").value("profile.nickname"))
                .andExpect(jsonPath("$.errors[0].message").value("이미 쓰고 있는 닉네임입니다."));
    }

    @Test
    void detail이_없으면_title을_쓰고_errors는_빠진다() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("대상 없음"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void 본문_검증_실패는_칸별_사유와_함께_400() throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("nickname"));
    }

    @Test
    void 쿼리_파라미터_검증_실패도_400() throws Exception {
        mvc.perform(get("/test/page").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void JSON이_깨지면_400() throws Exception {
        mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{nickname"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 메서드가_틀리면_상태는_405로_두고_코드를_붙인다() throws Exception {
        mvc.perform(post("/test/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 예상_못_한_예외는_500이고_내부_메시지를_숨긴다() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(not(containsString("secret"))));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/nickname-taken")
        void nicknameTaken() {
            throw new BusinessException(ErrorCode.NICKNAME_TAKEN, "다른 닉네임을 입력해 주세요.",
                    List.of(new ErrorField("profile.nickname", "이미 쓰고 있는 닉네임입니다.")));
        }

        @GetMapping("/test/not-found")
        void notFound() {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        @PostMapping("/test/body")
        void body(@Valid @RequestBody Body body) {
        }

        @GetMapping("/test/page")
        void page(@RequestParam @Max(50) int size) {
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("secret db password");
        }

        record Body(@NotBlank String nickname) {
        }

    }

}
