package com.gdghost.notice;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long authorId;
    private String title;
    private String body;

    @Column(name = "is_pinned")
    private boolean pinned;

    @Column(name = "is_must_read")
    private boolean mustRead;

    private LocalDate dueDate;
    private Instant remindedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    public static Notice write(long authorId, String title, String body, boolean pinned, boolean mustRead,
            LocalDate dueDate, Instant now) {
        Notice notice = new Notice();
        notice.authorId = authorId;
        notice.title = title;
        notice.body = body;
        notice.pinned = pinned;
        notice.mustRead = mustRead;
        notice.dueDate = dueDate;
        notice.createdAt = now;
        return notice;
    }

    /** null은 그대로. 실제로 바뀐 게 있을 때만 수정 시각을 찍는다 */
    public void edit(String title, String body, Boolean pinned, Boolean mustRead, LocalDate dueDate,
            boolean clearDueDate, Instant now) {
        String newTitle = title != null ? title : this.title;
        String newBody = body != null ? body : this.body;
        boolean newPinned = pinned != null ? pinned : this.pinned;
        boolean newMustRead = mustRead != null ? mustRead : this.mustRead;
        LocalDate newDueDate = clearDueDate ? null : dueDate != null ? dueDate : this.dueDate;
        if (newTitle.equals(this.title) && newBody.equals(this.body) && newPinned == this.pinned
                && newMustRead == this.mustRead && Objects.equals(newDueDate, this.dueDate)) {
            return;
        }
        this.title = newTitle;
        this.body = newBody;
        this.pinned = newPinned;
        this.mustRead = newMustRead;
        this.dueDate = newDueDate;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

}
