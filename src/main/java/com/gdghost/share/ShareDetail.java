package com.gdghost.share;

import java.time.Instant;
import java.util.List;

import com.gdghost.question.AuthorRef;
import com.gdghost.upload.Attachment;

public record ShareDetail(long id, ShareCategory category, String title, String body, List<String> tags,
        List<Attachment> photos, AuthorRef author, long saveCount, boolean saved, boolean mine, Instant createdAt,
        Instant updatedAt, List<Note> notes) {

    public record Note(long id, NoteAuthor author, String body, boolean mine, Instant createdAt) {
    }

    /** 탈퇴자는 id null · '탈퇴한 부원' */
    public record NoteAuthor(Long id, String nickname) {
    }

}
