package com.gdghost.member;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.security.LoginTokenService;
import com.gdghost.common.security.Role;

@Service
public class WithdrawalService {

    private final MemberRepository members;
    private final CurrentPasswords currentPasswords;
    private final MemberWithdrawalWriter withdrawalWriter;
    private final LoginTokenService loginTokens;
    private final Clock clock;

    public WithdrawalService(MemberRepository members, CurrentPasswords currentPasswords,
            MemberWithdrawalWriter withdrawalWriter, LoginTokenService loginTokens, Clock clock) {
        this.members = members;
        this.currentPasswords = currentPasswords;
        this.withdrawalWriter = withdrawalWriter;
        this.loginTokens = loginTokens;
        this.clock = clock;
    }

    /** 한 트랜잭션. 모든 기기 로그아웃 */
    @Transactional
    public void withdraw(long memberId, WithdrawalRequest request) {
        Member member = members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        currentPasswords.check(member, request.password());
        if (member.getRole() == Role.STAFF && withdrawalWriter.lockActiveStaffCount() <= 1) {
            throw new BusinessException(ErrorCode.LAST_STAFF, "혼자 남은 운영진은 탈퇴할 수 없습니다. 다른 운영진을 먼저 지정해 주세요.");
        }

        Instant now = clock.instant();
        withdrawalWriter.erase(memberId, member.getEmail(), now);
        loginTokens.revokeAll(memberId);
        member.withdraw(now);
    }

}
