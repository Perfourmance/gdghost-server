package com.gdghost.report;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(long reporterId, ReportTargetType targetType, long targetId);

}
