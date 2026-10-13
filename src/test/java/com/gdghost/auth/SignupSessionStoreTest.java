package com.gdghost.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.support.MutableClock;

class SignupSessionStoreTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-13T00:00:00Z"));
    private final SignupSessionStore store = new SignupSessionStore(clock);

    @Test
    void 발급한_토큰으로_가입_상태를_읽는다() {
        SignupSession opened = store.open(7, 5);

        assertThat(opened.token()).startsWith("st_");
        assertThat(opened.expiresAt()).isEqualTo(Instant.parse("2026-10-13T00:30:00Z"));
        assertThat(store.get(opened.token())).isEqualTo(opened);
    }

    @Test
    void 삼십분이_지나면_만료() {
        String token = store.open(7, 5).token();
        clock.advance(Duration.ofMinutes(30));

        assertThatThrownBy(() -> store.get(token))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.SIGNUP_SESSION_EXPIRED));
    }

    @Test
    void 없는_토큰은_만료와_같다() {
        assertThatThrownBy(() -> store.get("st_unknown"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.SIGNUP_SESSION_EXPIRED));
    }

    @Test
    void 인증된_메일을_기록해도_만료_시각은_그대로() {
        SignupSession opened = store.open(7, 5);
        clock.advance(Duration.ofMinutes(10));

        store.markVerified(opened.token(), "hong@gmail.com");

        assertThat(store.get(opened.token()).verifiedEmail()).isEqualTo("hong@gmail.com");
        assertThat(store.get(opened.token()).expiresAt()).isEqualTo(opened.expiresAt());
    }

}
