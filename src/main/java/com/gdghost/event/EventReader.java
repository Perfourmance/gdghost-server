package com.gdghost.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gdghost.common.security.Role;
import com.gdghost.question.AuthorRef;

@Repository
class EventReader {

    private final NamedParameterJdbcTemplate jdbc;

    EventReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record SummaryRow(long id, String name, LocalDate startDate, LocalDate endDate, long participantCount,
            long approvedCount, long myApprovedCount, long myPendingCount) {
    }

    record DetailRow(long id, String name, String description, String requirement, String guideSteps,
            LocalDate startDate, LocalDate endDate) {
    }

    record CertificationRow(long id, AuthorRef.Row author, String body, CertificationState state, String rejectReason,
            Instant createdAt) {
    }

    /** 집계를 조인으로 한 번에 읽는다 */
    List<SummaryRow> list(long viewerId) {
        return jdbc.query("""
                SELECT e.id, e.name, e.start_date, e.end_date,
                       COUNT(DISTINCT c.member_id) AS participant_count,
                       COALESCE(SUM(c.state = 'APPROVED'), 0) AS approved_count,
                       COALESCE(SUM(c.state = 'APPROVED' AND c.member_id = :viewer), 0) AS my_approved,
                       COALESCE(SUM(c.state = 'PENDING' AND c.member_id = :viewer), 0) AS my_pending
                FROM event e
                LEFT JOIN certification c ON c.event_id = e.id
                GROUP BY e.id, e.name, e.start_date, e.end_date
                """, Map.of("viewer", viewerId), (rs, i) -> new SummaryRow(rs.getLong("id"), rs.getString("name"),
                rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
                rs.getLong("participant_count"), rs.getLong("approved_count"), rs.getLong("my_approved"),
                rs.getLong("my_pending")));
    }

    Optional<DetailRow> find(long eventId) {
        return jdbc.query("""
                SELECT id, name, description, requirement, guide_steps, start_date, end_date
                FROM event WHERE id = :id
                """, Map.of("id", eventId), (rs, i) -> new DetailRow(rs.getLong("id"), rs.getString("name"),
                rs.getString("description"), rs.getString("requirement"), rs.getString("guide_steps"),
                rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class)))
                .stream().findFirst();
    }

    boolean exists(long eventId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM event WHERE id = :id)",
                Map.of("id", eventId), Boolean.class));
    }

    long count(long eventId, long memberId, CertificationState state) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM certification
                WHERE event_id = :event AND state = :state AND member_id = :member
                """, Map.of("event", eventId, "state", state.name(), "member", memberId), Long.class);
        return count == null ? 0 : count;
    }

    /** 운영진이 처리할 대기 수. 목록과 같은 기준(탈퇴자 제외) */
    long pendingForStaff(long eventId) {
        return countOps(CertificationState.PENDING, eventId);
    }

    record OpsRow(CertificationRow certification, OpsCertificationItem.EventRef event) {
    }

    private static final String OPS_FROM = """
            FROM certification c
            JOIN member m ON m.id = c.member_id
            JOIN event e ON e.id = c.event_id
            WHERE c.state = :state AND (:event IS NULL OR c.event_id = :event)
              AND (c.state <> 'PENDING' OR m.withdrawn_at IS NULL)
            """;

    List<OpsRow> opsCertifications(CertificationState state, Long eventId, int offset, int limit) {
        return jdbc.query("""
                SELECT c.id, c.member_id, c.body, c.state, c.reject_reason, c.created_at,
                       m.nickname, m.generation, m.role, m.withdrawn_at IS NOT NULL AS withdrawn,
                       e.id AS event_id, e.name AS event_name
                """ + OPS_FROM + """
                ORDER BY COALESCE(c.updated_at, c.created_at), c.id
                LIMIT :limit OFFSET :offset
                """, opsParams(state, eventId).addValue("limit", limit).addValue("offset", offset),
                (rs, i) -> new OpsRow(certification(rs, i),
                        new OpsCertificationItem.EventRef(rs.getLong("event_id"), rs.getString("event_name"))));
    }

    long countOps(CertificationState state, Long eventId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) " + OPS_FROM, opsParams(state, eventId), Long.class);
        return count == null ? 0 : count;
    }

    private static MapSqlParameterSource opsParams(CertificationState state, Long eventId) {
        return new MapSqlParameterSource().addValue("state", state.name()).addValue("event", eventId);
    }

    List<EventDetail.Rejected> rejected(long eventId, long memberId) {
        return jdbc.query("""
                SELECT id, reject_reason FROM certification
                WHERE event_id = :event AND member_id = :member AND state = 'REJECTED'
                ORDER BY id DESC
                """, Map.of("event", eventId, "member", memberId),
                (rs, i) -> new EventDetail.Rejected(rs.getLong("id"), rs.getString("reject_reason")));
    }

    /** 인증 횟수 상위. 정지 · 탈퇴한 부원은 뺀다 */
    List<RankRow> ranking(long eventId, int limit) {
        return jdbc.query("""
                SELECT m.id, m.nickname, m.generation, COUNT(*) AS approved
                FROM certification c
                JOIN member m ON m.id = c.member_id
                WHERE c.event_id = :event AND c.state = 'APPROVED'
                  AND m.withdrawn_at IS NULL AND m.suspended_at IS NULL
                GROUP BY m.id, m.nickname, m.generation
                ORDER BY approved DESC, m.id
                LIMIT :limit
                """, Map.of("event", eventId, "limit", limit), (rs, i) -> new RankRow(
                new EventDetail.RankedMember(rs.getLong("id"), rs.getString("nickname"), rs.getInt("generation")),
                rs.getLong("approved")));
    }

    record RankRow(EventDetail.RankedMember member, long approved) {
    }

    /** 최신순. allStates가 아니면 인증된 글과 내 글만 */
    List<CertificationRow> certifications(long eventId, long viewerId, boolean allStates, Long beforeId, int limit) {
        return jdbc.query("""
                SELECT c.id, c.member_id, c.body, c.state, c.reject_reason, c.created_at,
                       m.nickname, m.generation, m.role, m.withdrawn_at IS NOT NULL AS withdrawn
                FROM certification c
                JOIN member m ON m.id = c.member_id
                WHERE c.event_id = :event
                  AND (:all OR c.state = 'APPROVED' OR c.member_id = :viewer)
                  AND (:before IS NULL OR c.id < :before)
                ORDER BY c.id DESC
                LIMIT :limit
                """, new MapSqlParameterSource().addValue("event", eventId).addValue("viewer", viewerId)
                .addValue("all", allStates).addValue("before", beforeId).addValue("limit", limit),
                EventReader::certification);
    }

    CertificationRow certification(long certificationId) {
        return jdbc.queryForObject("""
                SELECT c.id, c.member_id, c.body, c.state, c.reject_reason, c.created_at,
                       m.nickname, m.generation, m.role, m.withdrawn_at IS NOT NULL AS withdrawn
                FROM certification c
                JOIN member m ON m.id = c.member_id
                WHERE c.id = :id
                """, Map.of("id", certificationId), EventReader::certification);
    }

    private static CertificationRow certification(ResultSet rs, int rowNum) throws SQLException {
        return new CertificationRow(rs.getLong("id"),
                new AuthorRef.Row(rs.getLong("member_id"), rs.getString("nickname"), rs.getInt("generation"),
                        Role.valueOf(rs.getString("role")), rs.getBoolean("withdrawn")),
                rs.getString("body"), CertificationState.valueOf(rs.getString("state")),
                rs.getString("reject_reason"),
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }

}
