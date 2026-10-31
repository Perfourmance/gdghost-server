package com.gdghost.embedding;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.ObjectMapper;

/** question_embedding · member_embedding 읽기 · 쓰기 */
@Repository
public class EmbeddingStore {

    public record MemberRow(String model, String sourceHash) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public EmbeddingStore(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /** 질문을 고치면 행을 지우므로, 같은 모델의 행이 있으면 지금 글의 벡터다 */
    public boolean hasQuestion(long questionId, String model) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM question_embedding WHERE question_id = ? AND model = ?
                """, Integer.class, questionId, model);
        return count != null && count > 0;
    }

    public Optional<float[]> questionVector(long questionId, String model) {
        return jdbc.query("SELECT vector FROM question_embedding WHERE question_id = ? AND model = ?",
                (rs, i) -> vector(rs.getString("vector")), questionId, model).stream().findFirst();
    }

    /** 활동 중 부원 벡터 전부. 후보 수가 적어 메모리에서 계산한다 (AI 매칭 설계 5장) */
    public Map<Long, float[]> activeMemberVectors(String model) {
        Map<Long, float[]> vectors = new HashMap<>();
        jdbc.query("""
                SELECT me.member_id, me.vector FROM member_embedding me
                JOIN member m ON m.id = me.member_id
                WHERE me.model = ? AND m.withdrawn_at IS NULL AND m.suspended_at IS NULL
                """, rs -> {
            vectors.put(rs.getLong("member_id"), vector(rs.getString("vector")));
        }, model);
        return vectors;
    }

    public float[] vector(String stored) {
        return json.readValue(stored, float[].class);
    }

    public Optional<MemberRow> member(long memberId) {
        return jdbc.query("SELECT model, source_hash FROM member_embedding WHERE member_id = ?",
                (rs, i) -> new MemberRow(rs.getString("model"), rs.getString("source_hash")), memberId)
                .stream().findFirst();
    }

    /** 그 사이 지워진 질문이면 넣지 않는다 */
    public void saveQuestion(long questionId, float[] vector, String model) {
        jdbc.update("""
                INSERT INTO question_embedding (question_id, vector, model, created_at)
                SELECT id, ?, ?, ? FROM question WHERE id = ? AND deleted_at IS NULL
                ON DUPLICATE KEY UPDATE vector = VALUES(vector), model = VALUES(model),
                                        created_at = VALUES(created_at)
                """, json.writeValueAsString(vector), model, LocalDateTime.now(clock), questionId);
    }

    /** 그 사이 탈퇴했으면 넣지 않는다 — 탈퇴 정리 뒤에 벡터가 남지 않게 */
    public void saveMember(long memberId, float[] vector, String model, String sourceHash) {
        jdbc.update("""
                INSERT INTO member_embedding (member_id, vector, model, source_hash, updated_at)
                SELECT id, ?, ?, ?, ? FROM member WHERE id = ? AND withdrawn_at IS NULL
                ON DUPLICATE KEY UPDATE vector = VALUES(vector), model = VALUES(model),
                                        source_hash = VALUES(source_hash), updated_at = VALUES(updated_at)
                """, json.writeValueAsString(vector), model, sourceHash, LocalDateTime.now(clock), memberId);
    }

    public List<Long> questionsToRetry(String model) {
        return jdbc.queryForList("""
                SELECT q.id FROM question q
                LEFT JOIN question_embedding qe ON qe.question_id = q.id AND qe.model = ?
                WHERE q.deleted_at IS NULL AND qe.question_id IS NULL
                ORDER BY q.id
                """, Long.class, model);
    }

    /** 태그 이름이 바뀌어도 잡히도록 활동 중 부원 전부. 호출 여부는 해시로 정한다 */
    public List<Long> activeMembers() {
        return jdbc.queryForList("""
                SELECT id FROM member WHERE withdrawn_at IS NULL AND suspended_at IS NULL ORDER BY id
                """, Long.class);
    }

}
