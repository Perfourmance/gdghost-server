package com.gdghost.ops;

import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.audit.OpsAction;
import com.gdghost.common.audit.OpsAudit;
import com.gdghost.common.audit.OpsAuditLogger;
import com.gdghost.member.MemberRepository;
import com.gdghost.notification.NotificationType;
import com.gdghost.notification.NotificationWriter;

/**
 * 졸업 처리. 학교의 실제 졸업 여부는 알 수 없어 운영진이 골라서 확정한다.
 * 상태는 임베딩 재료가 아니라 필터라 임베딩은 다시 계산하지 않는다.
 */
@Service
public class GraduationService {

    private final MemberRepository members;
    private final GraduationWriter writer;
    private final OpsAuditLogger opsAudit;
    private final NotificationWriter notifications;

    public GraduationService(MemberRepository members, GraduationWriter writer, OpsAuditLogger opsAudit,
            NotificationWriter notifications) {
        this.members = members;
        this.writer = writer;
        this.opsAudit = opsAudit;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public GraduationCandidatesResponse candidates(Integer generation, String studentNoMax) {
        return new GraduationCandidatesResponse(members.findGraduationCandidates(generation, studentNoMax).stream()
                .map(GraduationCandidatesResponse.Item::from)
                .toList());
    }

    /** 이미 졸업 · 탈퇴 · 정지이거나 없는 부원은 건너뛴다. 다시 불러도 알림이 겹치지 않는다 */
    @Transactional
    public GraduationResponse graduate(long staffId, List<Long> memberIds) {
        List<Long> requested = memberIds.stream().distinct().toList();
        List<Long> updated = writer.graduate(requested);
        for (long memberId : updated) {
            opsAudit.record(OpsAudit.of(staffId, OpsAction.GRADUATE).targetMember(memberId));
        }
        notifications.toOwnProfiles(updated, NotificationType.GRADUATED, "졸업을 축하해요",
                "근황을 업데이트하면 맞는 질문이 와요");
        HashSet<Long> done = new HashSet<>(updated);
        return new GraduationResponse(updated.size(), requested.stream().filter(id -> !done.contains(id)).toList());
    }

}
