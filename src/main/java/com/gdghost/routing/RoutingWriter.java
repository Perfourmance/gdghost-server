package com.gdghost.routing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.ObjectMapper;

@Repository
public class RoutingWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public RoutingWriter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void insertMembers(long questionId, List<CandidateScorer.Scored> ranked, String version, Instant now) {
        for (int i = 0; i < ranked.size(); i++) {
            CandidateScorer.Scored scored = ranked.get(i);
            jdbc.update("""
                    INSERT INTO routing (question_id, member_id, stage, rank_no, score, score_detail, state,
                                         sent_at, created_at)
                    VALUES (?, ?, 'MEMBER', ?, ?, ?, 'SENT', ?, ?)
                    """, questionId, scored.memberId(), i + 1, BigDecimal.valueOf(scored.total()),
                    detail(scored, version), utc(now), utc(now));
        }
    }

    void insertStaff(long questionId, long memberId, Instant now) {
        jdbc.update("""
                INSERT INTO routing (question_id, member_id, stage, state, sent_at, created_at)
                VALUES (?, ?, 'STAFF', 'SENT', ?, ?)
                """, questionId, memberId, utc(now), utc(now));
    }

    /** @param sentAt 22–08시면 null — CANDIDATE로 두고 08시에 보낸다 */
    void insertDirect(long questionId, long memberId, Instant sentAt, Instant now) {
        jdbc.update("""
                INSERT INTO routing (question_id, member_id, stage, state, sent_at, created_at)
                VALUES (?, ?, 'DIRECT', ?, ?, ?)
                """, questionId, memberId, sentAt == null ? "CANDIDATE" : "SENT",
                sentAt == null ? null : utc(sentAt), utc(now));
    }

    void markSent(long routingId, Instant now) {
        jdbc.update("UPDATE routing SET state = 'SENT', sent_at = ? WHERE id = ?", utc(now), routingId);
    }

    void cancel(long routingId, Instant now) {
        jdbc.update("UPDATE routing SET state = 'CANCELED', responded_at = ? WHERE id = ?", utc(now), routingId);
    }

    void markAnswered(long questionId, long memberId, Instant now) {
        jdbc.update("""
                UPDATE routing SET state = 'ANSWERED', responded_at = ?
                WHERE question_id = ? AND member_id = ? AND state = 'SENT' AND stage <> 'STAFF'
                """, utc(now), questionId, memberId);
    }

    private static LocalDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toLocalDateTime();
    }

    private String detail(CandidateScorer.Scored scored, String version) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("version", version);
        detail.put("rule", scored.rule());
        detail.put("profile", scored.profile());
        detail.put("history", scored.history());
        detail.put("activity", scored.activity());
        detail.put("total", scored.total());
        detail.put("reasons", scored.reasons());
        return json.writeValueAsString(detail);
    }

}
