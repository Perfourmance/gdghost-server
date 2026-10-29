package com.gdghost.report;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long reporterId;

    @Enumerated(EnumType.STRING)
    private ReportTargetType targetType;

    private long targetId;
    private String reason;

    @Enumerated(EnumType.STRING)
    private ReportState state;

    private Long handledBy;
    private Instant handledAt;
    private Instant createdAt;

    public static Report open(long reporterId, ReportTargetType targetType, long targetId, String reason,
            Instant now) {
        Report report = new Report();
        report.reporterId = reporterId;
        report.targetType = targetType;
        report.targetId = targetId;
        report.reason = reason;
        report.state = ReportState.OPEN;
        report.createdAt = now;
        return report;
    }

}
