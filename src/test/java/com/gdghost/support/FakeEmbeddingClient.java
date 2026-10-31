package com.gdghost.support;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.springframework.context.annotation.Primary;

import com.gdghost.embedding.EmbeddingClient;
import com.gdghost.embedding.EmbeddingException;

/** 테스트용 — 부르지 않고 받은 글을 쌓아 둔다 */
@Primary
public class FakeEmbeddingClient implements EmbeddingClient {

    private final List<String> texts = new CopyOnWriteArrayList<>();
    private volatile Predicate<String> failing = text -> false;
    private volatile Predicate<String> badInput = text -> false;

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String modelId() {
        return "@cf/baai/bge-m3";
    }

    @Override
    public List<float[]> embed(List<String> inputs) {
        texts.addAll(inputs);
        return inputs.stream().map(this::vector).toList();
    }

    private float[] vector(String text) {
        if (badInput.test(text)) {
            throw new EmbeddingException("임베딩 실패: HTTP 400", true);
        }
        if (failing.test(text)) {
            throw new EmbeddingException("임베딩 실패: HTTP 500", false);
        }
        float[] vector = new float[1024];
        Arrays.fill(vector, 0.5f);
        return vector;
    }

    public void failWhen(Predicate<String> condition) {
        failing = condition;
    }

    public void rejectWhen(Predicate<String> condition) {
        badInput = condition;
    }

    public List<String> texts(Predicate<String> condition) {
        return texts.stream().filter(condition).toList();
    }

    public void clear() {
        texts.clear();
        failing = text -> false;
        badInput = text -> false;
    }

    /** 리스너가 비동기이므로 조건으로 기다린다 */
    public static void await(BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("조건을 5초 동안 기다렸지만 만족하지 않음");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

}
