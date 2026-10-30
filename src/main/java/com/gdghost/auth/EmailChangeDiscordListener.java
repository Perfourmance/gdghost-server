package com.gdghost.auth;

import org.springframework.stereotype.Component;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.discord.DiscordMessage;
import com.gdghost.common.discord.DiscordNotifier;
import com.gdghost.common.event.DomainEventListener;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;

/** 어드민 이메일 변경 → 운영진 · 개발자 서버에 기록. 이메일 주소는 보내지 않는다 */
@Component
public class EmailChangeDiscordListener {

    private final MemberRepository members;
    private final DiscordNotifier discord;

    public EmailChangeDiscordListener(MemberRepository members, DiscordNotifier discord) {
        this.members = members;
        this.discord = discord;
    }

    @DomainEventListener
    public void on(EmailChangeRequestedEvent event) {
        send(new DiscordMessage("이메일 변경 요청 (어드민)", null,
                name(event.requesterId()) + " → " + name(event.memberId()) + "\n부원이 새 메일의 링크를 열면 바뀐다"));
    }

    @DomainEventListener
    public void on(EmailChangedEvent event) {
        send(new DiscordMessage("이메일 변경 완료 (어드민 요청)", null,
                name(event.memberId()) + (event.requesterId() == null ? "" : " · 요청 " + name(event.requesterId()))
                        + "\n모든 기기에서 로그아웃됨"));
    }

    private void send(DiscordMessage message) {
        discord.send(DiscordChannel.STAFF, message);
        discord.send(DiscordChannel.DEV, message);
    }

    private String name(Long memberId) {
        return members.findById(memberId).map(Member::getNickname).map(DiscordMessage::escape).orElse("탈퇴한 부원");
    }

}
