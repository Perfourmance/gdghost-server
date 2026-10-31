package com.gdghost.recruit;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recruit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long authorId;
    private Long scheduleId;

    @Enumerated(EnumType.STRING)
    private RecruitKind kind;

    private String title;
    private Instant meetAt;
    private int capacity;
    private int joinedCount;

    @Enumerated(EnumType.STRING)
    private RecruitState state;

    private String memo;
    private Instant createdAt;

    /** 개설자가 참여자 1명으로 들어간 상태로 시작한다 */
    static Recruit open(long authorId, Long scheduleId, RecruitKind kind, String title, Instant meetAt, int capacity,
            String memo, Instant now) {
        Recruit recruit = new Recruit();
        recruit.authorId = authorId;
        recruit.scheduleId = scheduleId;
        recruit.kind = kind;
        recruit.title = title;
        recruit.meetAt = meetAt;
        recruit.capacity = capacity;
        recruit.joinedCount = 1;
        recruit.state = RecruitState.OPEN;
        recruit.memo = memo;
        recruit.createdAt = now;
        return recruit;
    }

    boolean isTimePassed(Instant now) {
        return !meetAt.isAfter(now);
    }

    boolean isFull() {
        return joinedCount >= capacity;
    }

    /** 배치가 닫기 전이어도 시간이 지났으면 마감으로 본다 */
    RecruitState stateAt(Instant now) {
        return isTimePassed(now) ? RecruitState.CLOSED : state;
    }

    void join() {
        joinedCount++;
        if (isFull()) {
            state = RecruitState.CLOSED;
        }
    }

    /** 다 차서 닫혔던 모집은 자리가 나면 다시 연다 */
    void leave() {
        if (isFull()) {
            state = RecruitState.OPEN;
        }
        joinedCount--;
    }

    void close() {
        state = RecruitState.CLOSED;
    }

}
