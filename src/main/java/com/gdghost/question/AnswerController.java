package com.gdghost.question;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/questions/{questionId}/answers")
public class AnswerController {

    private final AnswerService answerService;

    public AnswerController(AnswerService answerService) {
        this.answerService = answerService;
    }

    @Operation(summary = "답변 · 답글 작성", description = "Idempotency-Key 필수. parentId가 없으면 답변(5자 이상), "
            + "있으면 답글(1자 이상). 내 질문엔 답글만(409), 익명은 익명 질문에서만(400), 직전 제출 5초 안이면 429. "
            + "질문 · 부모가 없거나 지워졌으면 404. 응답은 질문 상세 answers 항목 모양.")
    @Idempotent
    @PostMapping
    public ResponseEntity<AnswerNode> create(@AuthenticationPrincipal AuthMember me, @PathVariable long questionId,
            @RequestBody @Valid AnswerCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(answerService.create(me.id(), questionId, request));
    }

}
