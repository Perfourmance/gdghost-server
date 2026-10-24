package com.gdghost.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.gdghost.support.MutableClock;

class QuestionViewCounterTest {

    // KST 2026-10-18 23:00
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-18T14:00:00Z"));
    private final QuestionViewCounter counter = new QuestionViewCounter(clock);

    @Test
    void 같은_날에는_한_번만_센다() {
        assertThat(counter.firstViewToday(1, 10)).isTrue();
        assertThat(counter.firstViewToday(1, 10)).isFalse();
        assertThat(counter.firstViewToday(2, 10)).isTrue();
        assertThat(counter.firstViewToday(1, 11)).isTrue();
    }

    @Test
    void 한국_날짜가_바뀌면_다시_센다() {
        counter.firstViewToday(1, 10);
        clock.advance(Duration.ofHours(1));

        assertThat(counter.firstViewToday(1, 10)).isTrue();
    }

}
