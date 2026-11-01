package com.gdghost.admin;

import org.springframework.stereotype.Component;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.discord.DiscordMessage;
import com.gdghost.common.discord.DiscordNotifier;
import com.gdghost.common.event.DomainEventListener;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;

/** 라우팅 CSV를 받을 때마다 개발자 서버에 기록. 원본은 ops_audit_log */
@Component
public class RoutingExportDiscordListener {

    private final MemberRepository members;
    private final DiscordNotifier discord;

    public RoutingExportDiscordListener(MemberRepository members, DiscordNotifier discord) {
        this.members = members;
        this.discord = discord;
    }

    @DomainEventListener
    public void on(RoutingExportEvent event) {
        String actor = members.findById(event.actorId()).map(Member::getNickname).map(DiscordMessage::escape)
                .orElse("탈퇴한 부원");
        discord.send(DiscordChannel.DEV, new DiscordMessage("라우팅 CSV 내보내기", null,
                actor + " · " + event.from() + " ~ " + event.to() + " · " + event.rows() + "행"));
    }

}
