package com.gdghost.feed;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "피드")
@RestController
@RequestMapping("/api/v1/feed-posts/{postId}/like")
public class FeedLikeController {

    private final FeedLikeService likeService;

    public FeedLikeController(FeedLikeService likeService) {
        this.likeService = likeService;
    }

    @Operation(summary = "피드 좋아요", description = "멱등: 이미 눌렀으면 그대로 200. 없거나 삭제 · 숨김이면 404. 알림은 보내지 않는다.")
    @PutMapping
    public FeedLikeResponse like(@AuthenticationPrincipal AuthMember me, @PathVariable long postId) {
        return likeService.like(me.id(), postId);
    }

    @Operation(summary = "피드 좋아요 취소", description = "멱등: 누르지 않았어도 200. 지운 글 · 없는 글도 200, 없는 글은 likeCount 0.")
    @DeleteMapping
    public FeedLikeResponse unlike(@AuthenticationPrincipal AuthMember me, @PathVariable long postId) {
        return likeService.unlike(me.id(), postId);
    }

}
