package com.gdghost.feed;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.page.CursorPage;
import com.gdghost.question.AuthorRef;
import com.gdghost.upload.Attachment;
import com.gdghost.upload.AttachmentService;
import com.gdghost.upload.UploadPurpose;

@Service
public class FeedService {

    private final FeedPostRepository posts;
    private final FeedReader reader;
    private final AttachmentService attachments;
    private final Clock clock;

    public FeedService(FeedPostRepository posts, FeedReader reader, AttachmentService attachments, Clock clock) {
        this.posts = posts;
        this.reader = reader;
        this.attachments = attachments;
        this.clock = clock;
    }

    /** 사진 주소는 이 페이지 글에만 발급한다 */
    @Transactional(readOnly = true)
    public CursorPage<FeedPostItem> list(long viewerId, FeedCategory category, String cursor, int size) {
        List<FeedReader.Row> rows = reader.list(category, beforeId(cursor), size + 1);
        CursorPage<FeedReader.Row> page = CursorPage.of(rows, size, row -> String.valueOf(row.id()));
        return new CursorPage<>(items(page.items(), viewerId), page.nextCursor(), page.hasMore());
    }

    @Transactional(readOnly = true)
    public FeedPostItem detail(long viewerId, long postId) {
        FeedReader.Row row = reader.find(postId).orElseThrow(FeedService::notFound);
        return items(List.of(row), viewerId).getFirst();
    }

    /** 디스코드 · 푸시 없음 */
    @Transactional
    public FeedPostItem create(long authorId, FeedPostCreateRequest request) {
        FeedPost post = posts.saveAndFlush(FeedPost.write(authorId, request.category(), request.body(),
                clock.instant()));
        if (request.photoKeys() != null) {
            attachments.attach(UploadPurpose.FEED, post.getId(), authorId, request.photoKeys());
        }
        return detail(authorId, post.getId());
    }

    /** 없거나 지웠으면 404가 403보다 먼저. 사진은 연결만 끊고 정리 배치가 R2에서 지운다 */
    @Transactional
    public void delete(long memberId, long postId) {
        FeedPost post = posts.findForUpdate(postId).filter(p -> !p.isDeleted()).orElseThrow(FeedService::notFound);
        if (post.getAuthorId() != memberId) {
            throw new BusinessException(ErrorCode.NOT_AUTHOR, "본인 글만 지울 수 있습니다.");
        }
        post.delete(clock.instant());
        attachments.detach(UploadPurpose.FEED, postId);
    }

    private List<FeedPostItem> items(List<FeedReader.Row> rows, long viewerId) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(FeedReader.Row::id).toList();
        Map<Long, List<Attachment>> photos = attachments.photos(UploadPurpose.FEED, ids);
        Set<Long> liked = reader.likedAmong(ids, viewerId);
        Map<Long, Long> comments = reader.commentCounts(ids);
        return rows.stream()
                .map(row -> new FeedPostItem(row.id(), row.category(), AuthorRef.of(row.author(), false, null, viewerId),
                        row.body(), photos.getOrDefault(row.id(), List.of()), row.likeCount(),
                        liked.contains(row.id()), comments.getOrDefault(row.id(), 0L),
                        row.author().id() == viewerId, row.createdAt()))
                .toList();
    }

    private static Long beforeId(String cursor) {
        String raw = CursorPage.decode(cursor);
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw CursorPage.invalidCursor();
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "피드 글을 찾을 수 없습니다.");
    }

}
