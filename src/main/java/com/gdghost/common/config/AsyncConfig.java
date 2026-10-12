package com.gdghost.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import com.gdghost.common.error.ErrorLogRecorder;

/**
 * 실행기는 Boot 기본(applicationTaskExecutor, spring.task.execution).
 * 반환값 없는 @Async 메서드(이벤트 리스너)의 예외를 error_log에 남긴다.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    // 설정 빈은 일찍 만들어져서 JDBC 빈을 바로 받지 않고 필요할 때 꺼낸다
    private final ObjectProvider<ErrorLogRecorder> errorLog;

    public AsyncConfig(ObjectProvider<ErrorLogRecorder> errorLog) {
        this.errorLog = errorLog;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (e, method, params) -> {
            String where = "async " + method.getDeclaringClass().getSimpleName() + "." + method.getName();
            log.error("비동기 작업 실패: {}", where, e);
            errorLog.getObject().record(e, where);
        };
    }

}
