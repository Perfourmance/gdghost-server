package com.gdghost.common.push;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VAPID로 서명해 보낸다. 시간 초과 5초 · 재시도 없음. 키가 비어 있으면 보내지 않고 로그만(로컬).
 * 구독 주소는 부원이 보낸 값이라 리다이렉트를 따라가지 않는다.
 */
public class WebPushGateway implements PushGateway {

    private static final Logger log = LoggerFactory.getLogger(WebPushGateway.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(12);
    private static final Duration MESSAGE_TTL = Duration.ofDays(1);

    private final String publicKey;
    private final ECPrivateKey privateKey;
    private final String subject;
    private final Clock clock;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** @param subject 푸시 서비스가 문제 때 연락할 곳(https 주소 또는 mailto) */
    public WebPushGateway(String publicKey, String privateKey, String subject, Clock clock) {
        this.publicKey = publicKey;
        this.subject = subject;
        this.clock = clock;
        try {
            this.privateKey = privateKey.isBlank() ? null : WebPushCrypto.privateKey(
                    WebPushCrypto.decodeBase64Url(privateKey));
            if (!publicKey.isBlank()) {
                WebPushCrypto.publicKey(WebPushCrypto.decodeBase64Url(publicKey));
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // 값이 로그에 남지 않게 원인을 붙이지 않는다
            throw new IllegalStateException("VAPID 키 형식이 잘못됐습니다");
        }
    }

    @Override
    public PushResult send(PushTarget target, String payload) {
        if (privateKey == null || publicKey.isBlank()) {
            log.info("VAPID 키 없음 — 웹 푸시를 보내지 않음: {}", payload);
            return PushResult.SENT;
        }
        try {
            URI endpoint = URI.create(target.endpoint());
            byte[] body = WebPushCrypto.encrypt(payload.getBytes(StandardCharsets.UTF_8),
                    WebPushCrypto.decodeBase64Url(target.p256dh()), WebPushCrypto.decodeBase64Url(target.auth()));
            String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
            String token = WebPushCrypto.vapidToken(privateKey, audience, subject,
                    clock.instant().plus(TOKEN_LIFETIME).getEpochSecond());
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(TIMEOUT)
                    .header("Authorization", "vapid t=" + token + ", k=" + publicKey)
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", String.valueOf(MESSAGE_TTL.toSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 404 || status == 410) {
                return PushResult.GONE;
            }
            if (status / 100 != 2) {
                throw new PushSendException("푸시 서비스 응답 " + status);
            }
            return PushResult.SENT;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new PushSendException("구독 정보로 암호화할 수 없음: " + e.getClass().getSimpleName());
        } catch (IOException e) {
            throw new PushSendException("푸시 서비스 연결 실패: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PushSendException("푸시 전송 중단");
        }
    }

}
