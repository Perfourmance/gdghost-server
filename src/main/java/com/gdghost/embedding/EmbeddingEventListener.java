package com.gdghost.embedding;

import org.springframework.stereotype.Component;

import com.gdghost.common.event.DomainEventListener;
import com.gdghost.member.MemberProfileChangedEvent;
import com.gdghost.question.QuestionCreatedEvent;
import com.gdghost.question.QuestionEditedEvent;

@Component
public class EmbeddingEventListener {

    private final EmbeddingService embeddings;

    public EmbeddingEventListener(EmbeddingService embeddings) {
        this.embeddings = embeddings;
    }

    @DomainEventListener
    public void on(QuestionCreatedEvent event) {
        embeddings.embedQuestion(event.questionId());
    }

    @DomainEventListener
    public void on(QuestionEditedEvent event) {
        embeddings.embedQuestion(event.questionId());
    }

    @DomainEventListener
    public void on(MemberProfileChangedEvent event) {
        embeddings.embedMember(event.memberId());
    }

}
