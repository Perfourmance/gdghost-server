package com.gdghost.notification;

/**
 * 눌렀을 때 갈 대상. 화면 경로는 프론트가 만든다 (IA 5장).
 *
 * @param subType 대상 안의 위치(ANSWER 등), 없으면 null
 */
public record NotificationTarget(String type, long id, String subType, Long subId) {

    public static NotificationTarget question(long questionId) {
        return new NotificationTarget("QUESTION", questionId, null, null);
    }

    public static NotificationTarget answer(long questionId, long answerId) {
        return new NotificationTarget("QUESTION", questionId, "ANSWER", answerId);
    }

    public static NotificationTarget notice(long noticeId) {
        return new NotificationTarget("NOTICE", noticeId, null, null);
    }

    public static NotificationTarget schedule(long scheduleId) {
        return new NotificationTarget("SCHEDULE", scheduleId, null, null);
    }

    /** 설정 화면은 id가 없지만 target_id가 필수라 받는 사람 id를 넣는다 */
    public static NotificationTarget settings(long memberId) {
        return new NotificationTarget("SETTINGS", memberId, null, null);
    }

}
