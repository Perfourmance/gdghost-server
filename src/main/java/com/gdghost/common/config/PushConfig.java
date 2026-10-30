package com.gdghost.common.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gdghost.common.push.PushGateway;
import com.gdghost.common.push.WebPushGateway;

@Configuration
public class PushConfig {

    /** required(운영)면 키가 있어야 뜬다 — 푸시가 조용히 사라지지 않게. subject는 프론트 주소 */
    @Bean
    PushGateway pushGateway(@Value("${push.vapid.public-key}") String publicKey,
            @Value("${push.vapid.private-key}") String privateKey, @Value("${web.url}") String webUrl,
            @Value("${push.required:false}") boolean required, Clock clock) {
        if (required && (publicKey.isBlank() || privateKey.isBlank())) {
            throw new IllegalStateException("VAPID 키가 없습니다");
        }
        return new WebPushGateway(publicKey, privateKey, webUrl, clock);
    }

}
