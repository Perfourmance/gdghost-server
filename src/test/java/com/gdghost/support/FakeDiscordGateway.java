package com.gdghost.support;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.springframework.context.annotation.Primary;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.discord.DiscordGateway;
import com.gdghost.common.discord.DiscordMessage;

/**
 * 테스트용 — 보내지 않고 쌓아 둔다. 앞 테스트의 비동기 전송이 늦게 올 수 있으므로 조건으로 찾는다.
 */
@Primary
public class FakeDiscordGateway implements DiscordGateway {

    public record Sent(DiscordChannel channel, DiscordMessage message) {

        public boolean is(DiscordChannel channel, String title) {
            return this.channel == channel && message.title().equals(title);
        }

    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;

    @Override
    public void send(DiscordChannel channel, DiscordMessage message) {
        if (failure != null) {
            throw failure;
        }
        sent.add(new Sent(channel, message));
    }

    /** 커밋 후 다른 스레드에서 보내므로 조건에 맞는 것이 count개 쌓일 때까지 최대 5초 기다린다 */
    public List<Sent> await(Predicate<Sent> condition, int count) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (true) {
            List<Sent> found = sent.stream().filter(condition).toList();
            if (found.size() >= count) {
                return found;
            }
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("디스코드 " + count + "건을 기다렸지만 " + found.size() + "건: " + sent);
            }
            sleep(20);
        }
    }

    public Sent await(Predicate<Sent> condition) {
        return await(condition, 1).getFirst();
    }

    /** 오지 않아야 하는 것 — 비동기라 잠시 기다린 뒤 확인한다 */
    public List<Sent> after(Duration wait, Predicate<Sent> condition) {
        sleep(wait.toMillis());
        return sent.stream().filter(condition).toList();
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public void clear() {
        sent.clear();
        failure = null;
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
