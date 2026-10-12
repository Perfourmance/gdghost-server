package com.gdghost.common.mail;

/**
 * 메일을 실제로 내보내는 곳. 서비스는 이걸 직접 쓰지 않고 MailService를 쓴다(한도 · 비동기).
 */
public interface MailGateway {

    void send(Mail mail);

}
