package com.gdghost.routing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gdghost.common.batch.BatchRunner;
import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.discord.DiscordMessage;
import com.gdghost.common.discord.DiscordNotifier;

/** 운영진 09시 요약. 지금은 3일 넘게 답이 없는 질문만, 인증 확인 대기는 M10에서 더한다 */
@Component
public class OpsDigestJob {

    static final String NAME = "digest_ops_daily_09";
    private static final int MAX_TITLES = 20;

    private final BatchRunner batch;
    private final RoutingReader reader;
    private final DiscordNotifier discord;
    private final Clock clock;

    public OpsDigestJob(BatchRunner batch, RoutingReader reader, DiscordNotifier discord, Clock clock) {
        this.batch = batch;
        this.reader = reader;
        this.discord = discord;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void run() {
        batch.run(NAME, this::digest);
    }

    /** 어제부터 오늘 사이 3일을 넘긴 것만 적는다 — 같은 질문이 매일 반복되지 않게. 전체는 운영 화면에서 본다 */
    private String digest() {
        Instant threshold = clock.instant().minus(RoutingJob.STALE_AFTER);
        List<RoutingReader.Stale> stale = reader.staleQuestions(threshold);
        Instant dayBefore = threshold.minus(Duration.ofDays(1));
        List<RoutingReader.Stale> fresh = stale.stream()
                .filter(question -> question.createdAt().isAfter(dayBefore))
                .toList();
        if (!fresh.isEmpty()) {
            String titles = fresh.stream().limit(MAX_TITLES)
                    .map(question -> "· " + DiscordMessage.escape(question.title()))
                    .collect(Collectors.joining("\n"));
            discord.send(DiscordChannel.STAFF, new DiscordMessage(
                    "3일 넘게 답이 없는 질문 " + fresh.size() + "건 추가 (전체 " + stale.size() + "건)", null, titles));
        }
        return "stale=%d new=%d".formatted(stale.size(), fresh.size());
    }

}
