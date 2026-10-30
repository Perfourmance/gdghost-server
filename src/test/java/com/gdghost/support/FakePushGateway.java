package com.gdghost.support;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.springframework.context.annotation.Primary;

import com.gdghost.common.push.PushGateway;
import com.gdghost.common.push.PushResult;
import com.gdghost.common.push.PushSendException;
import com.gdghost.common.push.PushTarget;

/** 테스트용 — 보내지 않고 쌓아 둔다. 리스너가 비동기이므로 조건으로 기다린다 */
@Primary
public class FakePushGateway implements PushGateway {

    public record Sent(PushTarget target, String payload) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private final Map<String, Object> outcomes = new ConcurrentHashMap<>();

    @Override
    public PushResult send(PushTarget target, String payload) {
        Object outcome = outcomes.get(target.endpoint());
        if (outcome instanceof RuntimeException failure) {
            throw failure;
        }
        sent.add(new Sent(target, payload));
        return outcome == null ? PushResult.SENT : (PushResult) outcome;
    }

    public void gone(String endpoint) {
        outcomes.put(endpoint, PushResult.GONE);
    }

    public void fail(String endpoint) {
        outcomes.put(endpoint, new PushSendException("푸시 서비스 응답 500"));
    }

    public List<Sent> await(Predicate<Sent> condition, int count) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (true) {
            List<Sent> found = sent.stream().filter(condition).toList();
            if (found.size() >= count) {
                return found;
            }
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("푸시 " + count + "건을 기다렸지만 " + found.size() + "건: " + sent);
            }
            sleep(20);
        }
    }

    public List<Sent> after(Duration wait, Predicate<Sent> condition) {
        sleep(wait.toMillis());
        return sent.stream().filter(condition).toList();
    }

    public void clear() {
        sent.clear();
        outcomes.clear();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

}
