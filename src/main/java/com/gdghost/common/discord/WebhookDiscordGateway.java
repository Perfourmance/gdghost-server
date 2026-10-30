package com.gdghost.common.discord;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 웹훅 3개로 보낸다. 시간 초과 3초 · 1회 재시도. 주소가 비어 있는 채널은 보내지 않고 로그만(로컬).
 * 멘션은 어떤 것도 울리지 않는다.
 */
public class WebhookDiscordGateway implements DiscordGateway {

    private static final Logger log = LoggerFactory.getLogger(WebhookDiscordGateway.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final Map<DiscordChannel, String> webhooks;
    private final Duration retryDelay;
    private final RestClient client;

    public WebhookDiscordGateway(Map<DiscordChannel, String> webhooks, Duration retryDelay) {
        this.webhooks = webhooks;
        this.retryDelay = retryDelay;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void send(DiscordChannel channel, DiscordMessage message) {
        String webhook = webhooks.get(channel);
        if (webhook == null || webhook.isBlank()) {
            log.info("디스코드(보내지 않음) {} {} {}\n{}", channel, message.title(), message.url(),
                    message.description());
            return;
        }
        Map<String, Object> body = body(message);
        try {
            post(webhook, body);
        } catch (RestClientException first) {
            log.warn("디스코드 {} 전송 실패, 한 번 더 시도: {}", channel, reason(first));
            pause();
            try {
                post(webhook, body);
            } catch (RestClientException second) {
                // 웹훅 주소(비밀값)가 들어 있는 원래 예외는 기록에 넘기지 않는다
                throw new DiscordSendException(channel + " 전송 실패: " + reason(second));
            }
        }
    }

    private void post(String webhook, Map<String, Object> body) {
        client.post().uri(webhook).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
    }

    private static Map<String, Object> body(DiscordMessage message) {
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", message.title());
        if (message.url() != null) {
            embed.put("url", message.url());
        }
        if (message.description() != null) {
            embed.put("description", message.description());
        }
        return Map.of("embeds", List.of(embed), "allowed_mentions", Map.of("parse", new ArrayList<>()));
    }

    private static String reason(RestClientException e) {
        return e instanceof RestClientResponseException response
                ? "HTTP " + response.getStatusCode().value()
                : e.getClass().getSimpleName();
    }

    private void pause() {
        try {
            Thread.sleep(retryDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
