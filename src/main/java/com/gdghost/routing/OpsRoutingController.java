package com.gdghost.routing;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.common.page.OpsPage;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "운영 · 라우팅")
@SecurityRequirement(name = "sessionCookie")
@RestController
@RequestMapping("/api/v1/ops")
public class OpsRoutingController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_DAYS = 30;

    private final OpsRoutingReader reader;
    private final Clock clock;

    public OpsRoutingController(OpsRoutingReader reader, Clock clock) {
        this.reader = reader;
        this.clock = clock;
    }

    @Operation(summary = "3일 넘게 답 없는 질문", description = "답을 기다린 지 3일이 지난 질문, 오래 기다린 순. "
            + "익명 질문의 작성자는 보이지 않는다. sentCount · passedCount는 부원 · 지목 기준.")
    @GetMapping("/questions/stale")
    public OpsPage<OpsRoutingResponse.StaleQuestion> stale(@AuthenticationPrincipal AuthMember me,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return reader.staleQuestions(me.id(), page, size);
    }

    @Operation(summary = "라우팅 현황 집계", description = "from · to는 한국 날짜, 기본 최근 30일, 최대 1년. 보낸 시각 기준. "
            + "전환율 · 넘김 비율은 부원 · 지목 알림 대비, 운영진 알림은 staffFirstCount로 따로. "
            + "byVersion은 점수 계산이 있는 부원 알림만(지목 제외).")
    @GetMapping("/routing/stats")
    public OpsRoutingResponse.Stats stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(KST));
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1);
        if (start.isAfter(end) || start.isBefore(end.minusYears(1))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                    List.of(new ErrorField("from", "기간은 from부터 to까지 최대 1년입니다.")));
        }
        return reader.stats(start, end);
    }

    @Operation(summary = "라우팅 발송 이력", description = "최신순. state: SENT · ANSWERED · PASSED · CANCELED. "
            + "운영진 채널 알림과 보내지 않은 지목은 뺀다. 익명 질문은 제목까지, 질문자 없음.")
    @GetMapping("/routing/logs")
    public OpsPage<OpsRoutingResponse.Log> logs(@RequestParam(required = false) OpsRoutingResponse.LogState state,
            @RequestParam(required = false) Long memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return reader.logs(state, memberId, page, size);
    }

}
