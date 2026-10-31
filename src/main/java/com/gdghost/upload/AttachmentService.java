package com.gdghost.upload;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import org.springframework.stereotype.Service;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;

/** 글에 사진 붙이기 · 보여 주기. 공유 · 피드 · 인증 서비스가 부른다 */
@Service
public class AttachmentService {

    private final ObjectStorage storage;
    private final AttachmentStore store;
    private final Clock clock;

    public AttachmentService(ObjectStorage storage, AttachmentStore store, Clock clock) {
        this.storage = storage;
        this.store = store;
        this.clock = clock;
    }

    /**
     * 글 작성 트랜잭션 안에서 부른다. R2에 실제로 있는지 확인한 뒤 저장하고, 순서는 목록 순서.
     * 남의 키 · 다른 용도의 키 · 이미 쓴 키 · 없는 파일은 UPLOAD_NOT_FOUND.
     * 1MB를 넘으면 파일을 지우고 UPLOAD_TOO_LARGE.
     */
    public void attach(UploadPurpose purpose, long ownerId, long memberId, List<String> objectKeys) {
        boolean usable = new HashSet<>(objectKeys).size() == objectKeys.size()
                && objectKeys.stream().allMatch(key -> UploadKeys.belongsTo(key, purpose, memberId))
                && store.attachedAmong(objectKeys).isEmpty();
        if (!usable) {
            throw notFound();
        }
        for (String key : objectKeys) {
            OptionalLong size = storage.sizeOf(key);
            if (size.isEmpty()) {
                throw notFound();
            }
            if (size.getAsLong() > UploadService.MAX_SIZE) {
                storage.delete(key);
                throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE, "사진은 장당 1MB 이하여야 합니다.");
            }
        }
        store.insert(purpose, ownerId, objectKeys);
    }

    /** 사진을 통째로 교체한다. 빠진 사진은 연결이 끊겨 정리 배치가 R2에서 지운다 */
    public void replace(UploadPurpose purpose, long ownerId, long memberId, List<String> objectKeys) {
        store.delete(purpose, ownerId);
        attach(purpose, ownerId, memberId, objectKeys);
    }

    /** 글을 지울 때 연결을 끊는다. 정리 배치가 R2에서 지운다 */
    public void detach(UploadPurpose purpose, long ownerId) {
        store.delete(purpose, ownerId);
    }

    /** 지금 붙어 있는 키, 순서대로 */
    public List<String> objectKeys(UploadPurpose purpose, long ownerId) {
        return store.find(purpose, List.of(ownerId)).stream().map(AttachmentStore.Row::objectKey).toList();
    }

    /** 글 id별 사진. 권한 확인은 부르는 쪽이 한다 — 여기서 나간 주소는 만료까지 누구나 열 수 있다 */
    public Map<Long, List<Attachment>> photos(UploadPurpose purpose, Collection<Long> ownerIds) {
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        PhotoUrlWindow window = PhotoUrlWindow.at(clock.instant());
        Map<Long, List<Attachment>> result = new LinkedHashMap<>();
        for (AttachmentStore.Row row : store.find(purpose, ownerIds)) {
            String url = storage.presignGet(row.objectKey(), window.signedAt(), window.ttl()).toString();
            result.computeIfAbsent(row.ownerId(), id -> new ArrayList<>())
                    .add(new Attachment(row.objectKey(), url, window.expiresAt(), row.sortOrder()));
        }
        return result;
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.UPLOAD_NOT_FOUND, "사진을 찾을 수 없습니다. 다시 올려 주세요.");
    }

}
