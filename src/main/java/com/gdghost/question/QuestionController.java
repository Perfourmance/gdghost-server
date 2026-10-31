package com.gdghost.question;

import java.net.URI;
import java.time.temporal.ChronoUnit;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final AdoptionService adoptionService;

    public QuestionController(QuestionService questionService, AdoptionService adoptionService) {
        this.questionService = questionService;
        this.adoptionService = adoptionService;
    }

    @Operation(summary = "질문 작성", description = "Idempotency-Key 필수. 익명 여부는 나중에 바꿀 수 없다. 부원당 분당 3회 · 하루 30회(첫 작성부터 24시간). "
            + "targetMemberId(선배 지목)가 있으면 directDelivery: SENT · SCHEDULED(22–08시) · FALLBACK(받을 수 없음 → 일반 라우팅).")
    @Idempotent
    @RateLimit(limit = 3)
    @RateLimit(limit = 30, unit = ChronoUnit.DAYS)
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
            + "조회수는 부원당 하루 1회, 글쓴이 본인은 세지 않는다. 없거나 삭제 · 숨김이면 404. routing은 전달 현황, 없으면 null. 익명 질문은 reasons 빈 배열 + reasonsHidden.")
    @GetMapping("/{questionId}")
    public QuestionDetail detail(@AuthenticationPrincipal AuthMember me, @PathVariable long questionId) {
        return questionService.detail(me.id(), questionId);
    }

    @Operation(summary = "질문 수정", description = "본인 질문만. 보낸 필드만 바꾸고(null은 그대로), 빈 본문(\"\")은 지운다. "
            + "익명 여부는 바꿀 수 없어 보내면 400. 지우지 않은 답변 · 답글이 있을 때 내용이 바뀌면 editedAfterAnswer = true. "
            + "부원당 하루 20회(첫 수정부터 24시간).")
    @RateLimit(limit = 20, unit = ChronoUnit.DAYS)
    @PatchMapping("/{questionId}")
    public QuestionDetail update(@AuthenticationPrincipal AuthMember me, @PathVariable long questionId,
            @RequestBody @Valid QuestionUpdateRequest request) {
        return questionService.update(me.id(), questionId, request);
    }

    @Operation(summary = "답변 채택", description = "질문자만. 이 질문의 지우지 않은 답변(답글 아님)만, 이미 채택한 게 있으면 바꾼다. "
            + "아니면 400 ADOPT_INVALID.")
    @PutMapping("/{questionId}/adoption")
    public AdoptionResponse adopt(@AuthenticationPrincipal AuthMember me, @PathVariable long questionId,
            @RequestBody @Valid AdoptionRequest request) {
        return adoptionService.adopt(me.id(), questionId, request.answerId());
    }

    @Operation(summary = "채택 취소", description = "질문자만. 상태는 ANSWERED로. 채택이 없어도 204.")
    @DeleteMapping("/{questionId}/adoption")
    public ResponseEntity<Void> cancelAdoption(@AuthenticationPrincipal AuthMember me, @PathVariable long questionId) {
        adoptionService.cancel(me.id(), questionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "질문 삭제", description = "본인 질문만, 소프트 삭제. 답변은 남지만 질문과 함께 안 보인다.")
    @DeleteMapping("/{questionId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthMember me, @PathVariable long questionId) {
        questionService.delete(me.id(), questionId);
        return ResponseEntity.noContent().build();
    }

}
