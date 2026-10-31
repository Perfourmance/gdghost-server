package com.gdghost.routing;

import java.time.Clock;
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

    private String digest() {
        List<RoutingReader.Stale> stale = reader.staleQuestions(clock.instant().minus(RoutingJob.STALE_AFTER));
        if (!stale.isEmpty()) {
            String titles = stale.stream().limit(MAX_TITLES)
                    .map(question -> "· " + DiscordMessage.escape(question.title()))
                    .collect(Collectors.joining("\n"));
            discord.send(DiscordChannel.STAFF, new DiscordMessage("3일 넘게 답이 없는 질문 " + stale.size() + "건", null, titles));
        }
        return "stale=%d".formatted(stale.size());
    }

}
