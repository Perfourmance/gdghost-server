package com.gdghost.auth;

import org.springframework.stereotype.Component;

import com.gdghost.member.Member;
import com.gdghost.member.ProfileCompletenessReader;

/** 가입 완료 · 로그인 · 내 세션 확인의 Me 응답 */
@Component
public class MeReader {

    private final ProfileCompletenessReader completeness;

    public MeReader(ProfileCompletenessReader completeness) {
        this.completeness = completeness;
    }

    // TODO 안 읽은 알림(6단계) · 확인 안 한 필독 공지(5단계) 수
    public MeResponse read(Member member) {
        return new MeResponse(member.getId(), member.getLoginId(), member.getNickname(), member.getGeneration(),
                member.getRole(), member.getStaffTitle(), 0, 0, completeness.read(member));
    }

}
