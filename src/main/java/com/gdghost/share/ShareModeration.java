package com.gdghost.share;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 신고 접수 · 처리에서 공유 글을 다루는 길. lock으로 글 행을 먼저 잠근 뒤 hide · restore를 부른다 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ShareModeration {

    /** deletedBy는 null · AUTHOR · STAFF */
    public record Content(long authorId, String deletedBy) {
    }

    private final ShareRepository shares;

    public ShareModeration(ShareRepository shares) {
        this.shares = shares;
    }

    public boolean isLive(long shareId) {
        return shares.findById(shareId).filter(s -> !s.isDeleted()).isPresent();
    }

    public Optional<Content> lock(long shareId) {
        return shares.findForUpdate(shareId).map(s -> new Content(s.getAuthorId(), s.getDeletedBy()));
    }

    /** 사진은 되돌릴 수 있게 그대로 둔다 — 숨김 글은 주소가 발급되지 않는다 */
    public void hide(long shareId, Instant now) {
        shares.findById(shareId).orElseThrow().hideByStaff(now);
    }

    public void restore(long shareId) {
        shares.findById(shareId).orElseThrow().restore();
    }

}
