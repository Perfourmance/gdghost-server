package com.gdghost.routing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.time.QuietHours;
import com.gdghost.question.QuestionRepository;

@Service
public class RoutingPassService {

    private static final Logger log = LoggerFactory.getLogger(RoutingPassService.class);

    public record PassResponse(String state, boolean nextCandidateNotified) {
    }

    private final QuestionRepository questions;
    private final RoutingReader reader;
    private final RoutingWriter writer;
    private final RoutingDelivery delivery;
    private final MatchingConfig config;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public RoutingPassService(QuestionRepository questions, RoutingReader reader, RoutingWriter writer,
            RoutingDelivery delivery, MatchingConfig config, TransactionTemplate transaction, Clock clock) {
        this.questions = questions;
        this.reader = reader;
        this.writer = writer;
        this.delivery = delivery;
        this.config = config;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** 넘기기는 먼저 확정한다. 다음 사람에게 못 보내도 다음 배치가 다시 본다 */
    public PassResponse pass(long memberId, long questionId) {
        Instant now = clock.instant();
        transaction.executeWithoutResult(status -> {
            questions.findForUpdate(questionId).filter(question -> !question.isDeleted())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "질문을 찾을 수 없습니다."));
            long routing = reader.mySentRouting(questionId, memberId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ROUTING_NOT_ASSIGNED, "내가 받은 질문이 아닙니다."));
            writer.markPassed(routing, now);
        });
        return new PassResponse("PASSED", sendNext(questionId, now));
    }

    private boolean sendNext(long questionId, Instant now) {
        if (QuietHours.contains(now)) {
            return false;
        }
        try {
            return reader.waitingForMembers(questionId, now.minus(RoutingJob.MEMBER_AFTER),
                    now.minus(RoutingJob.STALE_AFTER), now.minus(Duration.ofHours(config.directTimeoutHours())))
                    .map(delivery::sendToMembers)
                    .orElse(0) > 0;
        } catch (RuntimeException e) {
            log.warn("넘기기 뒤 다음 후보 발송 실패, 다음 배치에서 다시 본다: {}", e.getClass().getSimpleName());
            return false;
        }
    }

}
