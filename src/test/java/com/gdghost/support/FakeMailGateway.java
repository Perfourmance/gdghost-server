package com.gdghost.support;

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

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public void clear() {
        sent.clear();
        failure = null;
    }

}
