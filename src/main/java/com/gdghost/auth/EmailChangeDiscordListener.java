package com.gdghost.auth;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.discord.DiscordKind;
import com.gdghost.common.discord.DiscordMessage;
import com.gdghost.common.discord.DiscordMessage.Field;
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
        send(new DiscordMessage(DiscordKind.STAFF, "이메일 변경 요청 (어드민)", null,
                "부원이 새 메일의 링크를 열면 바뀌어요",
                List.of(Field.inline("요청", name(event.requesterId())), Field.inline("대상", name(event.memberId())))));
    }

    @DomainEventListener
    public void on(EmailChangedEvent event) {
        List<Field> fields = new ArrayList<>(List.of(Field.inline("대상", name(event.memberId()))));
        if (event.requesterId() != null) {
            fields.add(Field.inline("요청", name(event.requesterId())));
        }
        send(new DiscordMessage(DiscordKind.STAFF, "이메일 변경 완료 (어드민 요청)", null,
                "모든 기기에서 로그아웃됐어요", fields));
    }

    private void send(DiscordMessage message) {
        discord.send(DiscordChannel.STAFF, message);
        discord.send(DiscordChannel.DEV, message);
    }

    private String name(Long memberId) {
        return members.findById(memberId).map(Member::getNickname).map(DiscordMessage::escape).orElse("탈퇴한 부원");
    }

}
