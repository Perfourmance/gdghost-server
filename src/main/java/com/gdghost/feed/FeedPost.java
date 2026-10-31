package com.gdghost.feed;

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

/** 수정 없음. like_count는 매핑하지 않는다 — 좋아요 수 증감을 덮어쓰지 않게 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long authorId;

    @Enumerated(EnumType.STRING)
    private FeedCategory category;

    private String body;
    private Instant createdAt;
    private Instant deletedAt;
    private String deletedBy;

    static FeedPost write(long authorId, FeedCategory category, String body, Instant now) {
        FeedPost post = new FeedPost();
        post.authorId = authorId;
        post.category = category;
        post.body = body;
        post.createdAt = now;
        return post;
    }

    boolean isDeleted() {
        return deletedAt != null;
    }

    void delete(Instant now) {
        this.deletedAt = now;
        this.deletedBy = "AUTHOR";
    }

    void hideByStaff(Instant now) {
        this.deletedAt = now;
        this.deletedBy = "STAFF";
    }

    void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
    }

}
