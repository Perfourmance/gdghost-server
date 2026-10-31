package com.gdghost.recruit;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.idempotency.Idempotent;
import com.gdghost.common.page.CursorPage;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "모집")
@RestController
@RequestMapping("/api/v1/recruits")
public class RecruitController {

    private final RecruitService recruitService;

    public RecruitController(RecruitService recruitService) {
        this.recruitService = recruitService;
    }

    @Operation(summary = "모집 열기", description = "Idempotency-Key 필수. 개설자는 참여자 1명으로 자동 포함. "
            + "meetAt은 지금보다 뒤, 90일 이내. capacity는 개설자 포함 2–50. scheduleId가 없거나 지운 일정이면 404.")
    @Idempotent
    @PostMapping
    public ResponseEntity<RecruitSummary> create(@AuthenticationPrincipal AuthMember me,
            @RequestBody @Valid RecruitCreateRequest request) {
        RecruitSummary created = recruitService.create(me.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/recruits/" + created.id())).body(created);
    }

    @Operation(summary = "모집 목록", description = "모집중이 위, 그 안에서 meetAt 가까운 순. meetAt이 7일 넘게 지난 모집은 빠진다.")
    @GetMapping
    public CursorPage<RecruitSummary> list(@AuthenticationPrincipal AuthMember me,
            @RequestParam(required = false) RecruitKind kind,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_SIZE) int size) {
        return recruitService.list(me.id(), kind, cursor, size);
    }

    @Operation(summary = "모집 상세", description = "목록 항목 + 참여자. 마감된 모집도 열린다.")
    @GetMapping("/{recruitId}")
    public RecruitDetail detail(@AuthenticationPrincipal AuthMember me, @PathVariable long recruitId) {
        return recruitService.detail(me.id(), recruitId);
    }

    @Operation(summary = "모집 참여", description = "본문 없음. 정원이 차면 자동 마감. 개설자에게 알림은 보내지 않는다.")
    @PostMapping("/{recruitId}/participants")
    public RecruitJoinResponse join(@AuthenticationPrincipal AuthMember me, @PathVariable long recruitId) {
        return recruitService.join(me.id(), recruitId);
    }

    @Operation(summary = "모집 참여 취소", description = "마감 전에만. 다 차서 닫힌 모집은 시간이 남았으면 취소할 수 있고 다시 열린다. "
            + "개설자는 취소 대신 모집 닫기. 참여하지 않은 모집이면 404.")
    @DeleteMapping("/{recruitId}/participants/me")
    public RecruitJoinResponse leave(@AuthenticationPrincipal AuthMember me, @PathVariable long recruitId) {
        return recruitService.leave(me.id(), recruitId);
    }

    @Operation(summary = "모집 닫기", description = "개설자만. 이미 닫힌 모집이면 그대로 200. 다시 여는 기능은 없다.")
    @PostMapping("/{recruitId}/closure")
    public RecruitClosureResponse close(@AuthenticationPrincipal AuthMember me, @PathVariable long recruitId) {
        return recruitService.close(me.id(), recruitId);
    }

}
