package com.gdghost.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 로컬용 — SMTP 계정이 없으면 보내지 않고 서버 로그에 찍는다(인증번호 확인용).
 * 운영은 MAIL_USERNAME이 필수라 여기로 오지 않는다.
 */
public class LogMailGateway implements MailGateway {

    private static final Logger log = LoggerFactory.getLogger(LogMailGateway.class);

    @Override
    public void send(Mail mail) {
        log.info("메일(보내지 않음) to={} subject={}\n{}", mail.to(), mail.subject(), mail.text());
    }

}
