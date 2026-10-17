package com.gdghost.ops;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.audit.OpsAction;
import com.gdghost.common.audit.OpsAudit;
import com.gdghost.common.audit.OpsAuditLogger;
import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.page.OpsPage;
import com.gdghost.common.security.Role;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;
import com.gdghost.member.MemberStatus;

/**
 * 운영 페이지 부원 관리 — 목록 · 가입 이메일 보기 · 부원별 운영 기록.
 */
@Service
public class OpsMemberService {

    private final MemberRepository members;
    private final OpsAuditLogger opsAudit;
    private final OpsAuditLogReader auditLogs;

    public OpsMemberService(MemberRepository members, OpsAuditLogger opsAudit, OpsAuditLogReader auditLogs) {
        this.members = members;
        this.opsAudit = opsAudit;
        this.auditLogs = auditLogs;
    }

    /** q는 이름 · 닉네임 · 아이디 부분 일치 */
    @Transactional(readOnly = true)
    public OpsPage<OpsMemberResponse> list(String q, Integer generation, MemberStatus status, Role role,
            Boolean suspended, int page, int size) {
        return OpsPage.of(members.search(likePattern(q), generation, status, role, suspended,
                PageRequest.of(page, size)), OpsMemberResponse::from);
    }

    /** 볼 때마다 기록한다 — 주소는 기록에 남기지 않는다 */
    @Transactional
    public MemberEmailResponse viewEmail(long staffId, long memberId) {
        Member member = members.findById(memberId)
                .filter(found -> found.getWithdrawnAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        opsAudit.record(OpsAudit.of(staffId, OpsAction.EMAIL_VIEW).targetMember(memberId));
        return new MemberEmailResponse(member.getEmail());
    }

    /** 이 조회 자체는 기록하지 않는다. 탈퇴한 부원의 기록도 본다 */
    public OpsPage<OpsAuditLogResponse> auditLogs(long memberId, int page, int size) {
        if (!members.existsById(memberId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return auditLogs.findByTargetMember(memberId, page, size);
    }

    private static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String escaped = q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

}
