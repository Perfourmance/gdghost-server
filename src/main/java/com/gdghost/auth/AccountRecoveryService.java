package com.gdghost.auth;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;

/**
 * 아이디 찾기. 가입 여부를 알리지 않으려고 가입 안 된 메일도 같은 응답 · 같은 제한.
 * 가입 메일 자체를 잊은 부원은 운영진 문의 (가입 이메일 보기).
 */
@Service
public class AccountRecoveryService {

    private final MemberRepository members;
    private final EmailVerifier emailVerifier;

    public AccountRecoveryService(MemberRepository members, EmailVerifier emailVerifier) {
        this.members = members;
        this.emailVerifier = emailVerifier;
    }

    public void findLoginId(String rawEmail) {
        String email = SignupService.normalize(rawEmail);
        Optional<Member> member = members.findByEmail(email);
        if (member.isPresent()) {
            emailVerifier.sendNotice(VerificationPurpose.FIND_LOGIN_ID, email, member.get().getId(),
                    "가입한 아이디: " + member.get().getLoginId() + "\n본인이 요청하지 않았다면 이 메일은 무시해 주세요.");
        } else {
            emailVerifier.sendNotice(VerificationPurpose.FIND_LOGIN_ID, email, null, null);
        }
    }

}
