package com.gdghost.notice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.time.QuietHours;
import com.gdghost.notification.NotificationTarget;
import com.gdghost.notification.NotificationType;
import com.gdghost.notification.NotificationWriter;

/** 미확인 공지 재알림. 공지당 1회, 디스코드는 쓰지 않는다 */
@Service
public class NoticeReminderService {

    static final String TITLE = "아직 확인하지 않은 공지가 있어요";

    private final NoticeRepository notices;
    private final NoticeReminderReader reader;
    private final NotificationWriter notifications;
    private final Clock clock;

    public NoticeReminderService(NoticeRepository notices, NoticeReminderReader reader,
            NotificationWriter notifications, Clock clock) {
        this.notices = notices;
        this.reader = reader;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** 앱 내 알림은 바로. 22–08시면 푸시만 08시 배치로 미룬다 */
    @Transactional
    public NoticeReminderResponse remind(long noticeId) {
        Notice notice = notices.findById(noticeId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "공지를 찾을 수 없습니다."));
        Instant now = clock.instant();
        // 조건부 UPDATE라 두 운영진이 동시에 눌러도 한 번만 나간다
        if (notices.markReminded(noticeId, now) == 0) {
            throw new BusinessException(ErrorCode.NOTICE_REMIND_USED, "이 공지는 이미 한 번 더 알렸습니다.");
        }
        List<Long> targets = reader.unconfirmedMembers(noticeId);
        notifications.toMembers(targets, NotificationType.NOTICE_REMIND, TITLE, notice.getTitle(),
                NotificationTarget.notice(noticeId), !QuietHours.contains(now));
        return new NoticeReminderResponse(targets.size(), now);
    }

}
