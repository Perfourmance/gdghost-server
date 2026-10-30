package com.gdghost.common.discord;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdghost.common.error.ErrorLogRecorder;

/**
 * 디스코드 알림 입구. 다른 스레드에서 보내고, 실패하면 error_log에만 남긴다(원래 작업은 성공).
 * 도메인 알림은 이벤트 리스너(커밋 후)에서 부른다.
 */
public class DiscordNotifier {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotifier.class);

    private final DiscordGateway gateway;
    private final Executor executor;
    private final ErrorLogRecorder errorLog;

    public DiscordNotifier(DiscordGateway gateway, Executor executor, ErrorLogRecorder errorLog) {
        this.gateway = gateway;
        this.executor = executor;
        this.errorLog = errorLog;
    }

    public void send(DiscordChannel channel, DiscordMessage message) {
        try {
            executor.execute(() -> {
                try {
                    gateway.send(channel, message);
                } catch (RuntimeException e) {
                    log.error("디스코드 전송 실패: {} {}", channel, message.title(), e);
                    // 개발자 디스코드도 실패했을 수 있으므로 다시 디스코드로 알리지 않는다
                    errorLog.recordQuietly(e, "discord " + channel);
                }
            });
        } catch (RejectedExecutionException e) {
            // 서버가 내려가는 중 — 에러 기록 경로에서도 불리므로 던지지 않는다
            log.warn("디스코드 전송 건너뜀(실행기 거절): {} {}", channel, message.title());
        }
    }

}
