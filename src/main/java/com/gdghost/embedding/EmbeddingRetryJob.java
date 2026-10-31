package com.gdghost.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gdghost.common.batch.BatchRunner;
import com.gdghost.embedding.EmbeddingService.Outcome;

/**
 * 비어 있거나 낡은 벡터를 채운다. 호출 실패가 나면 그 회차를 멈춰 연달아 부르지 않는다.
 * 그동안 후보 계산은 규칙 점수로 대체되므로 배치 자체는 실패가 아니다(실패 호출은 error_log에 남음).
 */
@Component
public class EmbeddingRetryJob {

    static final String NAME = "embedding_retry";

    private final BatchRunner batch;
    private final EmbeddingService embeddings;
    private final EmbeddingStore store;

    public EmbeddingRetryJob(BatchRunner batch, EmbeddingService embeddings, EmbeddingStore store) {
        this.batch = batch;
        this.embeddings = embeddings;
        this.store = store;
    }

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void run() {
        batch.run(NAME, this::retry);
    }

    private String retry() {
        if (!embeddings.enabled()) {
            return "disabled";
        }
        List<Outcome> outcomes = new ArrayList<>();
        boolean stopped = !process(store.questionsToRetry(embeddings.modelId()), embeddings::embedQuestion, outcomes)
                || !process(store.activeMembers(), embeddings::embedMember, outcomes);
        return "saved=%d badInput=%d failed=%d".formatted(count(outcomes, Outcome.SAVED),
                count(outcomes, Outcome.BAD_INPUT), count(outcomes, Outcome.FAILED))
                + (stopped ? " stopped" : "");
    }

    /** @return 호출 실패로 멈췄으면 false. 이 글만의 문제(BAD_INPUT)면 다음 글로 넘어간다 */
    private static boolean process(List<Long> ids, LongFunction<Outcome> embed, List<Outcome> outcomes) {
        for (long id : ids) {
            Outcome outcome = embed.apply(id);
            outcomes.add(outcome);
            if (outcome == Outcome.FAILED) {
                return false;
            }
        }
        return true;
    }

    private static long count(List<Outcome> outcomes, Outcome target) {
        return outcomes.stream().filter(target::equals).count();
    }

}
