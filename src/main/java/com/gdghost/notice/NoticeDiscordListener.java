package com.gdghost.notice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.discord.DiscordMessage;
import com.gdghost.common.discord.DiscordNotifier;
import com.gdghost.common.event.DomainEventListener;

/** 새 공지 → 부원 채널. 제목 · 링크만, 필독이면 [필독]. 수정 · 삭제는 다시 보내지 않는다 */
@Component
public class NoticeDiscordListener {

    private final NoticeRepository notices;
    private final DiscordNotifier discord;
    private final String webUrl;

    public NoticeDiscordListener(NoticeRepository notices, DiscordNotifier discord,
            @Value("${web.url}") String webUrl) {
        this.notices = notices;
        this.discord = discord;
        this.webUrl = webUrl;
    }

    @DomainEventListener
    public void on(NoticeCreatedEvent event) {
        notices.findById(event.noticeId())
                .filter(notice -> !notice.isDeleted())
                .ifPresent(notice -> discord.send(DiscordChannel.MEMBER, new DiscordMessage(
                        notice.isMustRead() ? "[필독] 새 공지" : "새 공지",
                        webUrl + "/notices/" + notice.getId(),
                        DiscordMessage.escape(notice.getTitle()))));
    }

}
