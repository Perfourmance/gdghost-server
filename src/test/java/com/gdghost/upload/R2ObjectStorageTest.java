package com.gdghost.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class R2ObjectStorageTest {

    private static final String KEY = "feed/2026/11/1/0123456789abcdef0123456789abcdef.webp";
    private static final Instant SIGNED_AT = Instant.parse("2026-11-01T03:00:00Z");

    private HttpServer server;
    private R2ObjectStorage storage;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            requests.add(method + " " + path);
            exchange.getRequestBody().readAllBytes();
            if (method.equals("HEAD")) {
                if (path.endsWith(KEY)) {
                    exchange.getResponseHeaders().add("Content-Length", "1234");
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            } else if (method.equals("GET")) {
                byte[] body = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <ListBucketResult><Name>photos</Name><IsTruncated>false</IsTruncated>
                        <Contents><Key>old.webp</Key><LastModified>2026-10-01T00:00:00.000Z</LastModified><Size>1</Size></Contents>
                        <Contents><Key>new.webp</Key><LastModified>2026-11-01T00:00:00.000Z</LastModified><Size>1</Size></Contents>
                        </ListBucketResult>
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/xml");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } else {
                exchange.sendResponseHeaders(204, -1);
            }
            exchange.close();
        });
        server.start();
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        storage = new R2ObjectStorage(endpoint, "photos", "access-key", "secret-key");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void 올리기_주소는_형식과_크기를_서명에_넣는다() {
        URI url = storage.presignPut(KEY, "image/webp", 284000, SIGNED_AT, Duration.ofMinutes(10));

        assertThat(url.getPath()).isEqualTo("/photos/" + KEY);
        assertThat(url.getQuery())
                .contains("X-Amz-Date=20261101T030000Z")
                .contains("X-Amz-Expires=600")
                .contains("X-Amz-SignedHeaders=content-length;content-type;host")
                .contains("X-Amz-Signature=")
                .doesNotContain("secret-key");
        assertThat(storage.presignPut(KEY, "image/webp", 284001, SIGNED_AT, Duration.ofMinutes(10))).isNotEqualTo(url);
    }

    @Test
    void 열람_주소는_같은_창이면_같다() {
        URI url = storage.presignGet(KEY, SIGNED_AT, Duration.ofHours(1));

        assertThat(storage.presignGet(KEY, SIGNED_AT, Duration.ofHours(1))).isEqualTo(url);
        assertThat(url.getQuery()).contains("X-Amz-Expires=3600").contains("X-Amz-Date=20261101T030000Z");
        assertThat(storage.presignGet(KEY, SIGNED_AT.plus(Duration.ofHours(1)), Duration.ofHours(1))).isNotEqualTo(url);
    }

    @Test
    void 크기를_읽고_없으면_빈_값() {
        assertThat(storage.sizeOf(KEY)).hasValue(1234);
        assertThat(storage.sizeOf("feed/none.webp")).isEmpty();
    }

    @Test
    void 지우고_오래된_키를_고른다() {
        storage.delete(KEY);

        assertThat(requests).contains("DELETE /photos/" + KEY);
        assertThat(storage.keysModifiedBefore(Instant.parse("2026-10-15T00:00:00Z"))).containsExactly("old.webp");
    }

}
