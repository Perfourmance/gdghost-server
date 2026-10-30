package com.gdghost.notification;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 알림 저장. 잃지 않게 알림을 만든 기능과 같은 트랜잭션에서 부른다 (커밋 후 리스너 X).
 * 받는 사람은 활동 중 부원만 — 탈퇴 · 정지한 사람에게는 쌓지 않는다.
 * 웹 푸시는 커밋 후 리스너가 보내고, 트랜잭션이 롤백되면 나가지 않는다.
 */
@Repository
public class NotificationWriter {

    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public NotificationWriter(NamedParameterJdbcTemplate jdbc, ApplicationEventPublisher events, Clock clock) {
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    /** 나에게 오는 알림 — 웹 푸시도 함께 */
    public void toMember(long memberId, NotificationType type, String title, String body, NotificationTarget target) {
        jdbc.update("""
                INSERT INTO notification (member_id, type, title, body, target_type, target_id,
                                          sub_target_type, sub_target_id, created_at)
                SELECT id, :type, :title, :body, :targetType, :targetId, :subType, :subId, :now
                FROM member
                WHERE id = :memberId AND withdrawn_at IS NULL AND suspended_at IS NULL
                """, params(type, title, body, target).addValue("memberId", memberId));
        events.publishEvent(NotificationPushEvent.to(List.of(memberId), type, title, body, target));
    }

    /**
     * 부원 전체 — INSERT … SELECT 한 번. 쓴 사람은 뺀다.
     *
     * @param push 필독 공지처럼 푸시까지 보낼 때만. 새 질문 같은 전체 알림은 false
     */
    public void toAllExcept(long excludedId, NotificationType type, String title, String body,
            NotificationTarget target, boolean push) {
        jdbc.update("""
                INSERT INTO notification (member_id, type, title, body, target_type, target_id,
                                          sub_target_type, sub_target_id, created_at)
                SELECT id, :type, :title, :body, :targetType, :targetId, :subType, :subId, :now
                FROM member
                WHERE id <> :excludedId AND withdrawn_at IS NULL AND suspended_at IS NULL
                """, params(type, title, body, target).addValue("excludedId", excludedId));
        if (push) {
            events.publishEvent(NotificationPushEvent.toAllExcept(excludedId, type, title, body, target));
        }
    }

    /**
     * 정해진 사람들 — 재알림처럼 대상을 따로 고른 경우.
     *
     * @param push false면 앱 내 알림만. 푸시를 미룰 때
     */
    public void toMembers(List<Long> memberIds, NotificationType type, String title, String body,
            NotificationTarget target, boolean push) {
        if (memberIds.isEmpty()) {
            return;
        }
        jdbc.update("""
                INSERT INTO notification (member_id, type, title, body, target_type, target_id,
                                          sub_target_type, sub_target_id, created_at)
                SELECT id, :type, :title, :body, :targetType, :targetId, :subType, :subId, :now
                FROM member
                WHERE id IN (:memberIds) AND withdrawn_at IS NULL AND suspended_at IS NULL
                """, params(type, title, body, target).addValue("memberIds", memberIds));
        if (push) {
            events.publishEvent(NotificationPushEvent.to(memberIds, type, title, body, target));
        }
    }

    /** @return 실제로 읽음이 된 수. 남의 알림 id는 조용히 무시 */
    int markRead(long memberId, List<Long> ids) {
        return jdbc.update("""
                UPDATE notification SET read_at = :now
                WHERE member_id = :memberId AND id IN (:ids) AND read_at IS NULL
                """, Map.of("now", LocalDateTime.now(clock), "memberId", memberId, "ids", ids));
    }

    int markAllRead(long memberId) {
        return jdbc.update("""
                UPDATE notification SET read_at = :now WHERE member_id = :memberId AND read_at IS NULL
                """, Map.of("now", LocalDateTime.now(clock), "memberId", memberId));
    }

    private MapSqlParameterSource params(NotificationType type, String title, String body,
            NotificationTarget target) {
        return new MapSqlParameterSource()
                .addValue("type", type.name())
                .addValue("title", title)
                .addValue("body", body)
                .addValue("targetType", target.type())
                .addValue("targetId", target.id())
                .addValue("subType", target.subType())
                .addValue("subId", target.subId())
                .addValue("now", LocalDateTime.now(clock));
    }

}
