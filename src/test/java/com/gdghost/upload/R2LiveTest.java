package com.gdghost.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.gdghost.TestcontainersConfiguration;
import com.gdghost.support.FakeDiscordGateway;
import com.gdghost.support.FakeMailGateway;
import com.gdghost.support.FakePushGateway;

/**
 * 실제 R2를 부른다. 평소에는 건너뛰고, 서명 코드를 고쳤을 때 직접 돌린다.
 * 키는 .env에서 읽는다: LIVE_R2=true ./gradlew test --tests '*R2LiveTest'
 */
@EnabledIfEnvironmentVariable(named = "LIVE_R2", matches = "true")
@SpringBootTest
@Import({TestcontainersConfiguration.class, FakeMailGateway.class, FakeDiscordGateway.class, FakePushGateway.class})
class R2LiveTest {

    private static final byte[] PHOTO = new byte[1024];

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    ObjectStorage storage;

    private int put(URI url, String contentType, byte[] body) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(url).header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private HttpResponse<Void> preflight(URI url, String origin) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(url).header("Origin", origin)
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "content-type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding());
    }

    @Test
    void 서명한_형식과_크기로만_올라가고_열람_주소로_읽힌다() throws Exception {
        assertThat(storage).isInstanceOf(R2ObjectStorage.class);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String key = UploadKeys.generate(UploadPurpose.FEED, 0, "webp", now);
        URI upload = storage.presignPut(key, "image/webp", PHOTO.length, now, Duration.ofMinutes(10));
        try {
            assertThat(preflight(upload, "http://localhost:3000").headers()
                    .firstValue("Access-Control-Allow-Origin")).hasValue("http://localhost:3000");
            assertThat(preflight(upload, "https://evil.example").headers()
                    .firstValue("Access-Control-Allow-Origin")).isEmpty();

            assertThat(put(upload, "image/webp", new byte[PHOTO.length + 1])).isEqualTo(403);
            assertThat(put(upload, "image/png", PHOTO)).isEqualTo(403);
            assertThat(storage.sizeOf(key)).isEmpty();

            assertThat(put(upload, "image/webp", PHOTO)).isEqualTo(200);
            assertThat(storage.sizeOf(key)).hasValue(PHOTO.length);

            PhotoUrlWindow window = PhotoUrlWindow.at(now);
            URI view = storage.presignGet(key, window.signedAt(), window.ttl());
            HttpResponse<byte[]> photo = http.send(HttpRequest.newBuilder(view).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(photo.statusCode()).isEqualTo(200);
            assertThat(photo.body()).hasSize(PHOTO.length);
        } finally {
            storage.delete(key);
        }
        assertThat(storage.sizeOf(key)).isEmpty();
    }

}
