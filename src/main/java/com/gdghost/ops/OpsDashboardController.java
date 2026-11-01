package com.gdghost.ops;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "운영 · 홈")
@SecurityRequirement(name = "sessionCookie")
@RestController
public class OpsDashboardController {

    private final OpsDashboardService dashboardService;

    public OpsDashboardController(OpsDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "운영 홈", description = "'이번 주'는 한국 시간 월요일 00시부터. 3일 미답변 · 미처리 신고는 전체 수와 상위 5건. "
            + "미확인 필독은 마감이 지나지 않았고 아직 다 확인하지 않은 공지, 최신 5건.")
    @GetMapping("/api/v1/ops/dashboard")
    public OpsDashboardResponse dashboard(@AuthenticationPrincipal AuthMember me) {
        return dashboardService.dashboard(me.id());
    }

}
