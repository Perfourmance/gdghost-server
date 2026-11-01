package com.gdghost.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.audit.OpsAction;
import com.gdghost.common.audit.OpsAudit;
import com.gdghost.common.audit.OpsAuditLogger;
import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;

@Service
public class RoutingExportService {

    private final RoutingExportReader reader;
    private final OpsAuditLogger opsAudit;
    private final ApplicationEventPublisher events;

    public RoutingExportService(RoutingExportReader reader, OpsAuditLogger opsAudit,
            ApplicationEventPublisher events) {
        this.reader = reader;
        this.opsAudit = opsAudit;
        this.events = events;
    }

    /** from · to는 한국 날짜, 둘 다 포함. 요청마다 운영 기록 + 개발자 디스코드 */
    @Transactional
    public String export(long actorId, LocalDate from, LocalDate to) {
        if (from.isAfter(to) || from.isBefore(to.minusYears(1))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                    List.of(new ErrorField("from", "기간은 from부터 to까지 최대 1년입니다.")));
        }
        List<String> rows = reader.rows(from, to);
        opsAudit.record(OpsAudit.of(actorId, OpsAction.ROUTING_EXPORT)
                .detail(Map.of("from", from.toString(), "to", to.toString(), "rows", rows.size())));
        events.publishEvent(new RoutingExportEvent(actorId, from, to, rows.size()));
        return String.join("\n", Stream.concat(Stream.of(RoutingExportReader.header()), rows.stream()).toList()) + "\n";
    }

}
