package com.gdghost.routing;

import java.util.List;

import com.gdghost.member.MemberStatus;
import com.gdghost.member.Position;

/** @param fallback AI를 쓰지 못해 규칙 점수만으로 고른 결과 */
public record SeniorMatchResponse(List<Item> items, boolean fallback) {

    public record Item(int rank, Member member, List<String> reasons) {
    }

    public record Member(long id, String nickname, int generation, MemberStatus status, Position position,
            String statusNote, boolean acceptCoffee) {
    }

}
