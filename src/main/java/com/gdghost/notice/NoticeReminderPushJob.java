package com.gdghost.notice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gdghost.common.batch.BatchRunner;
import com.gdghost.common.time.QuietHours;
import com.gdghost.notification.NotificationPushEvent;
import com.gdghost.notification.NotificationPusher;
import com.gdghost.notification.NotificationTarget;
import com.gdghost.notification.NotificationType;

/**
 * 22–08시에 누른 재알림의 푸시를 08시에 보낸다. 대상은 DB에서 다시 찾으므로 서버가 재시작돼도 잃지 않는다.
 * 그사이 확인한 사람에게는 보내지 않는다.
 */
@Component
public class NoticeReminderPushJob {

    static final String NAME = "notice_remind_push";

    private final BatchRunner batch;
    private final NoticeReminderReader reader;
    private final NotificationPusher pusher;
    private final Clock clock;

    public NoticeReminderPushJob(BatchRunner batch, NoticeReminderReader reader, NotificationPusher pusher,
            Clock clock) {
        this.batch = batch;
        this.reader = reader;
        this.pusher = pusher;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    public void run() {
        batch.run(NAME, this::pushDeferred);
    }

    private String pushDeferred() {
        Instant now = clock.instant();
        int members = 0;
        List<NoticeReminderReader.Reminded> reminded = reader.remindedBetween(QuietHours.lastStart(now),
                QuietHours.lastEnd(now));
        for (NoticeReminderReader.Reminded notice : reminded) {
            List<Long> receivers = reader.stillUnconfirmedReceivers(notice.noticeId());
            members += receivers.size();
            if (!receivers.isEmpty()) {
                pusher.push(NotificationPushEvent.to(receivers, NotificationType.NOTICE_REMIND,
                        NoticeReminderService.TITLE, notice.title(), NotificationTarget.notice(notice.noticeId())));
            }
        }
        return "notices=%d members=%d".formatted(reminded.size(), members);
    }

}
