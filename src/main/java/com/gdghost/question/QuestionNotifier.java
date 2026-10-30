package com.gdghost.question;

import org.springframework.stereotype.Component;

import com.gdghost.notification.NotificationTarget;
import com.gdghost.notification.NotificationType;
import com.gdghost.notification.NotificationWriter;

/** 질문 · 답변 알림 문구와 받는 사람. 익명 글은 닉네임 대신 그 글의 익명 표기만 쓴다 */
@Component
class QuestionNotifier {

    private final NotificationWriter notifications;

    QuestionNotifier(NotificationWriter notifications) {
        this.notifications = notifications;
    }

    void questionCreated(Question question) {
        notifications.toAllExcept(question.getAuthorId(), NotificationType.NEW_QUESTION, "새 질문이 올라왔어요",
                question.getTitle(), NotificationTarget.question(question.getId()), false);
    }

    /** 자기 글에 단 것은 알리지 않는다. 질문자가 부모 작성자이기도 하면 답글 알림 하나만 */
    void answerCreated(Question question, Answer parent, QuestionDetailReader.AnswerRow answer) {
        long actorId = answer.author().id();
        String actor = answer.anonymous() ? AnonLabels.answer(answer.aliasNo()) : answer.author().nickname();
        NotificationTarget target = NotificationTarget.answer(question.getId(), answer.id());
        if (parent != null && parent.getAuthorId() != actorId) {
            notifications.toMember(parent.getAuthorId(), NotificationType.NEW_REPLY,
                    actor + "님이 내 답변에 답글을 달았어요", question.getTitle(), target);
        }
        boolean questionAuthorIsParentAuthor = parent != null && parent.getAuthorId() == question.getAuthorId();
        if (question.getAuthorId() != actorId && !questionAuthorIsParentAuthor) {
            String title = actor + (parent == null ? "님이 내 질문에 답했어요" : "님이 내 질문에 답글을 달았어요");
            notifications.toMember(question.getAuthorId(), NotificationType.NEW_ANSWER, title, question.getTitle(),
                    target);
        }
    }

    void answerAdopted(Question question, Answer answer) {
        notifications.toMember(answer.getAuthorId(), NotificationType.ANSWER_ADOPTED, "내 답변이 채택됐어요",
                question.getTitle(), NotificationTarget.answer(question.getId(), answer.getId()));
    }

}
