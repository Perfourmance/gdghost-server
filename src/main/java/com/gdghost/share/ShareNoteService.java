package com.gdghost.share;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.notification.NotificationTarget;
import com.gdghost.notification.NotificationType;
import com.gdghost.notification.NotificationWriter;

@Service
public class ShareNoteService {

    private final ShareNoteRepository notes;
    private final ShareRepository shares;
    private final ShareReader reader;
    private final NotificationWriter notifications;
    private final Clock clock;

    public ShareNoteService(ShareNoteRepository notes, ShareRepository shares, ShareReader reader,
            NotificationWriter notifications, Clock clock) {
        this.notes = notes;
        this.shares = shares;
        this.reader = reader;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** 글쓴이에게 앱 내 알림 + 웹 푸시. 본인이 단 건 알리지 않는다 */
    @Transactional
    public ShareDetail.Note create(long memberId, long shareId, ShareNoteCreateRequest request) {
        Share share = shares.findById(shareId).filter(s -> !s.isDeleted())
                .orElseThrow(() -> notFound("공유 글을 찾을 수 없습니다."));
        ShareNote note = notes.saveAndFlush(ShareNote.write(shareId, memberId, request.body(), clock.instant()));
        ShareReader.NoteRow row = reader.note(note.getId());
        if (share.getAuthorId() != memberId) {
            notifications.toMember(share.getAuthorId(), NotificationType.SHARE_NOTE,
                    row.nickname() + "님이 내 공유 글에 덧붙였어요", share.getTitle(),
                    NotificationTarget.shareNote(shareId, note.getId()));
        }
        return ShareService.note(row, memberId);
    }

    /** 본인 것만 (운영진도 남의 것은 신고 처리의 숨김으로만). 지웠거나 글이 지워졌으면 404 */
    @Transactional
    public void delete(long memberId, long noteId) {
        ShareNote note = notes.findForUpdate(noteId)
                .filter(n -> !n.isDeleted() && shares.findById(n.getShareId()).filter(s -> !s.isDeleted()).isPresent())
                .orElseThrow(() -> notFound("덧붙이기를 찾을 수 없습니다."));
        if (note.getAuthorId() != memberId) {
            throw new BusinessException(ErrorCode.NOT_AUTHOR, "본인 덧붙이기만 지울 수 있습니다.");
        }
        note.deleteByAuthor(clock.instant());
    }

    private static BusinessException notFound(String detail) {
        return new BusinessException(ErrorCode.NOT_FOUND, detail);
    }

}
