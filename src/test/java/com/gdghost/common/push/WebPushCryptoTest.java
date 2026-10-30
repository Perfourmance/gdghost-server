package com.gdghost.common.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class WebPushCryptoTest {

    // RFC 8291 Appendix A
    private static final String SENDER_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String SENDER_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String RECEIVER_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String MESSAGE = "When I grow up, I want to be a watermelon";
    private static final String EXPECTED = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll"
            + "6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxs"
            + "j_Qulcy4a-fN";

    @Test
    void RFC_8291_예시와_같은_암호문을_만든다() throws Exception {
        KeyPair sender = new KeyPair(WebPushCrypto.publicKey(WebPushCrypto.decodeBase64Url(SENDER_PUBLIC)),
                WebPushCrypto.privateKey(WebPushCrypto.decodeBase64Url(SENDER_PRIVATE)));

        byte[] encrypted = WebPushCrypto.encrypt(MESSAGE.getBytes(StandardCharsets.UTF_8),
                WebPushCrypto.decodeBase64Url(RECEIVER_PUBLIC), WebPushCrypto.decodeBase64Url(AUTH), sender,
                WebPushCrypto.decodeBase64Url(SALT));

        assertThat(WebPushCrypto.base64Url(encrypted)).isEqualTo(EXPECTED);
    }

    @Test
    void 메시지마다_다른_키와_salt를_쓴다() throws Exception {
        byte[] plain = MESSAGE.getBytes(StandardCharsets.UTF_8);
        byte[] first = WebPushCrypto.encrypt(plain, WebPushCrypto.decodeBase64Url(RECEIVER_PUBLIC),
                WebPushCrypto.decodeBase64Url(AUTH));
        byte[] second = WebPushCrypto.encrypt(plain, WebPushCrypto.decodeBase64Url(RECEIVER_PUBLIC),
                WebPushCrypto.decodeBase64Url(AUTH));

        assertThat(WebPushCrypto.base64Url(first)).isNotEqualTo(WebPushCrypto.base64Url(second));
    }

    @Test
    void VAPID_토큰은_공개키로_검증되는_ES256_JWT다() throws Exception {
        long exp = Instant.parse("2026-11-01T00:00:00Z").getEpochSecond();

        String token = WebPushCrypto.vapidToken(WebPushCrypto.privateKey(WebPushCrypto.decodeBase64Url(SENDER_PRIVATE)),
                "https://fcm.googleapis.com", "https://example.com", exp);

        String[] parts = token.split("\\.");
        assertThat(new String(WebPushCrypto.decodeBase64Url(parts[0]), StandardCharsets.UTF_8))
                .isEqualTo("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
        assertThat(new String(WebPushCrypto.decodeBase64Url(parts[1]), StandardCharsets.UTF_8))
                .isEqualTo("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":" + exp + ",\"sub\":\"https://example.com\"}");
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(WebPushCrypto.publicKey(WebPushCrypto.decodeBase64Url(SENDER_PUBLIC)));
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(WebPushCrypto.decodeBase64Url(parts[2])).hasSize(64);
        assertThat(verifier.verify(WebPushCrypto.decodeBase64Url(parts[2]))).isTrue();
    }

}
