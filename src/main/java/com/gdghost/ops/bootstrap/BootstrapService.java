package com.gdghost.ops.bootstrap;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.security.Role;
import com.gdghost.invite.InviteCode;
import com.gdghost.invite.InviteCodeRepository;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;

/**
 * 순서: 준비용 코드로 가입 → BOOTSTRAP_DEV_LOGIN_IDS에 아이디를 넣고 재기동 → 첫 운영진 지정(어드민 API).
 */
@Service
public class BootstrapService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);
    private static final int CODE_MAX_LENGTH = 20;

    private final InviteCodeRepository inviteCodes;
    private final MemberRepository members;
    private final Clock clock;

    public BootstrapService(InviteCodeRepository inviteCodes, MemberRepository members, Clock clock) {
        this.inviteCodes = inviteCodes;
        this.members = members;
        this.clock = clock;
    }

    @Transactional
    public void apply(BootstrapProperties properties) {
        Instant now = clock.instant();
        createInviteCode(properties, now);
        promoteDevs(properties, now);
    }

    // 한 건이라도 있으면 만들지 않는다 — 정식 코드를 발급한 뒤 재기동해도 되살아나지 않게
    private void createInviteCode(BootstrapProperties properties, Instant now) {
        if (properties.inviteCode() == null || inviteCodes.count() > 0) {
            return;
        }
        if (properties.generation() == null) {
            throw new IllegalStateException("BOOTSTRAP_INVITE_CODE에는 BOOTSTRAP_GENERATION(기수)이 함께 필요");
        }
        if (properties.inviteCode().length() > CODE_MAX_LENGTH) {
            throw new IllegalStateException("BOOTSTRAP_INVITE_CODE는 " + CODE_MAX_LENGTH + "자 이하");
        }
        inviteCodes.save(InviteCode.bootstrap(properties.inviteCode(), properties.generation(), now));
        log.info("준비용 초대 코드 생성 (기수 {})", properties.generation());
    }

    // 아직 가입하지 않은 아이디는 건너뛴다 — 가입 후 재기동하면 반영
    private void promoteDevs(BootstrapProperties properties, Instant now) {
        if (properties.devLoginIds().isEmpty()) {
            return;
        }
        for (Member member : members.findByLoginIdIn(properties.devLoginIds())) {
            if (member.getRole() != Role.DEV && member.isActive()) {
                member.promoteToDev(now);
                log.info("부원 {} → 개발자 지정", member.getId());
            }
        }
    }

}
