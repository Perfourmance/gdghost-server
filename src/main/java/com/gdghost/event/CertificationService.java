package com.gdghost.event;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.security.AuthMember;
import com.gdghost.upload.AttachmentService;
import com.gdghost.upload.UploadPurpose;

/** 인증 올리기 · 반려된 인증 다시 올리기. 둘 다 이벤트 기간 안에만 */
@Service
public class CertificationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CertificationRepository certifications;
    private final EventReader reader;
    private final EventService eventService;
    private final AttachmentService attachments;
    private final Clock clock;

    public CertificationService(CertificationRepository certifications, EventReader reader, EventService eventService,
            AttachmentService attachments, Clock clock) {
        this.certifications = certifications;
        this.reader = reader;
        this.eventService = eventService;
        this.attachments = attachments;
        this.clock = clock;
    }

    @Transactional
    public CertificationItem submit(AuthMember me, long eventId, CertificationCreateRequest request) {
        EventReader.DetailRow event = reader.find(eventId).orElseThrow(CertificationService::notFound);
        checkOngoing(event);
        Certification certification = certifications.saveAndFlush(
                Certification.submit(eventId, me.id(), request.body(), clock.instant()));
        attachments.attach(UploadPurpose.CERT, certification.getId(), me.id(), request.photoKeys());
        return eventService.certificationItem(me, certification.getId());
    }

    /** 행을 잠가 운영진의 검토와 엇갈리지 않게 한다 */
    @Transactional
    public CertificationItem resubmit(AuthMember me, long certificationId, CertificationUpdateRequest request) {
        Certification certification = certifications.findForUpdate(certificationId)
                .orElseThrow(CertificationService::notFound);
        if (certification.getMemberId() != me.id()) {
            throw new BusinessException(ErrorCode.NOT_AUTHOR);
        }
        if (certification.getState() != CertificationState.REJECTED) {
            throw new BusinessException(ErrorCode.CERT_NOT_REJECTED, "반려된 인증만 다시 올릴 수 있습니다.");
        }
        checkOngoing(reader.find(certification.getEventId()).orElseThrow(CertificationService::notFound));
        if (request.photoKeys() != null) {
            attachments.replace(UploadPurpose.CERT, certificationId, me.id(), request.photoKeys());
        }
        certification.resubmit(request.body(), clock.instant());
        certifications.flush();
        return eventService.certificationItem(me, certificationId);
    }

    private void checkOngoing(EventReader.DetailRow event) {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        if (EventState.of(event.startDate(), event.endDate(), today) != EventState.ONGOING) {
            throw new BusinessException(ErrorCode.EVENT_CLOSED, "이벤트 기간이 아닙니다.");
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "찾을 수 없습니다.");
    }

}
