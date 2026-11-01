package com.gdghost.ops;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.audit.OpsAction;
import com.gdghost.common.audit.OpsAudit;
import com.gdghost.common.audit.OpsAuditLogger;
import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.security.Role;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;
import com.gdghost.notification.NotificationTarget;
import com.gdghost.notification.NotificationType;
import com.gdghost.notification.NotificationWriter;

/**
 * 운영진 지정 · 해제. 지정은 첫 운영진(어드민)과 운영진끼리의 지정이 같이 쓴다.
 * 본인이 한 변경은 알림을 보내지 않는다.
 */
@Service
public class StaffService {

    private final MemberRepository members;
    private final OpsAuditLogger opsAudit;
    private final NotificationWriter notifications;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public StaffService(MemberRepository members, OpsAuditLogger opsAudit, NotificationWriter notifications,
            ApplicationEventPublisher events, Clock clock) {
        this.members = members;
        this.opsAudit = opsAudit;
        this.notifications = notifications;
        this.events = events;
        this.clock = clock;
    }

    /** 이미 운영진이면 직함만 바꾼다. 같은 직함이면 기록 · 알림 없이 지금 상태를 돌려준다 */
    @Transactional
    public StaffResponse assign(long actorId, long memberId, String staffTitle) {
        // 행 전체를 UPDATE하므로 잠그지 않으면 동시 탈퇴로 지운 개인정보가 되살아날 수 있다
        Member member = members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        boolean wasStaff = member.getRole() == Role.STAFF;
        boolean sameTitle = wasStaff && staffTitle.equals(member.getStaffTitle());
        member.assignStaff(staffTitle, clock.instant());
        if (sameTitle) {
            return StaffResponse.from(member);
        }
        opsAudit.record(OpsAudit.of(actorId, OpsAction.STAFF_ASSIGN)
                .targetMember(memberId)
                .detail(Map.of("staffTitle", staffTitle)));
        if (actorId != memberId) {
            notifications.toMember(memberId, NotificationType.STAFF_ROLE_CHANGED,
                    wasStaff ? "운영진 직함이 바뀌었어요" : "운영진으로 지정됐어요", staffTitle,
                    NotificationTarget.settings(memberId));
        }
        events.publishEvent(new StaffRoleChangedEvent(actorId, memberId));
        return StaffResponse.from(member);
    }

    /** 운영진 행을 먼저 잠가 동시 해제 · 탈퇴를 줄 세운다. 웹 푸시는 보내지 않는다 (API 설계서) */
    @Transactional
    public void revoke(long actorId, long memberId) {
        int activeStaff = members.lockActiveStaffIds().size();
        Member member = members.findByIdForUpdate(memberId)
                .filter(found -> found.getWithdrawnAt() == null && found.getRole() == Role.STAFF)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (activeStaff <= 1) {
            throw new BusinessException(ErrorCode.LAST_STAFF, "마지막 운영진은 해제할 수 없습니다. 후임을 먼저 지정해 주세요.");
        }
        String staffTitle = member.getStaffTitle();
        member.revokeStaff(clock.instant());
        opsAudit.record(OpsAudit.of(actorId, OpsAction.STAFF_REVOKE)
                .targetMember(memberId)
                .detail(Collections.singletonMap("staffTitle", staffTitle)));
        if (actorId != memberId) {
            notifications.toMembers(List.of(memberId), NotificationType.STAFF_ROLE_CHANGED, "운영진에서 해제됐어요",
                    "이제 운영 메뉴를 쓸 수 없어요", NotificationTarget.settings(memberId), false);
        }
        events.publishEvent(new StaffRevokedEvent(actorId, memberId, staffTitle));
    }

}
