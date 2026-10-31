package com.gdghost.question;

/** 라우팅 쪽 구현을 부르는 자리. 의존은 routing → question 한 방향 */
public interface RoutingPort {

    /** 질문과 같은 트랜잭션에서 지목 발송 */
    DirectDelivery direct(Question question, long targetMemberId);

    /** 후보도 발송도 없으면 null */
    QuestionRouting view(long questionId, boolean anonymous, long viewerId);

}
