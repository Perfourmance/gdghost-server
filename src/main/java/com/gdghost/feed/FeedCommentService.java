package com.gdghost.feed;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.common.page.CursorPage;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;
import com.gdghost.notification.NotificationTarget;
import com.gdghost.notification.NotificationType;
import com.gdghost.notification.NotificationWriter;
import com.gdghost.question.AuthorRef;

@Service
public class FeedCommentService {

    private static final int MAX_LENGTH = 300;

    private final FeedCommentRepository comments;
    private final FeedPostRepository posts;
    private final FeedCommentReader reader;
    private final MemberRepository members;
    private final NotificationWriter notifications;
    private final Clock clock;

    public FeedCommentService(FeedCommentRepository comments, FeedPostRepository posts, FeedCommentReader reader,
            MemberRepository members, NotificationWriter notifications, Clock clock) {
        this.comments = comments;
        this.posts = posts;
        this.reader = reader;
        this.members = members;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CursorPage<FeedCommentItem> list(long viewerId, long postId, String cursor, int size) {
        livePost(postId);
        List<FeedCommentReader.Row> rows = reader.comments(postId, afterId(cursor), size + 1);
        CursorPage<FeedCommentReader.Row> page = CursorPage.of(rows, size, row -> String.valueOf(row.id()));
        if (page.items().isEmpty()) {
            return new CursorPage<>(List.of(), page.nextCursor(), page.hasMore());
        }
        Map<Long, List<FeedCommentReader.Row>> replies = reader
                .replies(page.items().stream().map(FeedCommentReader.Row::id).toList()).stream()
                .collect(Collectors.groupingBy(FeedCommentReader.Row::parentId));
        return new CursorPage<>(page.items().stream()
                .map(row -> item(row, viewerId, replies.getOrDefault(row.id(), List.of()).stream()
                        .map(reply -> item(reply, viewerId, null))
                        .toList()))
                .toList(), page.nextCursor(), page.hasMore());
    }

    /**
     * 답글에 답하면 그 답글의 부모 댓글에 붙이고 본문 앞에 @닉네임을 붙인다.
     * 알림은 댓글이면 글쓴이, 답글이면 답한 대상. 자기 자신에게는 없다.
     */
    @Transactional
    public FeedCommentItem create(long memberId, long postId, FeedCommentCreateRequest request) {
        FeedPost post = livePost(postId);
        FeedComment target = request.parentId() == null ? null : liveParent(postId, request.parentId());
        Long parentId = target == null ? null : target.isReply() ? target.getParentId() : target.getId();
        String body = target != null && target.isReply() ? mention(target.getAuthorId(), request.body())
                : request.body();

        FeedComment comment = comments.saveAndFlush(
                FeedComment.write(postId, parentId, memberId, body, clock.instant()));
        FeedCommentReader.Row row = reader.find(comment.getId());
        notifyAuthor(post, target, row);
        return item(row, memberId, target == null ? List.of() : null);
    }

    /** 본인 것만 (운영진도 남의 것은 신고 처리의 숨김으로만). 지웠거나 글이 지워졌으면 404 */
    @Transactional
    public void delete(long memberId, long commentId) {
        FeedComment comment = comments.findForUpdate(commentId)
                .filter(c -> !c.isDeleted() && posts.findById(c.getPostId()).filter(p -> !p.isDeleted()).isPresent())
                .orElseThrow(() -> notFound("댓글을 찾을 수 없습니다."));
        if (comment.getAuthorId() != memberId) {
            throw new BusinessException(ErrorCode.NOT_AUTHOR, "본인 댓글만 지울 수 있습니다.");
        }
        comment.deleteByAuthor(clock.instant());
    }

    private void notifyAuthor(FeedPost post, FeedComment target, FeedCommentReader.Row row) {
        long actorId = row.author().id();
        NotificationTarget link = new NotificationTarget("FEED_POST", post.getId(), "FEED_COMMENT", row.id());
        if (target == null) {
            if (post.getAuthorId() != actorId) {
                notifications.toMember(post.getAuthorId(), NotificationType.FEED_COMMENT,
                        row.author().nickname() + "님이 내 피드에 댓글을 달았어요", row.body(), link);
            }
        } else if (target.getAuthorId() != actorId) {
            notifications.toMember(target.getAuthorId(), NotificationType.FEED_REPLY,
                    row.author().nickname() + "님이 내 댓글에 답글을 달았어요", row.body(), link);
        }
    }

    /** 탈퇴한 사람은 닉네임이 없어 붙이지 않는다. @닉네임까지 합쳐 300자를 넘으면 400 */
    private String mention(long targetAuthorId, String body) {
        String nickname = members.findById(targetAuthorId).filter(Member::isActive).map(Member::getNickname)
                .orElse(null);
        if (nickname == null) {
            return body;
        }
        String mentioned = "@" + nickname + " " + body;
        if (mentioned.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                    List.of(new ErrorField("body", "@닉네임을 포함해 300자 이하로 입력해 주세요.")));
        }
        return mentioned;
    }

    private FeedPost livePost(long postId) {
        return posts.findById(postId).filter(post -> !post.isDeleted())
                .orElseThrow(() -> notFound("피드 글을 찾을 수 없습니다."));
    }

    private FeedComment liveParent(long postId, long parentId) {
        return comments.findById(parentId).filter(c -> !c.isDeleted() && c.getPostId() == postId)
                .orElseThrow(() -> notFound("댓글을 찾을 수 없습니다."));
    }

    private static FeedCommentItem item(FeedCommentReader.Row row, long viewerId, List<FeedCommentItem> replies) {
        if (row.deletedBy() != null) {
            return new FeedCommentItem(row.id(), row.parentId(), row.depth(), null, null, row.deletedBy(), false,
                    row.createdAt(), replies);
        }
        return new FeedCommentItem(row.id(), row.parentId(), row.depth(),
                AuthorRef.of(row.author(), false, null, viewerId), row.body(), null,
                row.author().id() == viewerId, row.createdAt(), replies);
    }

    private static Long afterId(String cursor) {
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

    private static BusinessException notFound(String detail) {
        return new BusinessException(ErrorCode.NOT_FOUND, detail);
    }

}
