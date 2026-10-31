package com.gdghost.share;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.page.CursorPage;

@Service
public class ShareSaveService {

    private final ShareRepository shares;
    private final ShareReader reader;
    private final ShareSaveWriter writer;
    private final ShareService shareService;
    private final Clock clock;

    public ShareSaveService(ShareRepository shares, ShareReader reader, ShareSaveWriter writer,
            ShareService shareService, Clock clock) {
        this.shares = shares;
        this.reader = reader;
        this.writer = writer;
        this.shareService = shareService;
        this.clock = clock;
    }

    /** 멱등. 본인 글도 저장할 수 있다. 글 행을 잠가 삭제 · 다른 저장과 한 줄로 세운다 */
    @Transactional
    public ShareSaveResponse save(long memberId, long shareId) {
        shares.findForUpdate(shareId).filter(share -> !share.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "공유 글을 찾을 수 없습니다."));
        writer.save(shareId, memberId, clock.instant());
        return new ShareSaveResponse(true, reader.saveCount(shareId));
    }

    /** 멱등. 지운 글도 저장 행을 뺄 수 있게 404를 내지 않는다 */
    @Transactional
    public ShareSaveResponse unsave(long memberId, long shareId) {
        if (shares.findForUpdate(shareId).isEmpty()) {
            return new ShareSaveResponse(false, 0);
        }
        writer.unsave(shareId, memberId);
        return new ShareSaveResponse(false, reader.saveCount(shareId));
    }

    @Transactional(readOnly = true)
    public CursorPage<SavedShareSummary> saved(long memberId, String cursor, int size) {
        List<ShareReader.SavedRow> rows = reader.saved(memberId, after(cursor), size + 1);
        CursorPage<ShareReader.SavedRow> page = CursorPage.of(rows, size, ShareSaveService::cursorOf);
        List<ShareSummary> summaries = shareService.summaries(
                page.items().stream().map(ShareReader.SavedRow::share).toList(), memberId);
        List<SavedShareSummary> items = new ArrayList<>(summaries.size());
        for (int i = 0; i < summaries.size(); i++) {
            items.add(new SavedShareSummary(summaries.get(i), page.items().get(i).savedAt()));
        }
        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }

    private static String cursorOf(ShareReader.SavedRow row) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, row.savedAt()) + ":" + row.share().id();
    }

    private static ShareReader.SavedAfter after(String cursor) {
        String raw = CursorPage.decode(cursor);
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(":", -1);
        if (parts.length != 2) {
            throw CursorPage.invalidCursor();
        }
        try {
            return new ShareReader.SavedAfter(Instant.EPOCH.plus(Long.parseLong(parts[0]), ChronoUnit.MICROS),
                    Long.parseLong(parts[1]));
        } catch (NumberFormatException | ArithmeticException | DateTimeException e) {
            throw CursorPage.invalidCursor();
        }
    }

}
