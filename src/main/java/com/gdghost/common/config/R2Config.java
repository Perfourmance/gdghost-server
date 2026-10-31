package com.gdghost.common.config;

import java.net.URI;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gdghost.upload.DisabledObjectStorage;
import com.gdghost.upload.ObjectStorage;
import com.gdghost.upload.R2ObjectStorage;

@Configuration
public class R2Config {

    /** required(운영)면 값이 모두 있어야 뜬다 — 빈 값으로 사진이 조용히 안 되는 일이 없게 */
    @Bean
    ObjectStorage objectStorage(@Value("${r2.account-id}") String accountId,
            @Value("${r2.access-key-id}") String accessKeyId,
            @Value("${r2.secret-access-key}") String secretAccessKey,
            @Value("${r2.bucket}") String bucket,
            @Value("${r2.required:false}") boolean required) {
        boolean configured = Stream.of(accountId, accessKeyId, secretAccessKey, bucket).noneMatch(String::isBlank);
        if (!configured) {
            if (required) {
                throw new IllegalStateException("R2 계정 ID · 키 · 버킷 값이 없습니다");
            }
            return new DisabledObjectStorage();
        }
        URI endpoint = URI.create("https://" + accountId + ".r2.cloudflarestorage.com");
        return new R2ObjectStorage(endpoint, bucket, accessKeyId, secretAccessKey);
    }

}
