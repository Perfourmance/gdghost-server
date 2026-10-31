package com.gdghost.upload;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gdghost.common.batch.BatchRunner;

/**
 * 글에 연결되지 않은 지 하루 지난 사진을 R2에서 지운다. attachment 행이 사라진 사진(삭제 실패분)도 여기서 지워진다.
 * 중간에 실패하면 남은 것은 다음 날 지워진다.
 */
@Component
public class UploadCleanupJob {

    static final String NAME = "upload_cleanup_nightly";
    private static final Duration KEEP = Duration.ofDays(1);

    private final BatchRunner batch;
    private final ObjectStorage storage;
    private final AttachmentStore store;
    private final Clock clock;

    public UploadCleanupJob(BatchRunner batch, ObjectStorage storage, AttachmentStore store, Clock clock) {
        this.batch = batch;
        this.storage = storage;
        this.store = store;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void run() {
        batch.run(NAME, this::cleanup);
    }

    private String cleanup() {
        if (!storage.enabled()) {
            return "skipped=r2_not_configured";
        }
        List<String> candidates = storage.keysModifiedBefore(clock.instant().minus(KEEP)).stream()
                .filter(UploadKeys::isOurs)
                .toList();
        Set<String> attached = store.attachedAmong(candidates);
        List<String> orphans = candidates.stream().filter(key -> !attached.contains(key)).toList();
        orphans.forEach(storage::delete);
        return "deleted=%d".formatted(orphans.size());
    }

}
