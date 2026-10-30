package com.gdghost.ops;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.discord.DiscordMessage;
import com.gdghost.common.discord.DiscordNotifier;
import com.gdghost.common.event.DomainEventListener;
import com.gdghost.common.security.Role;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;

/** 정지 · 해제 · 운영진 지정 → 운영진 서버에 기록 (누가 누구를). 원본은 ops_audit_log */
@Component
public class OpsDiscordListener {

    private final MemberRepository members;
    private final DiscordNotifier discord;

    public OpsDiscordListener(MemberRepository members, DiscordNotifier discord) {
        this.members = members;
        this.discord = discord;
    }

    @DomainEventListener
    public void on(MemberSuspendedEvent event) {
        Optional<Member> member = members.findById(event.memberId());
        String reason = member.map(Member::getSuspendedReason).orElse(null);
        discord.send(DiscordChannel.STAFF, new DiscordMessage("계정 정지", null,
                who(event.staffId()) + " → " + name(member)
                        + (reason == null ? "" : "\n사유 " + DiscordMessage.escape(reason))));
    }

    @DomainEventListener
    public void on(MemberUnsuspendedEvent event) {
        discord.send(DiscordChannel.STAFF, new DiscordMessage("계정 정지 해제", null,
                who(event.staffId()) + " → " + who(event.memberId())));
    }

    /** 개발자가 지정했으면(첫 운영진) 개발자 서버에도 */
    @DomainEventListener
    public void on(StaffRoleChangedEvent event) {
        Optional<Member> actor = members.findById(event.actorId());
        Optional<Member> member = members.findById(event.memberId());
        String title = member.map(Member::getStaffTitle).orElse(null);
        DiscordMessage message = new DiscordMessage("운영진 지정", null, name(actor) + " → " + name(member)
                + (title == null ? "" : " (" + DiscordMessage.escape(title) + ")"));
        discord.send(DiscordChannel.STAFF, message);
        if (actor.map(Member::getRole).filter(Role.DEV::equals).isPresent()) {
            discord.send(DiscordChannel.DEV, message);
        }
    }

    private String who(long memberId) {
        return name(members.findById(memberId));
    }

    private static String name(Optional<Member> member) {
        return member.map(Member::getNickname).map(DiscordMessage::escape).orElse("탈퇴한 부원");
    }

}
