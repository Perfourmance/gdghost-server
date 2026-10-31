package com.gdghost.share;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 신고 접수 · 처리에서 덧붙이기를 다루는 길. lock으로 행을 먼저 잠근 뒤 hide · restore를 부른다 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ShareNoteModeration {

    /** deletedBy는 null · AUTHOR · STAFF */
    public record Content(long authorId, String deletedBy) {
    }

    private final ShareNoteRepository notes;
    private final ShareRepository shares;

    public ShareNoteModeration(ShareNoteRepository notes, ShareRepository shares) {
        this.notes = notes;
        this.shares = shares;
    }

    /** 지운 덧붙이기, 글이 지워진 덧붙이기는 없는 것으로 본다 */
    public boolean isLive(long noteId) {
        return notes.findById(noteId)
                .filter(n -> !n.isDeleted())
                .flatMap(n -> shares.findById(n.getShareId()))
                .filter(s -> !s.isDeleted())
                .isPresent();
    }

    public Optional<Content> lock(long noteId) {
        return notes.findForUpdate(noteId).map(n -> new Content(n.getAuthorId(), n.getDeletedBy()));
    }

    public void hide(long noteId, Instant now) {
        notes.findById(noteId).orElseThrow().hideByStaff(now);
    }

    public void restore(long noteId) {
        notes.findById(noteId).orElseThrow().restore();
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<Long> shareId(long noteId) {
        return notes.findById(noteId).map(ShareNote::getShareId);
    }

}
