package com.gdghost.embedding;

import java.util.List;

import com.gdghost.member.MyProfileResponse.Interest;
import com.gdghost.member.MyProfileResponse.TagItem;
import com.gdghost.question.QuestionCategory;

/** 임베딩할 글 조립. 자리는 백엔드, 조립 규칙은 AI팀 */
public interface TextBuilder {

    record MemberSource(List<Interest> interests, List<TagItem> experiences, String statusNote, String bio) {
    }

    record QuestionSource(QuestionCategory category, String title, String body) {
    }

    /** 넣을 내용이 없으면 빈 문자열 */
    String memberText(MemberSource member);

    String questionText(QuestionSource question);

    /** 선배 찾기 입력. 질문과 같은 방식으로 분류 + 분야 + 글 */
    String searchText(QuestionCategory category, String interestName, String text);

}
