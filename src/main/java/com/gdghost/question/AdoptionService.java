package com.gdghost.question;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;

/** 채택 · 채택 취소. 질문 행을 잠가 답변 삭제와 상태가 엇갈리지 않게 한다 */
@Service
public class AdoptionService {

    private final QuestionRepository questions;
    private final AnswerRepository answers;

    public AdoptionService(QuestionRepository questions, AnswerRepository answers) {
        this.questions = questions;
        this.answers = answers;
    }

    // TODO 답변자 앱 내 알림(6) · 라우팅 답변 이력 점수(7)
    @Transactional
    public AdoptionResponse adopt(long memberId, long questionId, long answerId) {
        Question question = myQuestion(memberId, questionId);
        boolean adoptable = answers.findById(answerId)
                .filter(answer -> answer.getQuestionId() == questionId && !answer.isReply() && !answer.isDeleted())
                .isPresent();
        if (!adoptable) {
            throw new BusinessException(ErrorCode.ADOPT_INVALID, "이 질문의 답변만 채택할 수 있습니다. 답글은 채택할 수 없습니다.");
        }
        question.adopt(answerId);
        return new AdoptionResponse(question.getState(), question.getAdoptedAnswerId());
    }

    /** 채택이 없어도 그대로 성공 */
    @Transactional
    public void cancel(long memberId, long questionId) {
        myQuestion(memberId, questionId).cancelAdoption();
    }

    /** 없거나 삭제 · 숨김이면 404, 질문자가 아니면 403 */
    private Question myQuestion(long memberId, long questionId) {
        Question question = questions.findForUpdate(questionId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "질문을 찾을 수 없습니다."));
        if (question.getAuthorId() != memberId) {
            throw new BusinessException(ErrorCode.NOT_AUTHOR, "질문자만 채택할 수 있습니다.");
        }
        return question;
    }

}
