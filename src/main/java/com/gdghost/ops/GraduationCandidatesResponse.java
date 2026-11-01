package com.gdghost.ops;

import java.time.Instant;
import java.util.List;

import com.gdghost.member.Member;
import com.gdghost.member.MemberStatus;

public record GraduationCandidatesResponse(List<Item> items) {

    public record Item(long id, String name, String nickname, int generation, String studentNo, MemberStatus status,
                       Instant lastSeenAt) {

        static Item from(Member member) {
            return new Item(member.getId(), member.getName(), member.getNickname(), member.getGeneration(),
                    member.getStudentNo(), member.getStatus(), member.getLastSeenAt());
        }

    }

}
