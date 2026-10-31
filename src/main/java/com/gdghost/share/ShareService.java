package com.gdghost.share;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.common.page.CursorPage;
import com.gdghost.common.security.AuthMember;
import com.gdghost.question.AuthorRef;
import com.gdghost.question.GuestAuthor;
import com.gdghost.upload.AttachmentService;
import com.gdghost.upload.UploadPurpose;

@Service
public class ShareService {

    private static final Duration POPULAR_WINDOW = Duration.ofDays(7);
    private static final int QUERY_MIN = 2;
    private static final int QUERY_MAX = 30;

    private final ShareRepository shares;
    private final ShareReader reader;
    private final ShareTagWriter tagWriter;
    private final AttachmentService attachments;
    private final Clock clock;

    public ShareService(ShareRepository shares, ShareReader reader, ShareTagWriter tagWriter,
            AttachmentService attachments, Clock clock) {
        this.shares = shares;
        this.reader = reader;
        this.tagWriter = tagWriter;
        this.attachments = attachments;
        this.clock = clock;
    }

    /** 비회원(me == null)은 게스트 모양, 검색은 401 */
    @Transactional(readOnly = true)
    public CursorPage<?> list(AuthMember me, ShareCategory category, ShareSort sort, String q, String cursor,
            int size) {
        if (me == null && q != null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "검색은 로그인 후 쓸 수 있습니다.");
        }
        List<ShareReader.Row> rows = reader.list(category, sort, query(q), after(cursor, sort), size + 1);
        CursorPage<ShareReader.Row> page = CursorPage.of(rows, size, row -> cursorOf(row, sort));
        if (page.items().isEmpty()) {
            return page;
        }
        if (me == null) {
            return new CursorPage<>(page.items().stream()
                    .map(row -> new GuestShareSummary(row.id(), row.category(), row.title(),
                            GuestAuthor.of(row.author(), false), row.createdAt()))
                    .toList(), page.nextCursor(), page.hasMore());
        }
        return new CursorPage<>(summaries(page.items(), me.id()), page.nextCursor(), page.hasMore());
    }

    /** 최근 7일 저장이 많은 글. 비회원은 id · 분류 · 제목만 */
    @Transactional(readOnly = true)
    public List<?> popular(AuthMember me, int size) {
        List<ShareReader.Row> rows = reader.popular(clock.instant().minus(POPULAR_WINDOW), size);
        if (me == null) {
            return rows.stream().map(row -> new GuestPopularShare(row.id(), row.category(), row.title())).toList();
        }
        return summaries(rows, me.id());
    }

    /** 페이지 글 id로 태그 · 덧붙이기 수 · 저장 여부 · 사진 유무를 한 번씩 읽는다 */
    List<ShareSummary> summaries(List<ShareReader.Row> rows, long viewerId) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(ShareReader.Row::id).toList();
        Map<Long, List<String>> tags = reader.tags(ids);
        Map<Long, Long> noteCounts = reader.noteCounts(ids);
        Set<Long> saved = reader.savedAmong(ids, viewerId);
        Set<Long> withPhoto = reader.withPhotoAmong(ids);
        return rows.stream()
                .map(row -> new ShareSummary(row.id(), row.category(), row.title(),
                        tags.getOrDefault(row.id(), List.of()), AuthorRef.of(row.author(), false, null, viewerId),
                        row.saveCount(), noteCounts.getOrDefault(row.id(), 0L), saved.contains(row.id()),
                        withPhoto.contains(row.id()), row.createdAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ShareDetail detail(long viewerId, long shareId) {
        ShareReader.DetailRow share = reader.find(shareId, viewerId).orElseThrow(ShareService::notFound);
        return new ShareDetail(share.id(), share.category(), share.title(), share.body(),
                reader.tags(List.of(shareId)).getOrDefault(shareId, List.of()),
                attachments.photos(UploadPurpose.SHARE, List.of(shareId)).getOrDefault(shareId, List.of()),
                AuthorRef.of(share.author(), false, null, viewerId), share.saveCount(), share.saved(),
                share.author().id() == viewerId, share.createdAt(), share.updatedAt(),
                reader.notes(shareId).stream().map(note -> note(note, viewerId)).toList());
    }

    @Transactional
    public ShareDetail create(long authorId, ShareCreateRequest request) {
        Share share = shares.saveAndFlush(Share.write(authorId, request.category(), request.title(), request.body(),
                clock.instant()));
        if (request.tags() != null) {
            tagWriter.replace(share.getId(), request.tags());
        }
        if (request.photoKeys() != null) {
            attachments.attach(UploadPurpose.SHARE, share.getId(), authorId, request.photoKeys());
        }
        return detail(authorId, share.getId());
    }

    /** 내용 · 태그 · 사진이 실제로 바뀔 때만 수정 시각을 찍는다 */
    @Transactional
    public ShareDetail update(long memberId, long shareId, ShareUpdateRequest request) {
        Share share = lockOwn(memberId, shareId);
        boolean changed = share.edit(request.category(), request.title(), request.body());
        if (request.tags() != null && !sameTags(shareId, request.tags())) {
            tagWriter.replace(shareId, request.tags());
            changed = true;
        }
        if (request.photoKeys() != null
                && !attachments.objectKeys(UploadPurpose.SHARE, shareId).equals(request.photoKeys())) {
            attachments.replace(UploadPurpose.SHARE, shareId, memberId, request.photoKeys());
            changed = true;
        }
        if (changed) {
            share.touch(clock.instant());
        }
        shares.flush();
        return detail(memberId, shareId);
    }

    /** 사진은 연결만 끊고 정리 배치가 R2에서 지운다 */
    @Transactional
    public void delete(long memberId, long shareId) {
        Share share = lockOwn(memberId, shareId);
        share.delete(clock.instant());
        attachments.detach(UploadPurpose.SHARE, shareId);
    }

    /** 없거나 지웠으면 404가 403보다 먼저 */
    private Share lockOwn(long memberId, long shareId) {
        Share share = shares.findForUpdate(shareId).filter(s -> !s.isDeleted()).orElseThrow(ShareService::notFound);
        if (share.getAuthorId() != memberId) {
            throw new BusinessException(ErrorCode.NOT_AUTHOR, "본인 글만 바꿀 수 있습니다.");
        }
        return share;
    }

    private boolean sameTags(long shareId, List<String> tags) {
        Set<String> current = new HashSet<>(reader.tags(List.of(shareId)).getOrDefault(shareId, List.of()));
        return current.equals(new HashSet<>(tags));
    }

    private static ShareDetail.Note note(ShareReader.NoteRow note, long viewerId) {
        ShareDetail.NoteAuthor author = note.withdrawn()
                ? new ShareDetail.NoteAuthor(null, AuthorRef.WITHDRAWN_NICKNAME)
                : new ShareDetail.NoteAuthor(note.authorId(), note.nickname());
        return new ShareDetail.Note(note.id(), author, note.body(), note.authorId() == viewerId, note.createdAt());
    }

    private static String query(String q) {
        if (q == null) {
            return null;
        }
        String query = q.strip();
        int length = query.codePointCount(0, query.length());
        if (length < QUERY_MIN || length > QUERY_MAX) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                    List.of(new ErrorField("q", "검색어는 2–30자로 입력해 주세요.")));
        }
        return query;
    }

    private static String cursorOf(ShareReader.Row row, ShareSort sort) {
        return sort == ShareSort.SAVED ? row.saveCount() + ":" + row.id() : String.valueOf(row.id());
    }

    /** 다른 정렬의 커서는 모양이 달라 400 */
    private static ShareReader.After after(String cursor, ShareSort sort) {
        String raw = CursorPage.decode(cursor);
        if (raw == null) {
            return null;
        }
        try {
            if (sort == ShareSort.LATEST) {
                return new ShareReader.After(null, Long.parseLong(raw));
            }
            String[] parts = raw.split(":", -1);
            if (parts.length != 2) {
                throw CursorPage.invalidCursor();
            }
            return new ShareReader.After(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        } catch (NumberFormatException e) {
            throw CursorPage.invalidCursor();
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "공유 글을 찾을 수 없습니다.");
    }

}
