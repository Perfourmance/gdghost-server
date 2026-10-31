package com.gdghost.auth;

import org.springframework.stereotype.Component;

import com.gdghost.member.Member;
import com.gdghost.member.ProfileCompletenessReader;
import com.gdghost.notice.NoticeService;
import com.gdghost.notification.NotificationReader;

/** 가입 완료 · 로그인 · 내 세션 확인의 Me 응답 */
@Component
public class MeReader {

    private final ProfileCompletenessReader completeness;
    private final NotificationReader notifications;
    private final NoticeService notices;

    public MeReader(ProfileCompletenessReader completeness, NotificationReader notifications,
            NoticeService notices) {
        this.completeness = completeness;
        this.notifications = notifications;
        this.notices = notices;
    }

    public MeResponse read(Member member) {
        return new MeResponse(member.getId(), member.getLoginId(), member.getNickname(), member.getGeneration(),
                member.getRole(), member.getStaffTitle(), notifications.unreadCount(member.getId()),
                (int) notices.unconfirmedMustReadCount(member.getId()), completeness.read(member));
    }

}
