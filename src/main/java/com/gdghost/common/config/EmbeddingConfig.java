package com.gdghost.common.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gdghost.embedding.EmbeddingClient;
import com.gdghost.embedding.WorkersAiEmbeddingClient;

@Configuration
public class EmbeddingConfig {

    /** required(운영)면 키가 있어야 뜬다 — 빈 값으로 AI가 조용히 꺼지지 않게 */
    @Bean
    EmbeddingClient embeddingClient(@Value("${embedding.account-id}") String accountId,
            @Value("${embedding.api-token}") String apiToken,
            @Value("${embedding.required:false}") boolean required) {
        if (required && (accountId.isBlank() || apiToken.isBlank())) {
            throw new IllegalStateException("Workers AI 계정 ID 또는 토큰이 없습니다");
        }
        return new WorkersAiEmbeddingClient(WorkersAiEmbeddingClient.BASE_URL, accountId, apiToken,
                Duration.ofSeconds(1));
    }

}
