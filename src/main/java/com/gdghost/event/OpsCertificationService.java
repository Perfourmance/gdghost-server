package com.gdghost.event;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.audit.OpsAction;
import com.gdghost.common.audit.OpsAudit;
import com.gdghost.common.audit.OpsAuditLogger;
import com.gdghost.common.audit.OpsTargetType;
import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.page.OpsPage;
import com.gdghost.common.security.AuthMember;
import com.gdghost.notification.NotificationTarget;
import com.gdghost.notification.NotificationType;
import com.gdghost.notification.NotificationWriter;
import com.gdghost.question.AuthorRef;
import com.gdghost.upload.Attachment;
import com.gdghost.upload.AttachmentService;
import com.gdghost.upload.UploadPurpose;

/** 인증 확인 대기 목록 · 승인 · 반려 */
@Service
public class OpsCertificationService {

    private final CertificationRepository certifications;
    private final EventReader reader;
    private final EventService eventService;
    private final AttachmentService attachments;
    private final NotificationWriter notifications;
    private final OpsAuditLogger opsAudit;
    private final Clock clock;

    public OpsCertificationService(CertificationRepository certifications, EventReader reader,
            EventService eventService, AttachmentService attachments, NotificationWriter notifications,
            OpsAuditLogger opsAudit, Clock clock) {
        this.certifications = certifications;
        this.reader = reader;
        this.eventService = eventService;
        this.attachments = attachments;
        this.notifications = notifications;
        this.opsAudit = opsAudit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OpsPage<OpsCertificationItem> list(AuthMember me, CertificationState state, Long eventId, int page,
            int size) {
        List<EventReader.OpsRow> rows = reader.opsCertifications(state, eventId, page * size, size);
        Map<Long, List<Attachment>> photos = attachments.photos(UploadPurpose.CERT,
                rows.stream().map(row -> row.certification().id()).toList());
        List<OpsCertificationItem> items = rows.stream().map(row -> {
            EventReader.CertificationRow certification = row.certification();
            return new OpsCertificationItem(certification.id(), row.event(),
                    AuthorRef.of(certification.author(), false, null, me.id()), certification.body(),
                    photos.getOrDefault(certification.id(), List.of()), certification.state(),
                    certification.rejectReason(), certification.author().id() == me.id(), certification.createdAt());
        }).toList();
        return new OpsPage<>(items, page, size, reader.countOps(state, eventId));
    }

    /** 행을 잠가 두 운영진이 동시에 눌러도 먼저 처리한 쪽만 남는다 */
    @Transactional
    public CertificationItem review(AuthMember me, long certificationId, CertificationReviewRequest request) {
        Certification certification = certifications.findForUpdate(certificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "인증을 찾을 수 없습니다."));
        if (certification.getState() != CertificationState.PENDING) {
            throw new BusinessException(ErrorCode.CERT_ALREADY_REVIEWED, "이미 검토된 인증입니다.");
        }
        boolean approve = request.decision() == CertificationReviewRequest.Decision.APPROVE;
        if (approve) {
            certification.approve(me.id(), clock.instant());
        } else {
            certification.reject(me.id(), request.rejectReason(), clock.instant());
        }
        certifications.flush();

        opsAudit.record(OpsAudit.of(me.id(), OpsAction.CERT_REVIEW)
                .targetMember(certification.getMemberId())
                .target(OpsTargetType.CERTIFICATION, certificationId)
                .detail(Map.of("decision", request.decision().name())));
        NotificationTarget target = new NotificationTarget("EVENT", certification.getEventId(), "CERTIFICATION",
                certificationId);
        String eventName = reader.find(certification.getEventId()).orElseThrow().name();
        if (approve) {
            notifications.toMember(certification.getMemberId(), NotificationType.CERT_APPROVED, "인증이 승인됐어요",
                    eventName, target);
        } else {
            notifications.toMember(certification.getMemberId(), NotificationType.CERT_REJECTED, "인증이 반려됐어요",
                    request.rejectReason(), target);
        }
        return eventService.certificationItem(me, certificationId);
    }

}
