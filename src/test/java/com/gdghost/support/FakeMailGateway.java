package com.gdghost.support;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.annotation.Primary;

import com.gdghost.common.mail.Mail;
import com.gdghost.common.mail.MailGateway;

/**
 * 테스트용 — 보내지 않고 쌓아 둔다. 컨텍스트가 테스트 사이에 공유되므로 쓰기 전에 clear().
 */
@Primary
public class FakeMailGateway implements MailGateway {

    private final List<Mail> sent = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;

    @Override
    public void send(Mail mail) {
        if (failure != null) {
            throw failure;
        }
        sent.add(mail);
    }

    public List<Mail> sent() {
        return sent;
    }

    /** 발송은 커밋 후 다른 스레드라 응답 직후엔 아직 없을 수 있다 — count통이 쌓일 때까지 최대 5초 기다린다 */
    public List<Mail> awaitSent(int count) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (sent.size() < count) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("메일 " + count + "통을 기다렸지만 " + sent.size() + "통");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        return sent;
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public void clear() {
        sent.clear();
        failure = null;
    }

}
