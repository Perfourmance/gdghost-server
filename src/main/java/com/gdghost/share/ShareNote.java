package com.gdghost.share;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 한 줄 덧붙이기. 익명 · 수정 없음 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShareNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long shareId;
    private long authorId;
    private String body;
    private Instant createdAt;
    private Instant deletedAt;
    private String deletedBy;

    static ShareNote write(long shareId, long authorId, String body, Instant now) {
        ShareNote note = new ShareNote();
        note.shareId = shareId;
        note.authorId = authorId;
        note.body = body;
        note.createdAt = now;
        return note;
    }

    void deleteByAuthor(Instant now) {
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

    boolean isDeleted() {
        return deletedAt != null;
    }

}
