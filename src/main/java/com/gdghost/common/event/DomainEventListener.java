package com.gdghost.common.event;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 이벤트 리스너 — 커밋된 뒤에만, 다른 스레드에서 실행한다.
 * 실패해도 원래 요청은 성공이고 error_log에 남는다(AsyncConfig).
 * <p>
 * 발행: 서비스가 저장 후 {@code events.publishEvent(new QuestionCreatedEvent(questionId))}.
 * 이벤트에는 id만 담고 리스너가 필요한 값을 다시 읽는다.
 * 트랜잭션 밖에서 발행하면 실행되지 않는다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public @interface DomainEventListener {
}
