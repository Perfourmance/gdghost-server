package com.gdghost.ops;

import java.time.Clock;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.audit.OpsAction;
import com.gdghost.common.audit.OpsAudit;
import com.gdghost.common.audit.OpsAuditLogger;
import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;
import com.gdghost.notification.NotificationTarget;
import com.gdghost.notification.NotificationType;
import com.gdghost.notification.NotificationWriter;

/**
 * 운영진 지정. 첫 운영진(어드민)과 운영진끼리의 지정(10단계)이 같이 쓴다.
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

    @Transactional
    public StaffResponse assign(long actorId, long memberId, String staffTitle) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        member.assignStaff(staffTitle, clock.instant());
        opsAudit.record(OpsAudit.of(actorId, OpsAction.STAFF_ASSIGN)
                .targetMember(memberId)
                .detail(Map.of("staffTitle", staffTitle)));
        notifications.toMember(memberId, NotificationType.STAFF_ROLE_CHANGED, "운영진으로 지정됐어요", staffTitle,
                NotificationTarget.settings(memberId));
        events.publishEvent(new StaffRoleChangedEvent(actorId, memberId));
        return StaffResponse.from(member);
    }

}
