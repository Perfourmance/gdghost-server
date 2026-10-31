package com.gdghost.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gdghost.member.MyProfileResponse.Interest;
import com.gdghost.member.MyProfileResponse.TagItem;
import com.gdghost.question.QuestionCategory;

class DefaultTextBuilderTest {

    private final DefaultTextBuilder builder = new DefaultTextBuilder();

    @Test
    void 부원_글은_설계_문서_순서로_조립한다() {
        String text = builder.memberText(new TextBuilder.MemberSource(
                List.of(new Interest(1, "백엔드", List.of(new TagItem(2, "Spring"), new TagItem(3, "DB 설계"))),
                        new Interest(4, "기획", List.of())),
                List.of(new TagItem(5, "인턴"), new TagItem(6, "공모전 수상")), "인턴 근무 중", "취업 과정을 이야기해요"));

        assertThat(text).isEqualTo("""
                관심 분야: 백엔드(Spring, DB 설계), 기획
                경험: 인턴, 공모전 수상
                근황: 인턴 근무 중
                소개: 취업 과정을 이야기해요""");
    }

    @Test
    void 부원_글은_없는_항목을_뺀다() {
        assertThat(builder.memberText(new TextBuilder.MemberSource(List.of(), List.of(), null, "소개만")))
                .isEqualTo("소개: 소개만");
        assertThat(builder.memberText(new TextBuilder.MemberSource(List.of(), List.of(), null, null))).isEmpty();
    }

    @Test
    void 질문_글은_분류_제목_본문이고_본문은_없어도_된다() {
        assertThat(builder.questionText(new TextBuilder.QuestionSource(QuestionCategory.JOB, "이력서", "무엇을 쓰나요")))
                .isEqualTo("취업·채용 이력서\n무엇을 쓰나요");
        assertThat(builder.questionText(new TextBuilder.QuestionSource(QuestionCategory.STUDY, "공부법", null)))
                .isEqualTo("기술·공부 공부법");
    }

}
