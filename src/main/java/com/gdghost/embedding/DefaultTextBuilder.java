package com.gdghost.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.gdghost.member.MyProfileResponse.Interest;
import com.gdghost.member.MyProfileResponse.TagItem;
import com.gdghost.question.QuestionCategory;

/** 부원이 입력한 값만 쓴다. 질문자 프로필은 넣지 않는다 (AI 매칭 설계 3 · 4장) */
@Component
public class DefaultTextBuilder implements TextBuilder {

    @Override
    public String memberText(MemberSource member) {
        List<String> lines = new ArrayList<>();
        if (!member.interests().isEmpty()) {
            lines.add("관심 분야: " + member.interests().stream()
                    .map(DefaultTextBuilder::interest)
                    .collect(Collectors.joining(", ")));
        }
        if (!member.experiences().isEmpty()) {
            lines.add("경험: " + names(member.experiences()));
        }
        if (member.statusNote() != null) {
            lines.add("근황: " + member.statusNote());
        }
        if (member.bio() != null) {
            lines.add("소개: " + member.bio());
        }
        return String.join("\n", lines);
    }

    @Override
    public String questionText(QuestionSource question) {
        String head = label(question.category()) + " " + question.title();
        return question.body() == null ? head : head + "\n" + question.body();
    }

    private static String interest(Interest interest) {
        return interest.skills().isEmpty() ? interest.name()
                : interest.name() + "(" + names(interest.skills()) + ")";
    }

    private static String names(List<TagItem> tags) {
        return tags.stream().map(TagItem::name).collect(Collectors.joining(", "));
    }

    private static String label(QuestionCategory category) {
        return switch (category) {
            case JOB -> "취업·채용";
            case CAREER -> "커리어·이직";
            case PATH -> "진로 고민";
            case CAMPUS -> "학교생활";
            case STUDY -> "기술·공부";
        };
    }

}
