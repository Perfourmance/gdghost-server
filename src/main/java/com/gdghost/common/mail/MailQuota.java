package com.gdghost.common.mail;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;

/**
 * 서버 전체 하루 발송 상한(한국 날짜 기준). Gmail 하루 한도보다 낮게 잡아 계정이 막히지 않게 한다.
 * 서버 메모리에 세므로 재시작하면 0부터 — 서버 1대 전제.
 */
public class MailQuota {

    private static final Logger log = LoggerFactory.getLogger(MailQuota.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final Clock clock;
    private final int dailyLimit;
    private final Runnable onLimitReached;
    private LocalDate day;
    private int used;
    private boolean reported;

    public MailQuota(Clock clock, int dailyLimit) {
        this(clock, dailyLimit, () -> {
        });
    }

    /** @param onLimitReached 그날 처음 한도에 막혔을 때 한 번 (개발자 디스코드) */
    public MailQuota(Clock clock, int dailyLimit, Runnable onLimitReached) {
        this.clock = clock;
        this.dailyLimit = dailyLimit;
        this.onLimitReached = onLimitReached;
    }

    /** 한 통 몫을 쓴다. 다 썼으면 503 MAIL_QUOTA_EXCEEDED */
    public synchronized void acquire() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), KST);
        if (!today.equals(day)) {
            day = today;
            used = 0;
            reported = false;
        }
        if (used >= dailyLimit) {
            log.warn("오늘 메일 발송 한도({}통) 도달", dailyLimit);
            if (!reported) {
                reported = true;
                onLimitReached.run();
            }
            throw new BusinessException(ErrorCode.MAIL_QUOTA_EXCEEDED);
        }
        used++;
    }

}
