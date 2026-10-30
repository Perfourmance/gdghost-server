package com.gdghost.notification;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.page.CursorPage;

@Service
public class NotificationService {

    private final NotificationReader reader;
    private final NotificationWriter writer;

    public NotificationService(NotificationReader reader, NotificationWriter writer) {
        this.reader = reader;
        this.writer = writer;
    }

    @Transactional(readOnly = true)
    public CursorPage<NotificationResponse> list(long memberId, String cursor, int size) {
        List<NotificationResponse> rows = reader.list(memberId, afterId(cursor), size + 1);
        return CursorPage.of(rows, size, row -> String.valueOf(row.id()));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(long memberId) {
        return new UnreadCountResponse(reader.unreadCount(memberId));
    }

    @Transactional
    public NotificationReadResponse read(long memberId, NotificationReadRequest request) {
        if (request.readingAll()) {
            writer.markAllRead(memberId);
        } else {
            writer.markRead(memberId, request.ids());
        }
        return new NotificationReadResponse(reader.unreadCount(memberId));
    }

    private static Long afterId(String cursor) {
        String raw = CursorPage.decode(cursor);
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            throw CursorPage.invalidCursor();
        }
    }

}
