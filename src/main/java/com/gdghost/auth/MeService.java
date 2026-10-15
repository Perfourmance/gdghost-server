package com.gdghost.auth;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;

@Service
public class MeService {

    private final MemberRepository members;
    private final MeReader meReader;
    private final Clock clock;

    public MeService(MemberRepository members, MeReader meReader, Clock clock) {
        this.members = members;
        this.meReader = meReader;
        this.clock = clock;
    }

    /** 앱을 열 때마다 불리므로 last_seen_at을 여기서 갱신한다 (라우팅 보정에 쓰임) */
    @Transactional
    public MeResponse me(long memberId) {
        Member member = members.findById(memberId)
                // 실제로는 일어나지 않는다 — member 행은 지우지 않고(탈퇴도 컬럼만 비움) login_token이 FK로 참조한다.
                // DB에서 손으로 행을 지운 경우에만 해당해 비회원(401)으로 돌려보낸다
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        member.markSeen(clock.instant());
        return meReader.read(member);
    }

}
