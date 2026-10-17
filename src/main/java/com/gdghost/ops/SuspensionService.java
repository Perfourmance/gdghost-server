package com.gdghost.ops;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.audit.OpsAction;
import com.gdghost.common.audit.OpsAudit;
import com.gdghost.common.audit.OpsAuditLogger;
import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.security.LoginTokenService;
import com.gdghost.common.security.Role;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;

/**
 * 계정 정지 · 해제. 정지하면 즉시 로그아웃되고 다시 로그인할 수 없다. 작성한 글은 그대로 둔다.
 */
@Service
public class SuspensionService {

    private final MemberRepository members;
    private final LoginTokenService loginTokens;
    private final OpsAuditLogger opsAudit;
    private final Clock clock;

    public SuspensionService(MemberRepository members, LoginTokenService loginTokens, OpsAuditLogger opsAudit,
            Clock clock) {
        this.members = members;
        this.loginTokens = loginTokens;
        this.opsAudit = opsAudit;
        this.clock = clock;
    }

    /** 이미 정지된 부원이면 그대로 두고 지금 상태를 돌려준다 (멱등) */
    // TODO 디스코드 기록(#45) · 보내지 않은 라우팅 후보 취소(라우팅 단계) · 응답 대기 커피챗 만료(커피챗 단계)
    @Transactional
    public SuspensionResponse suspend(long staffId, long memberId, String reason) {
        if (staffId == memberId) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "본인은 정지할 수 없습니다.");
        }
        Member member = find(memberId);
        if (member.getRole() != Role.MEMBER) {
            throw new BusinessException(ErrorCode.TARGET_IS_STAFF, "운영진 · 개발자는 먼저 운영진에서 해제해 주세요.");
        }
        if (!member.isSuspended()) {
            String trimmed = reason.strip();
            member.suspend(trimmed, staffId, clock.instant());
            loginTokens.revokeAll(memberId);
            opsAudit.record(OpsAudit.of(staffId, OpsAction.SUSPEND)
                    .targetMember(memberId)
                    .detail(Map.of("reason", trimmed)));
        }
        Member suspendedBy = members.findById(member.getSuspendedBy()).orElseThrow();
        return SuspensionResponse.of(member, suspendedBy);
    }

    /** 부원은 다시 로그인해야 한다 (정지 때 토큰을 지웠으므로) */
    // TODO 디스코드 기록(#45)
    @Transactional
    public void unsuspend(long staffId, long memberId) {
        Member member = find(memberId);
        if (!member.isSuspended()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "정지되지 않은 부원입니다.");
        }
        Instant now = clock.instant();
        member.unsuspend(now);
        opsAudit.record(OpsAudit.of(staffId, OpsAction.UNSUSPEND).targetMember(memberId));
    }

    private Member find(long memberId) {
        return members.findById(memberId)
                .filter(found -> found.getWithdrawnAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

}
