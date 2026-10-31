package com.gdghost.feed;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;

/** 좋아요는 알림을 보내지 않는다 */
@Service
public class FeedLikeService {

    private final FeedPostRepository posts;
    private final FeedLikeWriter writer;

    public FeedLikeService(FeedPostRepository posts, FeedLikeWriter writer) {
        this.posts = posts;
        this.writer = writer;
    }

    /** 멱등. 글 행을 잠가 삭제 · 다른 좋아요와 한 줄로 세운다 */
    @Transactional
    public FeedLikeResponse like(long memberId, long postId) {
        posts.findForUpdate(postId).filter(post -> !post.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "피드 글을 찾을 수 없습니다."));
        writer.like(postId, memberId);
        return new FeedLikeResponse(true, writer.count(postId));
    }

    /** 멱등. 공유 저장 취소와 같이 지운 글 · 없는 글도 200 */
    @Transactional
    public FeedLikeResponse unlike(long memberId, long postId) {
        if (posts.findForUpdate(postId).isEmpty()) {
            return new FeedLikeResponse(false, 0);
        }
        writer.unlike(postId, memberId);
        return new FeedLikeResponse(false, writer.count(postId));
    }

}
