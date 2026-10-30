package com.gdghost.common.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

/** 실제 HTTP로 — 요청 헤더와 응답 코드 처리 */
class WebPushGatewayTest {

    private static final String PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String RECEIVER =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

    private HttpServer server;
    private int status;
    private final AtomicReference<Headers> received = new AtomicReference<>();
    private final WebPushGateway gateway = new WebPushGateway(PUBLIC, PRIVATE, "https://example.com",
            Clock.systemUTC());

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.set(exchange.getRequestHeaders());
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private PushTarget target() {
        return new PushTarget("http://127.0.0.1:" + server.getAddress().getPort() + "/push", RECEIVER, AUTH);
    }

    @Test
    void 성공하면_VAPID와_암호화_헤더를_담아_보낸다() {
        status = 201;

        assertThat(gateway.send(target(), "{\"title\":\"제목\"}")).isEqualTo(PushResult.SENT);

        assertThat(received.get().getFirst("Authorization")).startsWith("vapid t=").endsWith(", k=" + PUBLIC);
        assertThat(received.get().getFirst("Content-Encoding")).isEqualTo("aes128gcm");
        assertThat(received.get().getFirst("TTL")).isEqualTo("86400");
    }

    @Test
    void 구독이_사라졌으면_GONE() {
        status = 410;
        assertThat(gateway.send(target(), "{}")).isEqualTo(PushResult.GONE);
        status = 404;
        assertThat(gateway.send(target(), "{}")).isEqualTo(PushResult.GONE);
    }

    @Test
    void 그_밖의_실패는_주소_없이_예외로() {
        status = 500;
        assertThatThrownBy(() -> gateway.send(target(), "{}"))
                .isInstanceOf(PushSendException.class)
                .hasMessageNotContaining("127.0.0.1");
    }

    @Test
    void 키가_없으면_보내지_않는다() {
        status = 500;
        WebPushGateway local = new WebPushGateway("", "", "http://localhost:3000", Clock.systemUTC());

        assertThat(local.send(target(), "{}")).isEqualTo(PushResult.SENT);
        assertThat(received.get()).isNull();
    }

    @Test
    void 키_형식이_틀리면_값을_드러내지_않고_뜨지_않는다() {
        assertThatThrownBy(() -> new WebPushGateway("short", "!!!secret!!!", "https://example.com", Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("secret")
                .hasNoCause();
    }

}
