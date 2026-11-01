package com.gdghost.coffee;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CoffeeChatWriter {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public CoffeeChatWriter(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** 정지할 때. 알림은 보내지 않는다 (탈퇴와 같음) */
    public void expirePending(long memberId) {
        jdbc.update("""
                UPDATE coffee_chat SET state = 'EXPIRED', responded_at = :now
                WHERE state = 'REQUESTED' AND (requester_id = :member OR receiver_id = :member)
                """, Map.of("now", LocalDateTime.now(clock), "member", memberId));
    }

    /** 7일 무응답을 만료하고 신청자에게 앱 내 알림. 새벽에 돌므로 웹 푸시는 보내지 않는다 @return 만료한 수 */
    @Transactional
    public int expireOverdue() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM coffee_chat
                WHERE state = 'REQUESTED' AND created_at <= :cutoff
                ORDER BY id
                FOR UPDATE
                """, Map.of("cutoff", now.minus(CoffeeChat.RESPONSE_WINDOW)), Long.class);
        if (ids.isEmpty()) {
            return 0;
        }
        jdbc.update("UPDATE coffee_chat SET state = 'EXPIRED', responded_at = :now WHERE id IN (:ids)",
                Map.of("now", now, "ids", ids));
        jdbc.update("""
                INSERT INTO notification (member_id, type, title, body, target_type, target_id, created_at)
                SELECT c.requester_id, 'COFFEE_EXPIRED', '커피챗 신청이 만료됐어요',
                       CONCAT(r.nickname, '님이 7일 동안 응답하지 않았어요'), 'COFFEE_CHAT', c.id, :now
                FROM coffee_chat c
                JOIN member q ON q.id = c.requester_id
                JOIN member r ON r.id = c.receiver_id
                WHERE c.id IN (:ids) AND q.withdrawn_at IS NULL AND q.suspended_at IS NULL
                """, Map.of("now", now, "ids", ids));
        return ids.size();
    }

    /** 공개 기간(수락 후 14일)이 지난 메모를 지운다. 연락처가 담겨 있어 더 들고 있지 않는다 @return 지운 수 */
    public int clearClosedMemos() {
        return jdbc.update("""
                UPDATE coffee_chat SET accept_memo = NULL
                WHERE state = 'ACCEPTED' AND accept_memo IS NOT NULL AND responded_at <= :cutoff
                """, Map.of("cutoff", LocalDateTime.now(clock).minus(CoffeeChat.MEMO_WINDOW)));
    }

}
