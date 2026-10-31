package com.gdghost.routing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gdghost.common.batch.BatchRunner;
import com.gdghost.common.time.QuietHours;

/** 1시간 운영진 · 3시간 부원. 22–08시에는 쉬고, 그동안 지난 것은 08시 회차가 보낸다 */
@Component
public class RoutingJob {

    static final String NAME = "routing_10min";
    private static final Duration STAFF_AFTER = Duration.ofHours(1);
    static final Duration MEMBER_AFTER = Duration.ofHours(3);
    static final Duration STALE_AFTER = Duration.ofDays(3);

    private final BatchRunner batch;
    private final RoutingDelivery delivery;
    private final MatchingConfig config;
    private final RoutingReader reader;
    private final Clock clock;

    public RoutingJob(BatchRunner batch, RoutingDelivery delivery, MatchingConfig config, RoutingReader reader,
            Clock clock) {
        this.batch = batch;
        this.delivery = delivery;
        this.config = config;
        this.reader = reader;
        this.clock = clock;
    }

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void run() {
        batch.run(NAME, this::route);
    }

    private String route() {
        Instant now = clock.instant();
        if (QuietHours.contains(now)) {
            return "quiet";
        }
        int direct = 0;
        for (RoutingReader.Scheduled scheduled : reader.scheduledDirects()) {
            direct += delivery.sendScheduled(scheduled) ? 1 : 0;
        }
        Instant oldest = now.minus(STALE_AFTER);
        Instant directBefore = now.minus(Duration.ofHours(config.directTimeoutHours()));
        int staff = 0;
        for (long questionId : reader.waitingForStaff(now.minus(STAFF_AFTER), oldest, directBefore)) {
            staff += delivery.sendToStaff(questionId) ? 1 : 0;
        }
        int sent = direct;
        // 오래된 질문부터 — 앞 질문에서 받은 사람은 하루 3건 · 연속 지목에 바로 반영된다
        for (RoutingReader.Waiting waiting : reader.waitingForMembers(now.minus(MEMBER_AFTER), oldest, directBefore)) {
            sent += delivery.sendToMembers(waiting);
        }
        return "sent=%d staff=%d".formatted(sent, staff);
    }

}
