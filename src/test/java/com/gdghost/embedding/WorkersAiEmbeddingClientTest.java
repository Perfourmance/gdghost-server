package com.gdghost.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;

class WorkersAiEmbeddingClientTest {

    private static final String ACCOUNT = "secret-account";
    private static final String TOKEN = "secret-token";

    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> auth = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private volatile int failureStatus = 500;
    private volatile String response = response(1024);

    /** 2026-10-31 실제 호출에서 받은 모양 */
    private static String response(int dimension) {
        String vector = IntStream.range(0, dimension).mapToObj(i -> "0.25").collect(Collectors.joining(","));
        return """
                {"result":{"meta":{"cost_metric_name_1":"input_tokens","cost_metric_value_1":17,"neurons":0.018},
                "data":[[%s]],"response":null,"shape":[1,%d],"pooling":"cls"},
                "success":true,"errors":[],"messages":[]}
                """.formatted(vector, dimension);
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
            paths.add(exchange.getRequestURI().getRawPath());
            if (failuresLeft.getAndDecrement() > 0) {
                exchange.sendResponseHeaders(failureStatus, -1);
            } else {
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private WorkersAiEmbeddingClient client() {
        return new WorkersAiEmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort(), ACCOUNT, TOKEN,
                Duration.ZERO);
    }

    @Test
    void 실제_응답_모양에서_벡터를_꺼낸다() {
        List<float[]> vectors = client().embed(List.of("이력서"));

        assertThat(vectors).hasSize(1);
        assertThat(vectors.getFirst()).hasSize(1024);
        assertThat(vectors.getFirst()[0]).isEqualTo(0.25f);
        assertThat(paths.getFirst()).isEqualTo("/accounts/secret-account/ai/run/@cf/baai/bge-m3");
        assertThat(auth.getFirst()).isEqualTo("Bearer secret-token");
        assertThat(JsonPath.<List<String>>read(bodies.getFirst(), "$.text")).containsExactly("이력서");
        assertThat(JsonPath.<Boolean>read(bodies.getFirst(), "$.truncate_inputs")).isTrue();
    }

    @Test
    void 실패하면_한_번_더_부른다() {
        failuresLeft.set(1);

        assertThat(client().embed(List.of("이력서"))).hasSize(1);
        assertThat(bodies).hasSize(2);
    }

    @Test
    void 두_번_실패하면_상태_코드만_담아_던지고_비밀값은_없다() {
        failuresLeft.set(2);

        assertThatThrownBy(() -> client().embed(List.of("이력서")))
                .isInstanceOfSatisfying(EmbeddingException.class, e -> {
                    assertThat(e.isBadInput()).isFalse();
                    assertThat(e.getMessage()).contains("HTTP 500").doesNotContain(ACCOUNT, TOKEN);
                    assertThat(e.getCause()).isNull();
                });
        assertThat(bodies).hasSize(2);
    }

    @Test
    void 글_문제인_400은_다시_부르지_않는다() {
        failuresLeft.set(1);
        failureStatus = 400;

        assertThatThrownBy(() -> client().embed(List.of("이력서")))
                .isInstanceOfSatisfying(EmbeddingException.class, e -> assertThat(e.isBadInput()).isTrue());
        assertThat(bodies).hasSize(1);
    }

    @Test
    void 차원이_다르면_실패한다() {
        response = response(3);

        assertThatThrownBy(() -> client().embed(List.of("이력서"))).isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("차원");
    }

    @Test
    void 연결이_안_되면_종류만_담아_던진다() {
        WorkersAiEmbeddingClient client = client();
        server.stop(0);

        assertThatThrownBy(() -> client.embed(List.of("이력서"))).isInstanceOf(EmbeddingException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(ACCOUNT, TOKEN));
    }

    @Test
    void 키가_비면_꺼진_것으로_본다() {
        assertThat(new WorkersAiEmbeddingClient("http://x", "", "", Duration.ZERO).enabled()).isFalse();
        assertThat(new WorkersAiEmbeddingClient("http://x", ACCOUNT, TOKEN, Duration.ZERO).enabled()).isTrue();
    }

}
