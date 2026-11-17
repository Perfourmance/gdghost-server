package com.gdghost.common.discord;

import java.net.http.HttpClient;
import java.time.Clock;
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
 * 멘션은 어떤 것도 울리지 않는다. 그림은 디스코드가 직접 가져가므로 https 주소일 때만 붙인다.
 */
public class WebhookDiscordGateway implements DiscordGateway {

    private static final Logger log = LoggerFactory.getLogger(WebhookDiscordGateway.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final String NAME = "GDGhost";

    private final Map<DiscordChannel, String> webhooks;
    private final Duration retryDelay;
    private final String imageBase;
    private final Clock clock;
    private final RestClient client;

    /** imageBase: 프론트 주소. public/의 icon-192.png · discord/*.png를 쓴다 */
    public WebhookDiscordGateway(Map<DiscordChannel, String> webhooks, Duration retryDelay, String imageBase,
            Clock clock) {
        this.webhooks = webhooks;
        this.retryDelay = retryDelay;
        this.imageBase = imageBase != null && imageBase.startsWith("https://") ? imageBase.replaceAll("/+$", "") : null;
        this.clock = clock;
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

    Map<String, Object> body(DiscordMessage message) {
        DiscordKind kind = message.kind();
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("color", kind.color());
        if (kind.headline() && message.description() != null) {
            // 분류는 작은 윗줄, 글 제목은 큰 링크
            embed.put("author", Map.of("name", kind.emoji() + " " + message.title()));
            embed.put("title", message.description());
            if (message.url() != null) {
                embed.put("url", message.url());
            }
            String guide = kind.guide();
            if (message.url() != null) {
                guide += "\n[바로 보기 →](" + message.url() + ")";
            }
            embed.put("description", guide);
        } else {
            embed.put("title", kind.emoji() + " " + message.title());
            if (message.url() != null) {
                embed.put("url", message.url());
            }
            if (message.description() != null) {
                embed.put("description", message.description());
            }
        }
        if (!message.fields().isEmpty()) {
            embed.put("fields", message.fields().stream()
                    .map(field -> Map.of("name", field.name(), "value", field.value(), "inline", field.inline()))
                    .toList());
        }
        embed.put("timestamp", clock.instant().toString());
        Map<String, Object> footer = new LinkedHashMap<>();
        footer.put("text", NAME);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", NAME);
        if (imageBase != null) {
            embed.put("thumbnail", Map.of("url", imageBase + "/discord/" + kind.image()));
            footer.put("icon_url", imageBase + "/icon-192.png");
            body.put("avatar_url", imageBase + "/icon-192.png");
        }
        embed.put("footer", footer);
        body.put("embeds", List.of(embed));
        body.put("allowed_mentions", Map.of("parse", new ArrayList<>()));
        return body;
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
