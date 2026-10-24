package com.gdghost.question;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.idempotency.Idempotent;
import com.gdghost.common.page.CursorPage;
import com.gdghost.common.ratelimit.RateLimit;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "질문")
@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @Operation(summary = "질문 작성", description = "Idempotency-Key 필수. 익명 여부는 나중에 바꿀 수 없다. 부원당 분당 3회. "
            + "선배 지목(targetMemberId)은 7단계부터라 directDelivery는 지금 항상 null.")
    @Idempotent
    @RateLimit(limit = 3)
    @PostMapping
    public ResponseEntity<QuestionCreatedResponse> create(@AuthenticationPrincipal AuthMember me,
            @RequestBody @Valid QuestionCreateRequest request) {
        QuestionCreatedResponse response = questionService.create(me.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/questions/" + response.question().id())).body(response);
    }

    @Operation(summary = "질문 목록", description = "최신순, 삭제 · 숨김 제외. 본문 · 답변 내용은 담지 않는다. "
            + "비회원은 GuestQuestionSummary(제목 · 작성자 · 답변 수만), mine=true면 401. "
            + "unanswered는 답변을 기다리는(WAITING) 질문만.")
    @GetMapping
    public CursorPage<?> list(@AuthenticationPrincipal AuthMember me,
            @RequestParam(required = false) QuestionCategory category,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "false") boolean unanswered,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_SIZE) int size) {
        return questionService.list(me, category, mine, unanswered, cursor, size);
    }

    @Operation(summary = "질문 상세", description = "답변 트리 포함(채택 먼저, 나머지 오래된 순, depth 3까지). "
            + "조회수는 부원당 하루 1회, 글쓴이 본인은 세지 않는다. 없거나 삭제 · 숨김이면 404. routing은 7단계부터.")
    @GetMapping("/{questionId}")
    public QuestionDetail detail(@AuthenticationPrincipal AuthMember me, @PathVariable long questionId) {
        return questionService.detail(me.id(), questionId);
    }

}
