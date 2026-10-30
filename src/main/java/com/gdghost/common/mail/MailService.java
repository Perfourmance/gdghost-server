package com.gdghost.common.mail;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.gdghost.common.error.ErrorLogRecorder;

/**
 * 메일 발송 API(202)가 쓰는 입구. 한도는 바로 확인하고(503), 발송은 다른 스레드에서 한다.
 * 트랜잭션 안에서 부르면 커밋된 뒤에 보낸다 — 롤백된 인증번호가 메일로 나가지 않게.
 * 롤백돼도 한도는 이미 센 것으로 둔다.
 */
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final MailGateway gateway;
    private final MailQuota quota;
    private final Executor executor;
    private final ErrorLogRecorder errorLog;

    public MailService(MailGateway gateway, MailQuota quota, Executor executor, ErrorLogRecorder errorLog) {
        this.gateway = gateway;
        this.quota = quota;
        this.executor = executor;
        this.errorLog = errorLog;
    }

    public void send(Mail mail) {
        quota.acquire();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(mail);
                }
            });
        } else {
            dispatch(mail);
        }
    }

    // 실패는 error_log + 개발자 디스코드(ErrorLogRecorder)
    private void dispatch(Mail mail) {
        executor.execute(() -> {
            try {
                gateway.send(mail);
            } catch (RuntimeException e) {
                log.error("메일 발송 실패: {}", mail.subject(), e);
                errorLog.record(e, "mail " + mail.subject());
            }
        });
    }

}
