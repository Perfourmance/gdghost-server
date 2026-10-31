package com.gdghost.support;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.annotation.Primary;

import com.gdghost.upload.ObjectStorage;

/** 테스트용 — 메모리에 파일 크기 · 시각만 둔다. 주소는 받은 값이 보이는 가짜 */
@Primary
public class FakeObjectStorage implements ObjectStorage {

    private record Stored(long size, Instant modifiedAt) {
    }

    private final Map<String, Stored> objects = new ConcurrentHashMap<>();
    private final List<String> deleted = new CopyOnWriteArrayList<>();

    public void put(String objectKey, long size, Instant modifiedAt) {
        objects.put(objectKey, new Stored(size, modifiedAt));
    }

    public boolean exists(String objectKey) {
        return objects.containsKey(objectKey);
    }

    public List<String> deleted() {
        return deleted;
    }

    public void clear() {
        objects.clear();
        deleted.clear();
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public URI presignPut(String objectKey, String contentType, long size, Instant signedAt, Duration ttl) {
        return URI.create("https://r2.test/%s?method=PUT&size=%d&ttl=%d".formatted(objectKey, size, ttl.toSeconds()));
    }

    @Override
    public URI presignGet(String objectKey, Instant signedAt, Duration ttl) {
        return URI.create("https://r2.test/%s?method=GET&signedAt=%s&ttl=%d".formatted(objectKey, signedAt, ttl.toSeconds()));
    }

    @Override
    public OptionalLong sizeOf(String objectKey) {
        Stored stored = objects.get(objectKey);
        return stored == null ? OptionalLong.empty() : OptionalLong.of(stored.size());
    }

    @Override
    public void delete(String objectKey) {
        objects.remove(objectKey);
        deleted.add(objectKey);
    }

    @Override
    public List<String> keysModifiedBefore(Instant cutoff) {
        return objects.entrySet().stream()
                .filter(entry -> entry.getValue().modifiedAt().isBefore(cutoff))
                .map(Map.Entry::getKey)
                .toList();
    }

}
