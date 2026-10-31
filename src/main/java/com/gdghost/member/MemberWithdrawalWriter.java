package com.gdghost.member;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 탈퇴할 때 member 밖의 행 정리 — 글(질문 · 답변 · 공유 · 댓글)은 남긴다 */
@Repository
public class MemberWithdrawalWriter {

    private final JdbcTemplate jdbc;

    public MemberWithdrawalWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 활동 중인 운영진 수. 행을 잠가 두 운영진이 동시에 탈퇴해도 0명이 되지 않게 한다 */
    public int lockActiveStaffCount() {
        return jdbc.queryForList("SELECT id FROM member WHERE role = 'STAFF' AND withdrawn_at IS NULL FOR UPDATE",
                Long.class).size();
    }

    // TODO 피드 사진 R2 삭제(업로드 단계)
    public void erase(long memberId, String email, Instant now) {
        jdbc.update("DELETE FROM member_contact WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM member_tag WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM member_embedding WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM push_subscription WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM notification WHERE member_id = ?", memberId);
        jdbc.update("UPDATE email_verification SET email = NULL WHERE member_id = ? OR email = ?", memberId, email);
        jdbc.update("UPDATE email_change_log SET old_email = NULL, new_email = NULL WHERE member_id = ?", memberId);
        jdbc.update("""
                UPDATE feed_post SET deleted_at = ?, deleted_by = 'AUTHOR'
                WHERE author_id = ? AND deleted_at IS NULL
                """, now, memberId);
        jdbc.update("""
                UPDATE coffee_chat SET state = 'EXPIRED', responded_at = ?
                WHERE state = 'REQUESTED' AND (requester_id = ? OR receiver_id = ?)
                """, now, memberId, memberId);
        jdbc.update("""
                UPDATE routing SET state = 'CANCELED', responded_at = ?
                WHERE member_id = ? AND state = 'CANDIDATE'
                """, now, memberId);
        // 연결이 끊긴 사진은 정리 배치가 R2에서 지운다. 글과 인증 횟수는 남는다
        jdbc.update("""
                DELETE a FROM attachment a
                JOIN certification c ON a.owner_type = 'CERT' AND a.owner_id = c.id
                WHERE c.member_id = ?
                """, memberId);
        leaveRecruits(memberId, LocalDateTime.ofInstant(now, ZoneOffset.UTC));
    }

    /** 만나기 전 모집의 자리를 비우고 직접 연 모집은 닫는다. 지난 모집의 참여 기록은 남긴다 */
    private void leaveRecruits(long memberId, LocalDateTime now) {
        // 한 문장에서 state와 count를 함께 바꾸면 적용 순서가 보장되지 않아 나눈다
        jdbc.update("""
                UPDATE recruit r
                JOIN recruit_member rm ON rm.recruit_id = r.id AND rm.member_id = ?
                SET r.state = 'OPEN'
                WHERE r.meet_at > ? AND r.author_id <> ? AND r.state = 'CLOSED' AND r.joined_count >= r.capacity
                """, memberId, now, memberId);
        jdbc.update("""
                UPDATE recruit r
                JOIN recruit_member rm ON rm.recruit_id = r.id AND rm.member_id = ?
                SET r.joined_count = r.joined_count - 1
                WHERE r.meet_at > ? AND r.author_id <> ?
                """, memberId, now, memberId);
        jdbc.update("""
                DELETE rm FROM recruit_member rm
                JOIN recruit r ON r.id = rm.recruit_id
                WHERE rm.member_id = ? AND r.meet_at > ? AND r.author_id <> ?
                """, memberId, now, memberId);
        jdbc.update("UPDATE recruit SET state = 'CLOSED' WHERE author_id = ? AND state = 'OPEN'", memberId);
    }

}
