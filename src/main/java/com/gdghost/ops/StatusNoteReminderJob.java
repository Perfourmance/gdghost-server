package com.gdghost.ops;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gdghost.common.batch.BatchRunner;
import com.gdghost.notification.NotificationType;
import com.gdghost.notification.NotificationWriter;

/** 학기 시작(3월 · 9월 1일) 전 부원에게 근황 업데이트를 요청한다. 전체 알림이라 웹 푸시는 보내지 않는다 */
@Component
public class StatusNoteReminderJob {

    static final String NAME = "status_note_reminder";
    static final String CRON = "0 0 9 1 3,9 *";

    private final BatchRunner batch;
    private final NotificationWriter notifications;

    public StatusNoteReminderJob(BatchRunner batch, NotificationWriter notifications) {
        this.batch = batch;
        this.notifications = notifications;
    }

    @Scheduled(cron = CRON, zone = "Asia/Seoul")
    public void run() {
        batch.run(NAME, () -> "notified=" + notifications.toAllOwnProfiles(NotificationType.STATUS_NOTE_REMINDER,
                "새 학기가 시작됐어요", "근황을 업데이트하면 맞는 질문이 와요"));
    }

}
