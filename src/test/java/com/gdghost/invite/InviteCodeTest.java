package com.gdghost.invite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InviteCodeTest {

    static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");

    @Test
    void 기한이_남고_횟수가_남으면_ACTIVE() {
        assertThat(code(10, 9, NOW.plusSeconds(1)).state(NOW)).isEqualTo(InviteCodeState.ACTIVE);
    }

    @Test
    void 횟수를_다_쓰면_EXHAUSTED() {
        assertThat(code(10, 10, NOW.plusSeconds(1)).state(NOW)).isEqualTo(InviteCodeState.EXHAUSTED);
    }

    @Test
    void 만료_시각이_되면_EXPIRED() {
        assertThat(code(10, 0, NOW).state(NOW)).isEqualTo(InviteCodeState.EXPIRED);
    }

    @Test
    void 만료와_소진이_겹치면_EXPIRED() {
        assertThat(code(10, 10, NOW.minusSeconds(1)).state(NOW)).isEqualTo(InviteCodeState.EXPIRED);
    }

    @Test
    void 폐기가_가장_먼저() {
        InviteCode code = code(10, 10, NOW.minusSeconds(1));
        code.revoke(NOW);

        assertThat(code.state(NOW)).isEqualTo(InviteCodeState.REVOKED);
    }

    @Test
    void 두_번_폐기해도_처음_시각을_유지한다() {
        InviteCode code = code(10, 0, NOW.plusSeconds(60));
        code.revoke(NOW);
        code.revoke(NOW.plusSeconds(30));

        assertThat(code.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void 무작위_코드는_헷갈리는_글자_없이_8자() {
        Random random = new Random(1);
        for (int i = 0; i < 1000; i++) {
            assertThat(InviteCodeService.randomCode(random))
                    .hasSize(8)
                    .matches("[A-Z2-9]+")
                    .doesNotContain("O", "I", "0", "1");
        }
    }

    private static InviteCode code(int maxUses, int usedCount, Instant expiresAt) {
        InviteCode code = InviteCode.issue("ABCDEFGH", 5, 1L, maxUses, expiresAt, NOW.minus(Duration.ofDays(1)));
        ReflectionTestUtils.setField(code, "usedCount", usedCount);
        return code;
    }

}
