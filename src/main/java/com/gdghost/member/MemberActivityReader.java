package com.gdghost.member;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 프로필의 활동 수 · 활동 기록. question · answer · share · feed_post를 읽기만 한다 */
@Repository
public class MemberActivityReader {

    /** 셀 수 있는 답변 — 답글 · 삭제된 답변 · 삭제된 질문의 답변 제외. a = answer, q = question */
    public static final String VISIBLE_ANSWER = " a.parent_id IS NULL AND a.deleted_at IS NULL AND q.deleted_at IS NULL ";

    private static final int SNIPPET_LENGTH = 100;

    private final NamedParameterJdbcTemplate jdbc;

    public MemberActivityReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MemberStats stats(long memberId) {
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM answer a JOIN question q ON q.id = a.question_id
                        WHERE a.author_id = :id AND a.is_anonymous = FALSE AND""" + VISIBLE_ANSWER + """
                       ) AS answers,
                       (SELECT COUNT(*) FROM question
                        WHERE author_id = :id AND is_anonymous = FALSE AND deleted_at IS NULL) AS questions,
                       (SELECT COUNT(*) FROM share WHERE author_id = :id AND deleted_at IS NULL) AS shares,
                       (SELECT COUNT(*) FROM feed_post WHERE author_id = :id AND deleted_at IS NULL) AS feeds
                """, new MapSqlParameterSource("id", memberId), (rs, i) -> new MemberStats(rs.getLong("answers"),
                rs.getLong("questions"), rs.getLong("shares"), rs.getLong("feeds")));
    }

    /** 최신순(id 내림차순). includeAnonymous는 본인 조회일 때만 true */
    public List<ActivityItem.Row> answers(long memberId, boolean includeAnonymous, Long afterId, int limit) {
        return jdbc.query("""
                SELECT a.id, q.id AS question_id, q.title, LEFT(a.body, :snippet) AS snippet,
                       q.adopted_answer_id <=> a.id AS adopted, a.is_anonymous, a.created_at
                FROM answer a JOIN question q ON q.id = a.question_id
                WHERE a.author_id = :id AND (:includeAnonymous OR a.is_anonymous = FALSE)
                  AND (:afterId IS NULL OR a.id < :afterId) AND""" + VISIBLE_ANSWER + """
                ORDER BY a.id DESC
                LIMIT :limit
                """, params(memberId, includeAnonymous, afterId, limit), (rs, i) -> new ActivityItem.Row(
                rs.getLong("id"), new ActivityItem(ActivityType.ANSWER, rs.getLong("question_id"),
                        rs.getString("title"), rs.getString("snippet"), rs.getBoolean("adopted"),
                        includeAnonymous ? rs.getBoolean("is_anonymous") : null,
                        rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC))));
    }

    public List<ActivityItem.Row> questions(long memberId, boolean includeAnonymous, Long afterId, int limit) {
        return jdbc.query("""
                SELECT id, title, LEFT(body, :snippet) AS snippet, state = 'ADOPTED' AS adopted, is_anonymous,
                       created_at
                FROM question
                WHERE author_id = :id AND deleted_at IS NULL AND (:includeAnonymous OR is_anonymous = FALSE)
                  AND (:afterId IS NULL OR id < :afterId)
                ORDER BY id DESC
                LIMIT :limit
                """, params(memberId, includeAnonymous, afterId, limit), (rs, i) -> new ActivityItem.Row(
                rs.getLong("id"), new ActivityItem(ActivityType.QUESTION, rs.getLong("id"), rs.getString("title"),
                        rs.getString("snippet"), rs.getBoolean("adopted"),
                        includeAnonymous ? rs.getBoolean("is_anonymous") : null,
                        rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC))));
    }

    /** 공유에는 익명이 없다. anonymous는 다른 탭처럼 본인 조회일 때만 false로 내려간다 */
    public List<ActivityItem.Row> shares(long memberId, boolean self, Long afterId, int limit) {
        return jdbc.query("""
                SELECT id, title, LEFT(body, :snippet) AS snippet, created_at
                FROM share
                WHERE author_id = :id AND deleted_at IS NULL AND (:afterId IS NULL OR id < :afterId)
                ORDER BY id DESC
                LIMIT :limit
                """, params(memberId, self, afterId, limit), (rs, i) -> new ActivityItem.Row(
                rs.getLong("id"), new ActivityItem(ActivityType.SHARE, rs.getLong("id"), rs.getString("title"),
                        rs.getString("snippet"), false, self ? Boolean.FALSE : null,
                        rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC))));
    }

    /** 피드는 제목이 없어 title null. 익명이 없고 anonymous는 공유 탭과 같다 */
    public List<ActivityItem.Row> feeds(long memberId, boolean self, Long afterId, int limit) {
        return jdbc.query("""
                SELECT id, LEFT(body, :snippet) AS snippet, created_at
                FROM feed_post
                WHERE author_id = :id AND deleted_at IS NULL AND (:afterId IS NULL OR id < :afterId)
                ORDER BY id DESC
                LIMIT :limit
                """, params(memberId, self, afterId, limit), (rs, i) -> new ActivityItem.Row(
                rs.getLong("id"), new ActivityItem(ActivityType.FEED, rs.getLong("id"), null,
                        rs.getString("snippet"), false, self ? Boolean.FALSE : null,
                        rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC))));
    }

    private static MapSqlParameterSource params(long memberId, boolean includeAnonymous, Long afterId, int limit) {
        return new MapSqlParameterSource()
                .addValue("id", memberId)
                .addValue("includeAnonymous", includeAnonymous)
                .addValue("afterId", afterId)
                .addValue("limit", limit)
                .addValue("snippet", SNIPPET_LENGTH);
    }

}
