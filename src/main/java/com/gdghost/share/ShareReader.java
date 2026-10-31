package com.gdghost.share;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gdghost.common.security.Role;
import com.gdghost.question.AuthorRef;

@Repository
class ShareReader {

    private static final String AUTHOR_COLUMNS = """
            s.author_id, m.nickname, m.generation, m.role, m.withdrawn_at IS NOT NULL AS withdrawn
            """;

    private final NamedParameterJdbcTemplate jdbc;

    ShareReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Row(long id, ShareCategory category, String title, AuthorRef.Row author, long saveCount,
            Instant createdAt) {
    }

    record DetailRow(long id, ShareCategory category, String title, String body, AuthorRef.Row author, long saveCount,
            boolean saved, Instant createdAt, Instant updatedAt) {
    }

    record NoteRow(long id, long authorId, String nickname, boolean withdrawn, String body, Instant createdAt) {
    }

    record SavedRow(Row share, Instant savedAt) {
    }

    record SavedAfter(Instant savedAt, long id) {
    }

    /** 커서는 정렬과 같은 값. LATEST면 saveCount는 null */
    record After(Long saveCount, long id) {
    }

    List<Row> list(ShareCategory category, ShareSort sort, String titleOrTag, After after, int limit) {
        String position = after == null ? "" : sort == ShareSort.SAVED ? """
                AND (s.save_count < :afterSave OR (s.save_count = :afterSave AND s.id < :afterId))
                """ : "AND s.id < :afterId\n";
        String order = sort == ShareSort.SAVED ? "s.save_count DESC, s.id DESC" : "s.id DESC";
        return jdbc.query("""
                SELECT s.id, s.category, s.title, s.save_count, s.created_at,
                """ + AUTHOR_COLUMNS + """
                FROM share s JOIN member m ON m.id = s.author_id
                WHERE s.deleted_at IS NULL
                  AND (:category IS NULL OR s.category = :category)
                  AND (:like IS NULL OR s.title LIKE :like ESCAPE '!' OR EXISTS (
                        SELECT 1 FROM share_tag t WHERE t.share_id = s.id AND t.name LIKE :like ESCAPE '!'))
                """ + position + "ORDER BY " + order + " LIMIT :limit",
                new MapSqlParameterSource()
                        .addValue("category", category == null ? null : category.name())
                        .addValue("like", titleOrTag == null ? null : "%" + escapeLike(titleOrTag) + "%")
                        .addValue("afterSave", after == null ? null : after.saveCount())
                        .addValue("afterId", after == null ? null : after.id())
                        .addValue("limit", limit),
                (rs, i) -> row(rs));
    }

    /** 최근 저장한 순. 지운 글은 빠진다 */
    List<SavedRow> saved(long memberId, SavedAfter after, int limit) {
        String position = after == null ? "" : """
                AND (v.created_at < :afterAt OR (v.created_at = :afterAt AND v.share_id < :afterId))
                """;
        return jdbc.query("""
                SELECT s.id, s.category, s.title, s.save_count, s.created_at, v.created_at AS saved_at,
                """ + AUTHOR_COLUMNS + """
                FROM share_save v
                JOIN share s ON s.id = v.share_id
                JOIN member m ON m.id = s.author_id
                WHERE v.member_id = :member AND s.deleted_at IS NULL
                """ + position + "ORDER BY v.created_at DESC, v.share_id DESC LIMIT :limit",
                new MapSqlParameterSource()
                        .addValue("member", memberId)
                        .addValue("afterAt", after == null ? null : LocalDateTime.ofInstant(after.savedAt(), ZoneOffset.UTC))
                        .addValue("afterId", after == null ? null : after.id())
                        .addValue("limit", limit),
                (rs, i) -> new SavedRow(row(rs), instant(rs, "saved_at")));
    }

