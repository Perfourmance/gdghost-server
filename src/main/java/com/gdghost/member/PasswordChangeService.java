package com.gdghost.member;

import java.time.Clock;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.security.LoginTokenService;

@Service
public class PasswordChangeService {

    private final MemberRepository members;
    private final CurrentPasswords currentPasswords;
    private final PasswordEncoder passwordEncoder;
    private final LoginTokenService loginTokens;
    private final Clock clock;

    public PasswordChangeService(MemberRepository members, CurrentPasswords currentPasswords,
            PasswordEncoder passwordEncoder, LoginTokenService loginTokens, Clock clock) {
        this.members = members;
        this.currentPasswords = currentPasswords;
        this.passwordEncoder = passwordEncoder;
        this.loginTokens = loginTokens;
        this.clock = clock;
    }

    /** 다른 기기는 모두 로그아웃, 이 기기는 새 토큰 */
    @Transactional
    public LoginTokenService.Issued change(long memberId, String currentToken, PasswordChangeRequest request,
            String userAgent) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        currentPasswords.check(member, request.currentPassword());
        Passwords.check(request.newPassword());

        member.changePassword(passwordEncoder.encode(request.newPassword()), clock.instant());
        return loginTokens.reissueOnly(memberId, currentToken, userAgent);
    }

}
