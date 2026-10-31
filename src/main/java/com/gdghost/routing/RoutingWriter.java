package com.gdghost.routing;

import java.math.BigDecimal;
import java.time.Clock;
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
    private final Clock clock;

    public RoutingWriter(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /** 다시 계산할 때 쓴다. 지목(DIRECT) · 이미 보낸 것은 그대로 */
    void deleteMemberCandidates(long questionId) {
        jdbc.update("DELETE FROM routing WHERE question_id = ? AND stage = 'MEMBER' AND state = 'CANDIDATE'",
                questionId);
    }

    void insertCandidates(long questionId, List<CandidateScorer.Scored> ranked, String version) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (int i = 0; i < ranked.size(); i++) {
            CandidateScorer.Scored scored = ranked.get(i);
            jdbc.update("""
                    INSERT INTO routing (question_id, member_id, stage, rank_no, score, score_detail, state, created_at)
                    VALUES (?, ?, 'MEMBER', ?, ?, ?, 'CANDIDATE', ?)
                    """, questionId, scored.memberId(), i + 1, BigDecimal.valueOf(scored.total()),
                    detail(scored, version), now);
        }
    }

    /** @param sentAt 22–08시면 null — CANDIDATE로 두고 08시에 보낸다 */
    void insertDirect(long questionId, long memberId, Instant sentAt) {
        jdbc.update("""
                INSERT INTO routing (question_id, member_id, stage, state, sent_at, created_at)
                VALUES (?, ?, 'DIRECT', ?, ?, ?)
                """, questionId, memberId, sentAt == null ? "CANDIDATE" : "SENT",
                sentAt == null ? null : sentAt.atOffset(ZoneOffset.UTC).toLocalDateTime(),
                LocalDateTime.now(clock));
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
