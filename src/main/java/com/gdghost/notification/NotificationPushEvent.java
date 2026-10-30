package com.gdghost.notification;

import java.util.List;

/**
 * 웹 푸시 요청. 받는 사람은 정해진 사람들(memberIds) 또는 한 명을 뺀 전체(excludedId).
 * 앱 내 알림 내용과 같아서 다시 읽지 않고 담아 보낸다.
 */
public record NotificationPushEvent(List<Long> memberIds, Long excludedId, NotificationType type, String title,
        String body, NotificationTarget target) {

    public static NotificationPushEvent to(List<Long> memberIds, NotificationType type, String title, String body,
            NotificationTarget target) {
        return new NotificationPushEvent(memberIds, null, type, title, body, target);
    }

    public static NotificationPushEvent toAllExcept(long excludedId, NotificationType type, String title, String body,
            NotificationTarget target) {
        return new NotificationPushEvent(null, excludedId, type, title, body, target);
    }

}
