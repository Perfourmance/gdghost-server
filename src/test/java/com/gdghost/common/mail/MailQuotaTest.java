package com.gdghost.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.support.MutableClock;

class MailQuotaTest {

    @Test
    void 하루_한도까지_보내고_넘으면_503_MAIL_QUOTA_EXCEEDED() {
        MailQuota quota = new MailQuota(new MutableClock(Instant.parse("2026-10-11T03:00:00Z")), 3);
        quota.acquire();
        quota.acquire();
        quota.acquire();

        assertThatThrownBy(quota::acquire)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MAIL_QUOTA_EXCEEDED));
    }

    @Test
    void 한국_날짜가_바뀌면_다시_센다() {
        // UTC 14:59 = KST 23:59
        MutableClock clock = new MutableClock(Instant.parse("2026-10-11T14:59:00Z"));
        MailQuota quota = new MailQuota(clock, 1);
        quota.acquire();
        assertThatThrownBy(quota::acquire).isInstanceOf(BusinessException.class);

        clock.advance(Duration.ofMinutes(1));

        assertThatCode(quota::acquire).doesNotThrowAnyException();
    }

}
