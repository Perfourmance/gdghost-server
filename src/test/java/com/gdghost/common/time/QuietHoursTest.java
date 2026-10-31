package com.gdghost.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class QuietHoursTest {

    @ParameterizedTest
    @CsvSource({
            "2026-10-30T12:59:59Z, false", // KST 21:59:59
            "2026-10-30T13:00:00Z, true",  // KST 22:00
            "2026-10-30T15:00:00Z, true",  // KST 00:00
            "2026-10-30T22:59:59Z, true",  // KST 07:59:59
            "2026-10-30T23:00:00Z, false"  // KST 08:00
    })
    void 한국_시간_22시부터_08시_전까지(String utc, boolean quiet) {
        assertThat(QuietHours.contains(Instant.parse(utc))).isEqualTo(quiet);
    }

    @Test
    void 아침_8시에는_어젯밤_22시부터_지금까지가_구간이다() {
        Instant morning = Instant.parse("2026-10-30T23:00:00Z"); // KST 10/31 08:00

        assertThat(QuietHours.lastStart(morning)).isEqualTo(Instant.parse("2026-10-30T13:00:00Z"));
        assertThat(QuietHours.lastEnd(morning)).isEqualTo(morning);
    }

}
