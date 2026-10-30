package com.gdghost.notification;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gdghost.common.push.PushTarget;
import com.gdghost.common.security.TokenHasher;

@Repository
public class PushSubscriptionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PushSubscriptionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    boolean exists(String endpoint) {
        return !jdbc.queryForList("SELECT id FROM push_subscription WHERE endpoint_hash = :hash",
                Map.of("hash", TokenHasher.sha256(endpoint)), Long.class).isEmpty();
    }

    /** 이미 있는 endpoint면 주인을 지금 부원으로 — 한 기기를 다른 계정으로 로그인한 경우 */
    long save(long memberId, PushTarget target) {
        String hash = TokenHasher.sha256(target.endpoint());
        jdbc.update("""
                INSERT INTO push_subscription (member_id, endpoint, endpoint_hash, p256dh, auth)
                VALUES (:memberId, :endpoint, :hash, :p256dh, :auth) AS new
                ON DUPLICATE KEY UPDATE member_id = new.member_id, p256dh = new.p256dh, auth = new.auth
                """, new MapSqlParameterSource()
                .addValue("memberId", memberId)
                .addValue("endpoint", target.endpoint())
                .addValue("hash", hash)
                .addValue("p256dh", target.p256dh())
                .addValue("auth", target.auth()));
        jdbc.update("UPDATE member SET push_on = TRUE WHERE id = :memberId", Map.of("memberId", memberId));
        return jdbc.queryForObject("SELECT id FROM push_subscription WHERE endpoint_hash = :hash",
                Map.of("hash", hash), Long.class);
    }

    void delete(long memberId, String endpoint) {
        jdbc.update("DELETE FROM push_subscription WHERE endpoint_hash = :hash AND member_id = :memberId",
                Map.of("hash", TokenHasher.sha256(endpoint), "memberId", memberId));
    }

    void deleteGone(String endpoint) {
        jdbc.update("DELETE FROM push_subscription WHERE endpoint_hash = :hash",
                Map.of("hash", TokenHasher.sha256(endpoint)));
    }

    /** 푸시를 켠 활동 중 부원의 구독. memberIds가 있으면 그 사람들만, 없으면 excludedId를 뺀 전체 */
    List<PushTarget> targets(List<Long> memberIds, Long excludedId) {
        if (memberIds != null && memberIds.isEmpty()) {
            return List.of();
        }
        String who = memberIds != null ? "m.id IN (:memberIds)" : "m.id <> :excludedId";
        return jdbc.query("""
                SELECT s.endpoint, s.p256dh, s.auth
                FROM push_subscription s
                JOIN member m ON m.id = s.member_id
                WHERE m.push_on = TRUE AND m.withdrawn_at IS NULL AND m.suspended_at IS NULL AND
                """ + who, new MapSqlParameterSource().addValue("memberIds", memberIds)
                .addValue("excludedId", excludedId),
                (rs, i) -> new PushTarget(rs.getString("endpoint"), rs.getString("p256dh"), rs.getString("auth")));
    }

}
