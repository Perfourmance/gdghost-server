package com.gdghost.recruit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.common.page.CursorPage;

@Service
public class RecruitService {

    private static final Duration MAX_AHEAD = Duration.ofDays(90);

    private final RecruitRepository recruits;
    private final RecruitReader reader;
    private final RecruitWriter writer;
    private final Clock clock;

    public RecruitService(RecruitRepository recruits, RecruitReader reader, RecruitWriter writer, Clock clock) {
        this.recruits = recruits;
        this.reader = reader;
        this.writer = writer;
        this.clock = clock;
    }

    @Transactional
    public RecruitSummary create(long memberId, RecruitCreateRequest request) {
        Instant now = clock.instant();
        if (!request.meetAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.RECRUIT_TIME_PAST, "지난 시각은 고를 수 없습니다.");
        }
        if (request.meetAt().isAfter(now.plus(MAX_AHEAD))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                    List.of(new ErrorField("meetAt", "90일 이내로 골라 주세요.")));
        }
        if (request.scheduleId() != null && !reader.scheduleExists(request.scheduleId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "일정을 찾을 수 없습니다.");
        }
        Recruit recruit = recruits.saveAndFlush(Recruit.open(memberId, request.scheduleId(), request.kind(),
                request.title(), request.meetAt(), request.capacity(), request.memo(), now));
        writer.addMember(recruit.getId(), memberId);
        return reader.find(recruit.getId(), memberId, now).orElseThrow();
    }

    @Transactional(readOnly = true)
    public CursorPage<RecruitSummary> list(long viewerId, RecruitKind kind, String cursor, int size) {
        Instant now = clock.instant();
        List<RecruitSummary> rows = reader.list(viewerId, kind, now, after(cursor), size + 1);
        return CursorPage.of(rows, size, row -> "%d|%s|%d".formatted(row.state() == RecruitState.OPEN ? 1 : 0,
                LocalDateTime.ofInstant(row.meetAt(), ZoneOffset.UTC), row.id()));
    }

    @Transactional(readOnly = true)
    public RecruitDetail detail(long viewerId, long recruitId) {
        RecruitSummary summary = reader.find(recruitId, viewerId, clock.instant())
                .orElseThrow(RecruitService::notFound);
        return RecruitDetail.of(summary, reader.participants(recruitId));
    }

    /** 행을 잠그고 확인해 동시에 눌러도 정원을 넘지 않는다 */
    @Transactional
    public RecruitJoinResponse join(long memberId, long recruitId) {
        Recruit recruit = recruits.findForUpdate(recruitId).orElseThrow(RecruitService::notFound);
        Instant now = clock.instant();
        if (reader.joined(recruitId, memberId)) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED, "이미 참여한 모집입니다.");
        }
        if (recruit.isTimePassed(now)) {
            throw closed();
        }
        if (recruit.isFull()) {
            throw new BusinessException(ErrorCode.RECRUIT_FULL, "정원이 다 찼습니다.");
        }
        if (recruit.getState() == RecruitState.CLOSED) {
            throw closed();
        }
        writer.addMember(recruitId, memberId);
        recruit.join();
        return response(recruit, true, now);
    }

    @Transactional
    public RecruitJoinResponse leave(long memberId, long recruitId) {
        Recruit recruit = recruits.findForUpdate(recruitId).orElseThrow(RecruitService::notFound);
        Instant now = clock.instant();
        if (!reader.joined(recruitId, memberId)) {
            throw notFound();
        }
        if (recruit.getAuthorId() == memberId) {
            throw new BusinessException(ErrorCode.RECRUIT_OWNER_CANNOT_LEAVE, "개설자는 모집을 닫아 주세요.");
        }
        // 다 차서 닫힌 모집은 시간이 남았으면 취소할 수 있다. 개설자가 닫은 모집은 정원 전이라 여기서 걸린다
        boolean closedEarly = recruit.getState() == RecruitState.CLOSED && !recruit.isFull();
        if (recruit.isTimePassed(now) || closedEarly) {
            throw closed();
        }
        writer.removeMember(recruitId, memberId);
        recruit.leave();
        return response(recruit, false, now);
    }

    /** 이미 닫혔어도 그대로 성공 */
    @Transactional
    public RecruitClosureResponse close(long memberId, long recruitId) {
        Recruit recruit = recruits.findForUpdate(recruitId).orElseThrow(RecruitService::notFound);
        if (recruit.getAuthorId() != memberId) {
            throw new BusinessException(ErrorCode.NOT_AUTHOR);
        }
        recruit.close();
        return new RecruitClosureResponse(RecruitState.CLOSED);
    }

    private static RecruitJoinResponse response(Recruit recruit, boolean joined, Instant now) {
        return new RecruitJoinResponse(joined, recruit.getJoinedCount(), recruit.getCapacity(), recruit.stateAt(now));
    }

    private static RecruitReader.Cursor after(String cursor) {
        String raw = CursorPage.decode(cursor);
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split("\\|", -1);
        try {
            if (parts.length != 3 || !(parts[0].equals("0") || parts[0].equals("1"))) {
                throw CursorPage.invalidCursor();
            }
            return new RecruitReader.Cursor(parts[0].equals("1"), LocalDateTime.parse(parts[1]),
                    Long.parseLong(parts[2]));
        } catch (NumberFormatException | DateTimeParseException e) {
            throw CursorPage.invalidCursor();
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "모집을 찾을 수 없습니다.");
    }

    private static BusinessException closed() {
        return new BusinessException(ErrorCode.RECRUIT_CLOSED, "마감된 모집입니다.");
    }

}
