package com.gdghost.notification;

/**
 * 커밋 후 웹 푸시 요청. 받는 사람은 한 명(memberId) 또는 한 명을 뺀 전체(excludedId).
 * 앱 내 알림 내용과 같아서 다시 읽지 않고 담아 보낸다.
 */
public record NotificationPushEvent(Long memberId, Long excludedId, NotificationType type, String title, String body,
        NotificationTarget target) {
}
