package com.gdghost.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpMailGatewayTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final SmtpMailGateway gateway = new SmtpMailGateway(sender, "club@gmail.com");
    private final Mail mail = new Mail("hong@gmail.com", "[GDGoC] 인증번호", "123456");

    @Test
    void 보내는_사람과_내용을_채운다() {
        gateway.send(mail);

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("club@gmail.com");
        assertThat(message.getValue().getTo()).containsExactly("hong@gmail.com");
        assertThat(message.getValue().getSubject()).isEqualTo("[GDGoC] 인증번호");
        assertThat(message.getValue().getText()).isEqualTo("123456");
    }

    @Test
    void 보내기_실패는_한_번_더_시도한다() {
        doThrow(new MailSendException("시간 초과")).doNothing().when(sender).send(any(SimpleMailMessage.class));

        gateway.send(mail);

        verify(sender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void 두_번_실패하면_예외() {
        doThrow(new MailSendException("시간 초과")).when(sender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> gateway.send(mail)).isInstanceOf(MailSendException.class);
        verify(sender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void 계정_인증_실패는_다시_시도하지_않는다() {
        doThrow(new MailAuthenticationException("비밀번호 틀림")).when(sender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> gateway.send(mail)).isInstanceOf(MailAuthenticationException.class);
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
    }

}
