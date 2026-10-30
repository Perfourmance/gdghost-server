package com.gdghost.common.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;

class WebhookDiscordGatewayTest {

    private static final DiscordMessage MESSAGE =
            new DiscordMessage("새 질문 · 취업·채용", "http://localhost:3000/questions/1", "인턴 준비 @everyone");

    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private String webhook;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/webhooks/1/secret-token", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int status = failuresLeft.getAndDecrement() > 0 ? 500 : 204;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        webhook = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/webhooks/1/secret-token";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void embed_하나로_보내고_멘션은_막는다() {
        gateway(webhook).send(DiscordChannel.MEMBER, MESSAGE);

        assertThat(bodies).hasSize(1);
        String body = bodies.getFirst();
        assertThat(JsonPath.<String>read(body, "$.embeds[0].title")).isEqualTo(MESSAGE.title());
        assertThat(JsonPath.<String>read(body, "$.embeds[0].url")).isEqualTo(MESSAGE.url());
        assertThat(JsonPath.<String>read(body, "$.embeds[0].description")).isEqualTo(MESSAGE.description());
        assertThat(JsonPath.<List<?>>read(body, "$.allowed_mentions.parse")).isEmpty();
    }

    @Test
    void 링크_본문이_없으면_빼고_보낸다() {
        gateway(webhook).send(DiscordChannel.STAFF, new DiscordMessage("계정 정지", null, null));

        assertThat(JsonPath.<Map<String, Object>>read(bodies.getFirst(), "$.embeds[0]")).containsOnlyKeys("title");
    }

    @Test
    void 실패하면_한_번_더_보낸다() {
        failuresLeft.set(1);

        gateway(webhook).send(DiscordChannel.STAFF, MESSAGE);

        assertThat(bodies).hasSize(2);
    }

    @Test
    void 두_번_실패하면_웹훅_주소_없이_예외() {
        failuresLeft.set(2);

        assertThatThrownBy(() -> gateway(webhook).send(DiscordChannel.STAFF, MESSAGE))
                .isInstanceOf(DiscordSendException.class)
                .hasMessage("STAFF 전송 실패: HTTP 500")
                .hasNoCause();
        assertThat(bodies).hasSize(2);
    }

    @Test
    void 연결이_안_되면_이름만_남긴다() {
        server.stop(0);

        assertThatThrownBy(() -> gateway(webhook).send(DiscordChannel.DEV, MESSAGE))
                .isInstanceOf(DiscordSendException.class)
                .hasMessageStartingWith("DEV 전송 실패: ")
                .hasMessageNotContaining("secret-token");
    }

    @Test
    void 주소가_비어_있는_채널은_보내지_않는다() {
        new WebhookDiscordGateway(Map.of(DiscordChannel.MEMBER, ""), Duration.ZERO)
                .send(DiscordChannel.MEMBER, MESSAGE);
        new WebhookDiscordGateway(Map.of(), Duration.ZERO).send(DiscordChannel.DEV, MESSAGE);

        assertThat(bodies).isEmpty();
    }

    @Test
    void 사용자_글의_마크다운은_글자로_보이게() {
        assertThat(DiscordMessage.escape("[여기](https://x.y) **굵게** <@123>"))
                .isEqualTo("\\[여기\\]\\(https://x.y\\) \\*\\*굵게\\*\\* \\<@123\\>");
        assertThat(DiscordMessage.escape(null)).isEmpty();
    }

    private WebhookDiscordGateway gateway(String url) {
        return new WebhookDiscordGateway(
                Map.of(DiscordChannel.MEMBER, url, DiscordChannel.STAFF, url, DiscordChannel.DEV, url),
                Duration.ZERO);
    }

}
