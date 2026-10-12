package com.gdghost.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 동아리 Gmail SMTP. 시간 초과 10초(spring.mail.properties), 보내기 실패만 1회 재시도
 * — 인증 · 메시지 오류는 다시 해도 같으므로 바로 실패.
 */
public class SmtpMailGateway implements MailGateway {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailGateway.class);

    private final JavaMailSender sender;
    private final String from;

    public SmtpMailGateway(JavaMailSender sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(Mail mail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(mail.to());
        message.setSubject(mail.subject());
        message.setText(mail.text());
        try {
            sender.send(message);
        } catch (MailSendException first) {
            log.warn("메일 발송 실패, 한 번 더 시도", first);
            sender.send(message);
        }
    }

}
