package com.gdghost.routing;

import java.time.temporal.ChronoUnit;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.ratelimit.RateLimit;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "라우팅")
@RestController
public class RoutingController {

    private final RoutingPassService passService;
    private final SeniorMatchService seniorMatchService;

    public RoutingController(RoutingPassService passService, SeniorMatchService seniorMatchService) {
        this.passService = passService;
        this.seniorMatchService = seniorMatchService;
    }

    @Operation(summary = "제가 답하기 어려워요 (넘기기)", description = "내가 받은(SENT) 질문만, 아니면 403. "
            + "넘기면 질문당 5명까지 다음 사람에게 보낸다. 22–08시이거나 후보가 없으면 nextCandidateNotified false, "
            + "22–08시와 3시간 전이면 배치가 이어서 처리한다. 넘겨도 답변은 달 수 있다.")
    @PostMapping("/api/v1/questions/{questionId}/routing/pass")
    public RoutingPassService.PassResponse pass(@AuthenticationPrincipal AuthMember me,
            @PathVariable long questionId) {
        return passService.pass(me.id(), questionId);
    }

    @Operation(summary = "선배 찾기", description = "분류 + 분야 + 질문(10–120자)으로 선배 1–3순위. 저장하지 않고 알림도 보내지 않는다. "
            + "AI 실패 시 규칙 점수만 쓰고 fallback true. 결과가 없으면 items 빈 배열. 부원당 분당 5회 · 하루 30회(첫 요청부터 24시간).")
    @RateLimit(limit = 5)
    @RateLimit(limit = 30, unit = ChronoUnit.DAYS)
    @PostMapping("/api/v1/senior-matches")
    public SeniorMatchResponse find(@AuthenticationPrincipal AuthMember me,
            @RequestBody @Valid SeniorMatchRequest request) {
        return seniorMatchService.find(me.id(), request);
    }

}
