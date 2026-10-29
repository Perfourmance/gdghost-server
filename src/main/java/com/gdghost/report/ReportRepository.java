package com.gdghost.report;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(long reporterId, ReportTargetType targetType, long targetId);

    /** 엔티티를 올리지 않고 대상만 — 대상 행을 먼저 잠근 뒤 신고를 새로 읽기 위해 */
    @Query("SELECT new com.gdghost.report.ReportTarget(r.targetType, r.targetId) FROM Report r WHERE r.id = :id")
    Optional<ReportTarget> findTarget(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Report r WHERE r.targetType = :type AND r.targetId = :targetId ORDER BY r.id")
    List<Report> lockByTarget(ReportTargetType type, long targetId);

}
