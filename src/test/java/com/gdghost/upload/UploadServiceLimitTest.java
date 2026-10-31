package com.gdghost.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.gdghost.common.ratelimit.RateLimitedException;
import com.gdghost.common.ratelimit.RateLimiter;
import com.gdghost.support.FakeObjectStorage;
import com.gdghost.support.MutableClock;

class UploadServiceLimitTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-01T03:00:00Z"));
    private final UploadService service = new UploadService(new FakeObjectStorage(), new RateLimiter(clock), clock);

    private PresignResponse upload(long member, int files) {
        return service.presign(member, new PresignRequest(UploadPurpose.FEED,
                Collections.nCopies(files, new PresignRequest.File("image/webp", 1L))));
    }

    /** 분당 제한에 걸리지 않게 시간을 넘기며 n개를 쓴다 */
    private void use(long member, int files) {
        for (int i = 0; i < files; i += 3) {
            upload(member, Math.min(3, files - i));
            clock.advance(Duration.ofSeconds(20));
        }
    }

    @Test
    void 하루_30개를_넘으면_막히고_하루가_지나면_풀린다() {
        use(1, 30);

        assertThatThrownBy(() -> upload(1, 1)).isInstanceOf(RateLimitedException.class);
        assertThat(upload(2, 1).items()).hasSize(1);

        clock.advance(Duration.ofDays(1));
        assertThat(upload(1, 1).items()).hasSize(1);
    }

    @Test
    void 남은_개수보다_많이_요청하면_세지_않고_거절한다() {
        use(1, 29);

        assertThatThrownBy(() -> upload(1, 2)).isInstanceOf(RateLimitedException.class);
        assertThat(upload(1, 1).items()).hasSize(1);
    }

    @Test
    void 분당_제한에_막힌_요청은_하루_몫을_쓰지_않는다() {
        for (int i = 0; i < 6; i++) {
            upload(1, 3);
        }
        upload(1, 2);
        assertThatThrownBy(() -> upload(1, 3)).isInstanceOf(RateLimitedException.class);

        clock.advance(Duration.ofMinutes(1));
        assertThat(upload(1, 3).items()).hasSize(3);
        assertThat(upload(1, 3).items()).hasSize(3);
        assertThat(upload(1, 3).items()).hasSize(3);
        assertThat(upload(1, 1).items()).hasSize(1);
        assertThatThrownBy(() -> upload(1, 1)).isInstanceOf(RateLimitedException.class);
    }

}
