package com.gdghost.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 실제로 보낸 라우팅만, 보낸 시각 순. 이름 · 제목 · 본문 없이 id와 숫자만 */
@Repository
public class RoutingExportReader {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String HEADER = "routing_id,question_id,question_category,member_id,stage,rank_no,state,"
            + "sent_at,answered_at,version,rule,profile,history,activity,total";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public RoutingExportReader(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** @return 머리글을 뺀 행 */
    List<String> rows(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT r.id, r.question_id, q.category, q.is_anonymous, r.member_id, r.stage, r.rank_no, r.state,
                       r.sent_at, r.responded_at, r.score_detail
                FROM routing r
                JOIN question q ON q.id = r.question_id
                WHERE r.sent_at >= :from AND r.sent_at < :to
                ORDER BY r.sent_at, r.id
                """, Map.of("from", utcStart(from), "to", utcStart(to.plusDays(1))), (rs, i) -> {
            String state = rs.getString("state");
            Instant responded = instant(rs.getObject("responded_at", LocalDateTime.class));
            // 익명 질문에 답한 사람을 추측할 수 없게 받은 사람을 비운다
            String member = rs.getBoolean("is_anonymous") ? "" : rs.getString("member_id");
            return String.join(",", Stream.concat(Stream.of(rs.getString("id"), rs.getString("question_id"),
                    rs.getString("category"), member, rs.getString("stage"), text(rs.getString("rank_no")), state,
                    seconds(instant(rs.getObject("sent_at", LocalDateTime.class))),
                    state.equals("ANSWERED") ? seconds(responded) : ""),
                    scores(rs.getString("score_detail"))).toList());
        });
    }

    static String header() {
        return HEADER;
    }

    /** 운영진 · 지목은 점수 계산이 없어 빈칸 */
    private Stream<String> scores(String detail) {
        if (detail == null) {
            return Stream.of("", "", "", "", "", "");
        }
        JsonNode node = json.readTree(detail);
        return Stream.of("version", "rule", "profile", "history", "activity", "total")
                .map(field -> node.has(field) ? quote(node.get(field).asString()) : "");
    }

    /** version은 설정값이라 쉼표 · 따옴표가 들어와도 칸이 밀리지 않게 */
    private static String quote(String value) {
        return value.contains(",") || value.contains("\"") || value.contains("\n")
                ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String seconds(Instant instant) {
        return instant == null ? "" : instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static LocalDateTime utcStart(LocalDate date) {
        return LocalDateTime.ofInstant(date.atStartOfDay(KST).toInstant(), ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }

}
