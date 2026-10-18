package com.gdghost.member;

import java.util.List;

/** 부원 목록의 한 줄 */
public record MemberSummary(long id, String nickname, int generation, MemberStatus status, Position position,
        String statusNote, List<String> interestTags, long answerCount, boolean acceptQuestion,
        boolean acceptCoffee, boolean isStaff) {
}
