package com.gdghost.ops;

import java.util.List;

import com.gdghost.common.page.OpsPage;
import com.gdghost.notice.NoticeStatsReader.UnconfirmedMustRead;
import com.gdghost.report.OpsReportResponse;
import com.gdghost.routing.OpsRoutingResponse;

public record OpsDashboardResponse(OpsRoutingResponse.ThisWeek thisWeek,
                                   Top<OpsRoutingResponse.StaleQuestion> staleQuestions,
                                   Top<OpsReportResponse> openReports,
                                   long pendingCertifications,
                                   List<UnconfirmedMustRead> unconfirmedMustRead) {

    /** 상위 몇 건과 전체 수 */
    public record Top<T>(long total, List<T> items) {

        static <T> Top<T> of(OpsPage<T> page) {
            return new Top<>(page.total(), page.items());
        }

    }

}
