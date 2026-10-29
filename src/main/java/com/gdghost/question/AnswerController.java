package com.gdghost.question;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.idempotency.Idempotent;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "답변")
@RestController
@RequestMapping("/api/v1")
public class AnswerController {

    private final AnswerService answerService;

    public AnswerController(AnswerService answerService) {
        this.answerService = answerService;
    }

    @Operation(summary = "답변 · 답글 작성", description = "Idempotency-Key 필수. parentId가 없으면 답변(5자 이상), "
            + "있으면 답글(1자 이상). 내 질문엔 답글만(409), 익명은 익명 질문에서만(400), 직전 제출 5초 안이면 429. "
            + "질문 · 부모가 없거나 지워졌으면 404. 응답은 질문 상세 answers 항목 모양.")
    @Idempotent
    @PostMapping("/questions/{questionId}/answers")
    public ResponseEntity<AnswerNode> create(@AuthenticationPrincipal AuthMember me, @PathVariable long questionId,
            @RequestBody @Valid AnswerCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(answerService.create(me.id(), questionId, request));
    }

    @Operation(summary = "답변 · 답글 수정", description = "본인 것만, 본문만(답변 5자 · 답글 1자 이상). 익명 여부는 그대로, "
            + "채택은 유지. 본문이 바뀌면 editedAt을 찍는다. 지운 답변이거나 질문이 지워졌으면 404. "
            + "응답은 질문 상세 트리의 그 노드(하위 답글 포함).")
    @PatchMapping("/answers/{answerId}")
    public AnswerNode update(@AuthenticationPrincipal AuthMember me, @PathVariable long answerId,
            @RequestBody @Valid AnswerUpdateRequest request) {
        return answerService.update(me.id(), answerId, request);
    }

    @Operation(summary = "답변 · 답글 삭제", description = "본인 것만, 소프트 삭제. 답글이 남으면 상세 트리에 자리(deletedBy)가 남는다. "
            + "채택된 답변이면 채택도 풀고, 남은 답변이 없으면 질문은 WAITING.")
    @DeleteMapping("/answers/{answerId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthMember me, @PathVariable long answerId) {
        answerService.delete(me.id(), answerId);
        return ResponseEntity.noContent().build();
    }

}
