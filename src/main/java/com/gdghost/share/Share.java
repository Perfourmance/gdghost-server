package com.gdghost.share;

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

/** save_count는 매핑하지 않는다 — 저장 수 증감을 글 수정이 덮어쓰지 않게 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Share {

    private static final String AUTHOR = "AUTHOR";
    private static final String STAFF = "STAFF";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long authorId;

    @Enumerated(EnumType.STRING)
    private ShareCategory category;

    private String title;
    private String body;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private String deletedBy;

    static Share write(long authorId, ShareCategory category, String title, String body, Instant now) {
        Share share = new Share();
        share.authorId = authorId;
        share.category = category;
        share.title = title;
        share.body = body;
        share.createdAt = now;
        return share;
    }

    /** null은 그대로. @return 실제로 바뀌었으면 true */
    boolean edit(ShareCategory category, String title, String body) {
        ShareCategory newCategory = category != null ? category : this.category;
        String newTitle = title != null ? title : this.title;
        String newBody = body != null ? body : this.body;
        if (newCategory == this.category && newTitle.equals(this.title) && newBody.equals(this.body)) {
            return false;
        }
        this.category = newCategory;
        this.title = newTitle;
        this.body = newBody;
        return true;
    }

    void touch(Instant now) {
        this.updatedAt = now;
    }

    boolean isDeleted() {
        return deletedAt != null;
    }

    void delete(Instant now) {
        this.deletedAt = now;
        this.deletedBy = AUTHOR;
    }

    void hideByStaff(Instant now) {
        this.deletedAt = now;
        this.deletedBy = STAFF;
    }

    void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
    }

}
