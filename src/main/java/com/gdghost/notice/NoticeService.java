package com.gdghost.notice;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.page.CursorPage;
import com.gdghost.common.security.AuthMember;
import com.gdghost.common.security.Role;

/** 공지 목록 · 상세 · 확인했어요, 홈 공지 묶음 */
@Service
public class NoticeService {

    public static final int HOME_PINNED = 5;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NoticeListReader listReader;
    private final NoticeDetailReader detailReader;
    private final NoticeStatsReader statsReader;
    private final NoticeReadWriter readWriter;
    private final Clock clock;

    public NoticeService(NoticeListReader listReader, NoticeDetailReader detailReader, NoticeStatsReader statsReader,
            NoticeReadWriter readWriter, Clock clock) {
        this.listReader = listReader;
        this.detailReader = detailReader;
        this.statsReader = statsReader;
        this.readWriter = readWriter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CursorPage<NoticeSummary> list(long memberId, String cursor, int size) {
        List<NoticeListReader.Row> rows = listReader.list(memberId, false, after(cursor), size + 1);
        CursorPage<NoticeListReader.Row> page = CursorPage.of(rows, size,
                row -> (row.pinned() ? 1 : 0) + ":" + row.id());
        Map<Long, Long> commentCounts = listReader.commentCounts(page.items().stream()
                .map(NoticeListReader.Row::id).toList());
        return new CursorPage<>(page.items().stream()
                .map(row -> new NoticeSummary(row.id(), row.title(), row.pinned(), row.mustRead(), row.dueDate(),
                        commentCounts.getOrDefault(row.id(), 0L), row.author(), row.read(), row.confirmed(),
                        row.createdAt()))
                .toList(), page.nextCursor(), page.hasMore());
    }

    /** 열면 읽음으로 남긴다 (처음 시각 유지) */
    @Transactional
    public NoticeDetail detail(AuthMember me, long noticeId) {
        NoticeDetailReader.Row row = find(noticeId);
        readWriter.read(noticeId, me.id(), clock.instant());
        return detail(row, me);
    }

    /** 다시 눌러도 처음 시각 그대로. 취소는 없다 */
    @Transactional
    public NoticeConfirmation confirm(long memberId, long noticeId) {
        find(noticeId);
        readWriter.confirm(noticeId, memberId, clock.instant());
        return new NoticeConfirmation(true, detailReader.confirmedAt(noticeId, memberId));
    }

    @Transactional(readOnly = true)
    public List<PinnedNotice> pinned(long memberId) {
        return listReader.list(memberId, true, null, HOME_PINNED).stream()
                .map(row -> new PinnedNotice(row.id(), row.title(), row.mustRead(), row.dueDate(), row.read()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GuestPinnedNotice> guestPinned() {
        return listReader.guestPinned(HOME_PINNED);
    }

    @Transactional(readOnly = true)
    public long unconfirmedMustReadCount(long memberId) {
        return listReader.unconfirmedMustRead(memberId, LocalDate.ofInstant(clock.instant(), KST));
    }

    /** 운영 작성 · 수정 응답에도 쓴다. 읽음은 남기지 않는다 */
    NoticeDetail detail(long noticeId, AuthMember viewer) {
        return detail(find(noticeId), viewer);
    }

    // TODO 댓글(#44)
    private NoticeDetail detail(NoticeDetailReader.Row row, AuthMember viewer) {
        return new NoticeDetail(row.id(), row.title(), row.body(), row.pinned(), row.mustRead(), row.dueDate(),
                row.author(), row.createdAt(), detailReader.confirmedAt(row.id(), viewer.id()) != null, List.of(),
                viewer.role() == Role.MEMBER ? null : statsReader.stats(row.id()));
    }

    private NoticeDetailReader.Row find(long noticeId) {
        return detailReader.find(noticeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "공지를 찾을 수 없습니다."));
    }

    private static NoticeListReader.Cursor after(String cursor) {
        String raw = CursorPage.decode(cursor);
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(":", -1);
        try {
            if (parts.length != 2 || !(parts[0].equals("0") || parts[0].equals("1"))) {
                throw CursorPage.invalidCursor();
            }
            return new NoticeListReader.Cursor(parts[0].equals("1"), Long.parseLong(parts[1]));
        } catch (NumberFormatException e) {
            throw CursorPage.invalidCursor();
        }
    }

}
