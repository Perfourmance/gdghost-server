package com.gdghost.invite;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.audit.OpsAction;
import com.gdghost.common.audit.OpsAudit;
import com.gdghost.common.audit.OpsAuditLogger;
import com.gdghost.common.audit.OpsTargetType;
import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.page.OpsPage;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;

@Service
public class InviteCodeService {

    // 헷갈리는 0 · O · 1 · I 제외
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int CODE_LENGTH = 8;

    private static final RandomGenerator RANDOM = new SecureRandom();

    private final InviteCodeRepository inviteCodes;
    private final MemberRepository members;
    private final OpsAuditLogger opsAudit;
    private final Clock clock;

    public InviteCodeService(InviteCodeRepository inviteCodes, MemberRepository members, OpsAuditLogger opsAudit,
            Clock clock) {
        this.inviteCodes = inviteCodes;
        this.members = members;
        this.opsAudit = opsAudit;
        this.clock = clock;
    }

    /** 같은 기수에서 아직 쓸 수 있는 코드는 폐기한다 — 기수당 활성 1개 */
    @Transactional
    public InviteCodeResponse issue(long actorId, InviteCodeIssueRequest request) {
        Instant now = clock.instant();
        List<InviteCode> replaced = inviteCodes.findUsable(request.generation(), now);
        replaced.forEach(code -> code.revoke(now));

        InviteCode code = inviteCodes.save(InviteCode.issue(newCode(), request.generation(), actorId,
                request.maxUses(), now.plus(Duration.ofDays(request.expiresInDaysOrDefault())), now));
        opsAudit.record(OpsAudit.of(actorId, OpsAction.INVITE_CODE_ISSUE)
                .target(OpsTargetType.INVITE_CODE, code.getId())
                .detail(Map.of("generation", code.getGeneration(), "maxUses", code.getMaxUses(),
                        "expiresAt", code.getExpiresAt().toString(),
                        "revokedCodeIds", replaced.stream().map(InviteCode::getId).toList())));

        String nickname = members.findById(actorId).map(Member::getNickname).orElse(null);
        return InviteCodeResponse.of(code, nickname, now);
    }

    @Transactional(readOnly = true)
    public OpsPage<InviteCodeResponse> list(int page, int size) {
        Instant now = clock.instant();
        return OpsPage.of(inviteCodes.findPageWithCreator(PageRequest.of(page, size)),
                row -> InviteCodeResponse.of(row.code(), row.creatorNickname(), now));
    }

    /** 이미 폐기된 코드도 성공 (멱등). 기록은 처음 폐기할 때만 */
    @Transactional
    public void revoke(long actorId, long codeId) {
        InviteCode code = find(codeId);
        if (code.getRevokedAt() != null) {
            return;
        }
        code.revoke(clock.instant());
        opsAudit.record(OpsAudit.of(actorId, OpsAction.INVITE_CODE_REVOKE)
                .target(OpsTargetType.INVITE_CODE, codeId));
    }

    /** 탈퇴한 부원은 개인정보가 지워져 빠진다 */
    @Transactional(readOnly = true)
    public List<InviteCodeMemberResponse> members(long codeId) {
        find(codeId);
        return inviteCodes.findMembers(codeId);
    }

    private InviteCode find(long codeId) {
        return inviteCodes.findById(codeId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    // 32^8 ≈ 1조 가지라 겹칠 일은 거의 없지만, 겹치면 다시 뽑는다
    private String newCode() {
        String code;
        do {
            code = randomCode(RANDOM);
        } while (inviteCodes.existsByCode(code));
        return code;
    }

    static String randomCode(RandomGenerator random) {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

}
