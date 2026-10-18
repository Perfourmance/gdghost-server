package com.gdghost.member;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gdghost.common.security.Role;

/**
 * 부원 목록. 숨김 · 탈퇴 · 정지는 빠진다.
 * 관심 분야 · 답변 수는 페이지의 id로 IN + GROUP BY 한 번씩 읽는다 (N+1 방지).
 */
@Repository
public class MemberDirectoryReader {

    private final NamedParameterJdbcTemplate jdbc;

    public MemberDirectoryReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 커서 위치(after)는 직전 페이지 마지막 줄의 기수 · 닉네임. 다음 페이지 확인용으로 limit개를 읽는다 */
    public List<MemberSummary> list(Integer generation, MemberStatus status, Integer afterGeneration,
            String afterNickname, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("generation", generation)
                .addValue("status", status != null ? status.name() : null)
                .addValue("afterGeneration", afterGeneration)
                .addValue("afterNickname", afterNickname)
                .addValue("limit", limit);
        List<MemberSummary> rows = jdbc.query("""
                SELECT id, nickname, generation, status, position, status_note, accept_question, accept_coffee, role
                FROM member
                WHERE withdrawn_at IS NULL AND suspended_at IS NULL AND hidden_from_list = FALSE
                  AND (:generation IS NULL OR generation = :generation)
                  AND (:status IS NULL OR status = :status)
                  AND (:afterGeneration IS NULL
                       OR generation < :afterGeneration
                       OR (generation = :afterGeneration AND nickname > :afterNickname))
                ORDER BY generation DESC, nickname
                LIMIT :limit
                """, params, (rs, i) -> new MemberSummary(rs.getLong("id"), rs.getString("nickname"),
                rs.getInt("generation"), MemberStatus.valueOf(rs.getString("status")),
                Position.valueOf(rs.getString("position")), rs.getString("status_note"), List.of(), 0,
                rs.getBoolean("accept_question"), rs.getBoolean("accept_coffee"),
                Role.valueOf(rs.getString("role")) == Role.STAFF));
        if (rows.isEmpty()) {
            return rows;
        }
        List<Long> ids = rows.stream().map(MemberSummary::id).toList();
        Map<Long, List<String>> interests = interests(ids);
        Map<Long, Long> answerCounts = answerCounts(ids);
        return rows.stream()
                .map(row -> new MemberSummary(row.id(), row.nickname(), row.generation(), row.status(),
                        row.position(), row.statusNote(), interests.getOrDefault(row.id(), List.of()),
                        answerCounts.getOrDefault(row.id(), 0L), row.acceptQuestion(), row.acceptCoffee(),
                        row.isStaff()))
                .toList();
    }

    private Map<Long, List<String>> interests(List<Long> ids) {
        Map<Long, List<String>> result = new HashMap<>();
        jdbc.query("""
                SELECT mt.member_id, t.name
                FROM member_tag mt JOIN tag t ON t.id = mt.tag_id
                WHERE t.kind = 'INTEREST' AND mt.member_id IN (:ids)
                ORDER BY t.sort_order, t.id
                """, Map.of("ids", ids), rs -> {
            result.computeIfAbsent(rs.getLong("member_id"), k -> new ArrayList<>()).add(rs.getString("name"));
        });
        return result;
    }

    private Map<Long, Long> answerCounts(List<Long> ids) {
        Map<Long, Long> result = new HashMap<>();
        jdbc.query("""
                SELECT a.author_id, COUNT(*) AS cnt
                FROM answer a JOIN question q ON q.id = a.question_id
                WHERE a.author_id IN (:ids) AND a.is_anonymous = FALSE AND
                """ + MemberActivityReader.VISIBLE_ANSWER + """
                GROUP BY a.author_id
                """, Map.of("ids", ids), rs -> {
            result.put(rs.getLong("author_id"), rs.getLong("cnt"));
        });
        return result;
    }

}
