package com.gdghost.upload;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;

/** R2 값이 없는 로컬용. 운영은 값이 없으면 기동에 실패하므로 여기로 오지 않는다 */
public class DisabledObjectStorage implements ObjectStorage {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public URI presignPut(String objectKey, String contentType, long size, Instant signedAt, Duration ttl) {
        throw notConfigured();
    }

    @Override
    public URI presignGet(String objectKey, Instant signedAt, Duration ttl) {
        throw notConfigured();
    }

    @Override
    public OptionalLong sizeOf(String objectKey) {
        throw notConfigured();
    }

    @Override
    public void delete(String objectKey) {
        throw notConfigured();
    }

    @Override
    public List<String> keysModifiedBefore(Instant cutoff) {
        throw notConfigured();
    }

    private static IllegalStateException notConfigured() {
        return new IllegalStateException("R2 설정이 없습니다");
    }

}
