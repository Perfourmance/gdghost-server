package com.gdghost.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 테스트에서는 끈다 — 배치가 테스트 도중 돌지 않게. 서버 1대 전제라 실행 잠금은 없다 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "batch.scheduling-enabled", matchIfMissing = true)
public class SchedulingConfig {
}
