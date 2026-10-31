package com.gdghost.schedule;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.gdghost.common.batch.BatchRunner;
import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.discord.DiscordMessage;
import com.gdghost.common.discord.DiscordNotifier;
import com.gdghost.notification.NotificationTarget;
import com.gdghost.notification.NotificationType;
import com.gdghost.notification.NotificationWriter;

/** 매일 09시 D-3 · D-1. 일정은 부원 채널에 묶어 올리고 연결된 모집 참여자에게, 모집은 만나는 날 기준으로 참여자에게 개인 알림 */
@Component
public class DeadlineJob {

    static final String NAME = "deadline_daily_09";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_LINES = 20;

    private final BatchRunner batch;
    private final ScheduleReader reader;
    private final NotificationWriter notifications;
    private final DiscordNotifier discord;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String webUrl;

    public DeadlineJob(BatchRunner batch, ScheduleReader reader, NotificationWriter notifications,
            DiscordNotifier discord, TransactionTemplate transaction, Clock clock,
            @Value("${web.url}") String webUrl) {
        this.batch = batch;
        this.reader = reader;
        this.notifications = notifications;
        this.discord = discord;
        this.transaction = transaction;
        this.clock = clock;
        this.webUrl = webUrl;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void run() {
        batch.run(NAME, this::notifyDue);
    }

    private String notifyDue() {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        List<ScheduleReader.Due> due = reader.dueOn(List.of(today.plusDays(1), today.plusDays(3)));
        if (!due.isEmpty()) {
            discord.send(DiscordChannel.MEMBER, new DiscordMessage("마감 임박 일정 " + due.size() + "건", null,
                    lines(due, today)));
        }
        int notified = 0;
        for (ScheduleReader.Due schedule : due) {
            List<Long> members = reader.participants(schedule.id());
            notify(members, "마감 임박 " + label(schedule, today), schedule.title(),
                    NotificationTarget.schedule(schedule.id()));
            notified += members.size();
        }
        int recruits = 0;
        for (int days : List.of(1, 3)) {
            LocalDate date = today.plusDays(days);
            for (ScheduleReader.Meeting recruit : reader.recruitsMeeting(utcStart(date), utcStart(date.plusDays(1)))) {
                List<Long> members = reader.recruitParticipants(recruit.id());
                notify(members, "모집 D-" + days, recruit.title(), NotificationTarget.recruit(recruit.id()));
                notified += members.size();
                recruits++;
            }
        }
        return "due=%d recruits=%d notified=%d".formatted(due.size(), recruits, notified);
    }

    // 푸시는 커밋 후에 나가므로 대상마다 트랜잭션으로 묶는다
    private void notify(List<Long> members, String title, String body, NotificationTarget target) {
        transaction.executeWithoutResult(status -> notifications.toMembers(members, NotificationType.DEADLINE, title,
                body, target, true));
    }

    private static LocalDateTime utcStart(LocalDate kstDate) {
        return LocalDateTime.ofInstant(kstDate.atStartOfDay(KST).toInstant(), ZoneOffset.UTC);
    }

    private String lines(List<ScheduleReader.Due> due, LocalDate today) {
        String lines = due.stream().limit(MAX_LINES)
                .map(schedule -> "· %s %s\n%s/schedules/%d".formatted(label(schedule, today),
                        DiscordMessage.escape(schedule.title()), webUrl, schedule.id()))
                .collect(Collectors.joining("\n"));
        return due.size() > MAX_LINES ? lines + "\n외 " + (due.size() - MAX_LINES) + "건" : lines;
    }

    private static String label(ScheduleReader.Due schedule, LocalDate today) {
        return "D-" + ChronoUnit.DAYS.between(today, schedule.deadline());
    }

}
