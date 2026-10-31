package com.gdghost.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class PhotoUrlWindowTest {

    private static final Instant HOUR = Instant.parse("2026-11-01T03:00:00Z");

    @Test
    void 평소에는_다음_정각에_만료된다() {
        PhotoUrlWindow window = PhotoUrlWindow.at(HOUR.plus(Duration.ofMinutes(50)));

        assertThat(window.signedAt()).isEqualTo(HOUR);
        assertThat(window.expiresAt()).isEqualTo(HOUR.plus(Duration.ofHours(1)));
        assertThat(window.ttl()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void 같은_시간대에는_창이_같다() {
        assertThat(PhotoUrlWindow.at(HOUR)).isEqualTo(PhotoUrlWindow.at(HOUR.plus(Duration.ofMinutes(49))));
    }

    @Test
    void 정각까지_10분_미만이면_그다음_정각까지() {
        PhotoUrlWindow window = PhotoUrlWindow.at(HOUR.plus(Duration.ofMinutes(51)));

        assertThat(window.signedAt()).isEqualTo(HOUR);
        assertThat(window.expiresAt()).isEqualTo(HOUR.plus(Duration.ofHours(2)));
    }

}
