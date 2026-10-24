package com.gdghost.question;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * 조회수는 부원당 하루(KST) 1회. 서버 1대 전제라 메모리에 기억한다 — 재시작하면 그날 한 번 더 셀 수 있다.
 */
@Component
public class QuestionViewCounter {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final Clock clock;
    // 날짜가 키에 들어 있어 만료는 메모리 정리용
    private final Cache<String, Boolean> seen = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(1))
            .maximumSize(100_000)
            .build();

    public QuestionViewCounter(Clock clock) {
        this.clock = clock;
    }

    /** 오늘 처음 보는 거면 true */
    boolean firstViewToday(long memberId, long questionId) {
        String key = memberId + ":" + questionId + ":" + LocalDate.ofInstant(clock.instant(), KST);
        return seen.asMap().putIfAbsent(key, Boolean.TRUE) == null;
    }

}
