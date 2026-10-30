package com.gdghost.common.config;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.discord.DiscordGateway;
import com.gdghost.common.discord.DiscordNotifier;
import com.gdghost.common.discord.WebhookDiscordGateway;
import com.gdghost.common.error.ErrorLogRecorder;

@Configuration
public class DiscordConfig {

    /** required(운영)면 주소 3개가 모두 https여야 뜬다 — 빈 값으로 알림이 조용히 사라지지 않게 */
    @Bean
    DiscordGateway discordGateway(@Value("${discord.webhook.member}") String member,
            @Value("${discord.webhook.staff}") String staff, @Value("${discord.webhook.dev}") String dev,
            @Value("${discord.required:false}") boolean required) {
        Map<DiscordChannel, String> webhooks = new EnumMap<>(DiscordChannel.class);
        webhooks.put(DiscordChannel.MEMBER, member);
        webhooks.put(DiscordChannel.STAFF, staff);
        webhooks.put(DiscordChannel.DEV, dev);
        if (required) {
            webhooks.forEach((channel, url) -> {
                if (url == null || !url.startsWith("https://")) {
                    throw new IllegalStateException("디스코드 " + channel + " 웹훅 주소가 없습니다");
                }
            });
        }
        return new WebhookDiscordGateway(webhooks, Duration.ofSeconds(1));
    }

    @Bean
    DiscordNotifier discordNotifier(DiscordGateway gateway,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor, ErrorLogRecorder errorLog) {
        return new DiscordNotifier(gateway, executor, errorLog);
    }

}
