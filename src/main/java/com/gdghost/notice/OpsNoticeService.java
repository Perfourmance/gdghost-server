package com.gdghost.notice;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.common.page.OpsPage;
import com.gdghost.common.security.AuthMember;
import com.gdghost.notification.NotificationTarget;
import com.gdghost.notification.NotificationType;
import com.gdghost.notification.NotificationWriter;

/** 운영진의 공지 작성 · 목록 · 수정 · 삭제. 다른 운영진이 쓴 공지도 고치고 지울 수 있다 (인계 대비) */
@Service
public class OpsNoticeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NoticeRepository notices;
    private final NoticeReadWriter readWriter;
    private final NoticeService noticeService;
    private final OpsNoticeReader opsReader;
    private final NotificationWriter notifications;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public OpsNoticeService(NoticeRepository notices, NoticeReadWriter readWriter, NoticeService noticeService,
            OpsNoticeReader opsReader, NotificationWriter notifications, ApplicationEventPublisher events, Clock clock) {
        this.notices = notices;
        this.readWriter = readWriter;
        this.noticeService = noticeService;
        this.opsReader = opsReader;
        this.notifications = notifications;
        this.events = events;
        this.clock = clock;
    }

    /** 작성자는 바로 확인함. 웹 푸시는 필독만 */
    @Transactional
    public NoticeDetail create(AuthMember me, NoticeCreateRequest request) {
        checkDueDate(request.dueDate());
        Notice notice = notices.saveAndFlush(Notice.write(me.id(), request.title(), request.body(), request.pinned(),
                request.mustRead(), request.dueDate(), clock.instant()));
        readWriter.confirm(notice.getId(), me.id(), clock.instant());
        notifications.toAllExcept(me.id(), NotificationType.NOTICE,
                notice.isMustRead() ? "필독 공지가 올라왔어요" : "새 공지가 올라왔어요", notice.getTitle(),
                NotificationTarget.notice(notice.getId()), notice.isMustRead());
        events.publishEvent(new NoticeCreatedEvent(notice.getId()));
        return noticeService.detail(notice.getId(), me);
    }

    @Transactional(readOnly = true)
    public OpsPage<OpsNoticeResponse> list(int page, int size) {
        return opsReader.list(page, size);
    }

    @Transactional
    public NoticeDetail update(AuthMember me, long noticeId, NoticeUpdateRequest request) {
        checkDueDate(request.dueDate());
        Notice notice = find(noticeId);
        notice.edit(request.title(), request.body(), request.isPinned(), request.isMustRead(), request.dueDate(),
                request.clearingDueDate(), clock.instant());
        notices.flush();
        return noticeService.detail(noticeId, me);
    }

    /** 이미 디스코드에 올라간 메시지는 그대로 (웹훅은 보내기만 한다) */
    @Transactional
    public void delete(long noticeId) {
        find(noticeId).delete(clock.instant());
    }

    /** 마감일은 오늘(한국 날짜)부터. 보낼 때만 검사해서 지난 공지의 다른 필드는 고칠 수 있다 */
    private void checkDueDate(LocalDate dueDate) {
        if (dueDate != null && dueDate.isBefore(LocalDate.ofInstant(clock.instant(), KST))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                    List.of(new ErrorField("dueDate", "마감일은 오늘 이후여야 합니다.")));
        }
    }

    private Notice find(long noticeId) {
        return notices.findById(noticeId)
                .filter(notice -> !notice.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "공지를 찾을 수 없습니다."));
    }

}
