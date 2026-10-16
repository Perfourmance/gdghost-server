package com.gdghost.common.config;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

import com.gdghost.common.error.ErrorLogRecorder;
import com.gdghost.common.mail.LogMailGateway;
import com.gdghost.common.mail.MailGateway;
import com.gdghost.common.mail.MailQuota;
import com.gdghost.common.mail.MailService;
import com.gdghost.common.mail.SmtpMailGateway;

@Configuration
public class MailConfig {

    // Gmail 일반 계정 한도(최근 24시간 약 500통)보다 낮게 — 서버는 한국 날짜로 세서 자정 전후로 2배까지 나갈 수 있음
    static final int DAILY_LIMIT = 200;

    /** SMTP 계정이 있으면 실제 발송, 없으면(로컬) 로그만 */
    @Bean
    MailGateway mailGateway(MailProperties properties, ObjectProvider<JavaMailSender> sender) {
        return StringUtils.hasText(properties.getUsername())
                ? new SmtpMailGateway(sender.getObject(), properties.getUsername())
                : new LogMailGateway();
    }

    @Bean
    MailQuota mailQuota(Clock clock) {
        return new MailQuota(clock, DAILY_LIMIT);
    }

    @Bean
    MailService mailService(MailGateway gateway, MailQuota quota,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor, ErrorLogRecorder errorLog) {
        return new MailService(gateway, quota, executor, errorLog);
    }

}
