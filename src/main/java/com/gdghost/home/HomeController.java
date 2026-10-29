package com.gdghost.home;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.security.AuthMember;
import com.gdghost.question.QuestionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "홈")
@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

    static final int RECENT_QUESTIONS = 3;

    private final QuestionService questionService;

    public HomeController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @Operation(summary = "홈", description = "비회원도 부른다. 비회원은 GuestHomeResponse(질문은 제목 · 작성자 · 답변 수까지). "
            + "최근 질문 3개만 지금 채우고, 공지(5단계) · 일정(8단계) · 공유(9단계)는 그 전까지 빈 배열.")
    @GetMapping
    public Object home(@AuthenticationPrincipal AuthMember me) {
        // TODO 고정 공지 · 미확인 필독 수(5) · 다가오는 일정(8) · 인기 공유(9)
        List<?> recentQuestions = questionService.list(me, null, false, false, null, RECENT_QUESTIONS).items();
        if (me == null) {
            return new GuestHomeResponse(List.of(), recentQuestions, List.of(), List.of());
        }
        return new HomeResponse(List.of(), 0, recentQuestions, List.of(), List.of());
    }

}
