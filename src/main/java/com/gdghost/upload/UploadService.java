package com.gdghost.upload;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.ratelimit.RateLimitedException;
import com.gdghost.common.ratelimit.RateLimiter;

@Service
public class UploadService {

    static final long MAX_SIZE = 1024 * 1024;

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");
    private static final Duration PUT_TTL = Duration.ofMinutes(10);
    private static final int FILES_PER_MINUTE = 20;
    private static final int FILES_PER_DAY = 30;

    private final ObjectStorage storage;
    private final RateLimiter limiter;
    private final Clock clock;

    public UploadService(ObjectStorage storage, RateLimiter limiter, Clock clock) {
        this.storage = storage;
        this.limiter = limiter;
        this.clock = clock;
    }

    public PresignResponse presign(long memberId, PresignRequest request) {
        request.files().forEach(UploadService::check);
        acquire(memberId, request.files().size());

        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        List<PresignResponse.Item> items = request.files().stream().map(file -> {
            String key = UploadKeys.generate(request.purpose(), memberId, EXTENSIONS.get(file.contentType()), now);
            String url = storage.presignPut(key, file.contentType(), file.size(), now, PUT_TTL).toString();
            return new PresignResponse.Item(key, url, "PUT", Map.of("Content-Type", file.contentType()),
                    now.plus(PUT_TTL));
        }).toList();
        return new PresignResponse(items);
    }

    private static void check(PresignRequest.File file) {
        if (!EXTENSIONS.containsKey(file.contentType())) {
            throw new BusinessException(ErrorCode.UPLOAD_TYPE_NOT_ALLOWED, "JPEG · PNG · WebP만 올릴 수 있습니다.");
        }
        if (file.size() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE, "사진은 장당 1MB 이하여야 합니다.");
        }
    }

    // 제한은 요청이 아니라 파일 수라 @RateLimit을 쓰지 않는다. 짧은 창을 먼저 세어 막힌 요청이 하루 몫을 쓰지 않게 한다
    private void acquire(long memberId, int files) {
        acquire("upload:minute:" + memberId, FILES_PER_MINUTE, Duration.ofMinutes(1), files);
        acquire("upload:day:" + memberId, FILES_PER_DAY, Duration.ofDays(1), files);
    }

    private void acquire(String key, int limit, Duration window, int files) {
        long retryAfter = limiter.tryAcquire(key, limit, window, files);
        if (retryAfter > 0) {
            throw new RateLimitedException(retryAfter);
        }
    }

}
