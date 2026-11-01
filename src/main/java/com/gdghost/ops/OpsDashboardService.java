package com.gdghost.ops;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.notice.NoticeStatsReader;
import com.gdghost.report.OpsReportService;
import com.gdghost.report.ReportState;
import com.gdghost.routing.OpsRoutingReader;
import com.gdghost.routing.RoutingReader;

/** 운영 페이지 첫 화면. 각 목록 API와 같은 기준으로 센다 */
@Service
public class OpsDashboardService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int TOP = 5;

    private final OpsRoutingReader opsRouting;
    private final RoutingReader routing;
    private final OpsReportService reports;
    private final NoticeStatsReader notices;
    private final Clock clock;

    public OpsDashboardService(OpsRoutingReader opsRouting, RoutingReader routing, OpsReportService reports,
            NoticeStatsReader notices, Clock clock) {
        this.opsRouting = opsRouting;
        this.routing = routing;
        this.reports = reports;
        this.notices = notices;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OpsDashboardResponse dashboard(long viewerId) {
        return new OpsDashboardResponse(opsRouting.thisWeek(),
                OpsDashboardResponse.Top.of(opsRouting.staleQuestions(viewerId, 0, TOP)),
                OpsDashboardResponse.Top.of(reports.list(ReportState.OPEN, 0, TOP)),
                routing.pendingCertifications(),
                notices.unconfirmedMustRead(LocalDate.now(clock.withZone(KST)), TOP));
    }

}
