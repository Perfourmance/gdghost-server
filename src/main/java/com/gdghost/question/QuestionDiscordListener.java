package com.gdghost.question;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.discord.DiscordMessage;
import com.gdghost.common.discord.DiscordNotifier;
import com.gdghost.common.event.DomainEventListener;

/** 새 질문 → 부원 채널. 분류 · 제목 · 링크만 (작성자는 익명 여부와 상관없이 넣지 않는다) */
@Component
public class QuestionDiscordListener {

    private final QuestionRepository questions;
    private final DiscordNotifier discord;
    private final String webUrl;

    public QuestionDiscordListener(QuestionRepository questions, DiscordNotifier discord,
            @Value("${web.url}") String webUrl) {
        this.questions = questions;
        this.discord = discord;
        this.webUrl = webUrl;
    }

    @DomainEventListener
    public void on(QuestionCreatedEvent event) {
        questions.findById(event.questionId())
                .filter(question -> !question.isDeleted())
                .ifPresent(question -> discord.send(DiscordChannel.MEMBER, new DiscordMessage(
                        "새 질문 · " + label(question.getCategory()),
                        webUrl + "/questions/" + question.getId(),
                        DiscordMessage.escape(question.getTitle()))));
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