    /** since 이후 저장이 많은 순. 저장이 없는 글은 빠진다 */
    List<Row> popular(Instant since, int limit) {
        return jdbc.query("""
                SELECT s.id, s.category, s.title, s.save_count, s.created_at,
                """ + AUTHOR_COLUMNS + """
                FROM (SELECT share_id, COUNT(*) AS n FROM share_save WHERE created_at >= :since GROUP BY share_id) r
                JOIN share s ON s.id = r.share_id
                JOIN member m ON m.id = s.author_id
                WHERE s.deleted_at IS NULL
                ORDER BY r.n DESC, s.id DESC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("since", LocalDateTime.ofInstant(since, ZoneOffset.UTC))
                .addValue("limit", limit), (rs, i) -> row(rs));
    }

    long saveCount(long shareId) {
        return jdbc.queryForList("SELECT save_count FROM share WHERE id = :id", Map.of("id", shareId), Long.class)
                .stream().findFirst().orElse(0L);
    }

    Optional<DetailRow> find(long shareId, long viewerId) {
        return jdbc.query("""
                SELECT s.id, s.category, s.title, s.body, s.save_count, s.created_at, s.updated_at,
                       EXISTS (SELECT 1 FROM share_save v WHERE v.share_id = s.id AND v.member_id = :viewer) AS saved,
                """ + AUTHOR_COLUMNS + """
                FROM share s JOIN member m ON m.id = s.author_id
                WHERE s.id = :id AND s.deleted_at IS NULL
                """, new MapSqlParameterSource().addValue("id", shareId).addValue("viewer", viewerId),
                (rs, i) -> new DetailRow(rs.getLong("id"), ShareCategory.valueOf(rs.getString("category")),
                        rs.getString("title"), rs.getString("body"), author(rs), rs.getLong("save_count"),
                        rs.getBoolean("saved"), instant(rs, "created_at"), instant(rs, "updated_at")))
                .stream().findFirst();
    }

    Map<Long, List<String>> tags(Collection<Long> shareIds) {
        Map<Long, List<String>> tags = new HashMap<>();
        jdbc.query("SELECT share_id, name FROM share_tag WHERE share_id IN (:ids) ORDER BY share_id, name",
                Map.of("ids", shareIds), rs -> {
                    tags.computeIfAbsent(rs.getLong("share_id"), id -> new ArrayList<>()).add(rs.getString("name"));
                });
        return tags;
    }

    Map<Long, Long> noteCounts(Collection<Long> shareIds) {
        Map<Long, Long> counts = new HashMap<>();
        jdbc.query("""
                SELECT share_id, COUNT(*) AS n FROM share_note
                WHERE share_id IN (:ids) AND deleted_at IS NULL
                GROUP BY share_id
                """, Map.of("ids", shareIds), rs -> {
                    counts.put(rs.getLong("share_id"), rs.getLong("n"));
                });
        return counts;
    }

    Set<Long> savedAmong(Collection<Long> shareIds, long memberId) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT share_id FROM share_save WHERE share_id IN (:ids) AND member_id = :member",
                new MapSqlParameterSource().addValue("ids", shareIds).addValue("member", memberId), Long.class));
    }

    Set<Long> withPhotoAmong(Collection<Long> shareIds) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT DISTINCT owner_id FROM attachment WHERE owner_type = 'SHARE' AND owner_id IN (:ids)",
                Map.of("ids", shareIds), Long.class));
    }

    /** 오래된 순 전부. 지운 것은 빠진다 */
    List<NoteRow> notes(long shareId) {
        return jdbc.query("""
                SELECT n.id, n.author_id, m.nickname, m.withdrawn_at IS NOT NULL AS withdrawn, n.body, n.created_at
                FROM share_note n JOIN member m ON m.id = n.author_id
                WHERE n.share_id = :id AND n.deleted_at IS NULL
                ORDER BY n.id
                """, Map.of("id", shareId), (rs, i) -> new NoteRow(rs.getLong("id"), rs.getLong("author_id"),
                rs.getString("nickname"), rs.getBoolean("withdrawn"), rs.getString("body"),
                instant(rs, "created_at")));
    }

    private static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getLong("id"), ShareCategory.valueOf(rs.getString("category")), rs.getString("title"),
                author(rs), rs.getLong("save_count"), instant(rs, "created_at"));
    }

    private static AuthorRef.Row author(ResultSet rs) throws SQLException {
        return new AuthorRef.Row(rs.getLong("author_id"), rs.getString("nickname"), rs.getInt("generation"),
                Role.valueOf(rs.getString("role")), rs.getBoolean("withdrawn"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

}
