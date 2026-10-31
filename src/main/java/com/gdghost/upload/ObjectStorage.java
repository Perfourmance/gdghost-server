package com.gdghost.upload;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;

/** 사진 저장소. 주소 서명은 네트워크를 쓰지 않고, 나머지는 저장소를 호출한다 */
public interface ObjectStorage {

    boolean enabled();

    /** Content-Type · Content-Length를 서명에 넣어 다른 형식 · 크기로는 올릴 수 없게 한다 */
    URI presignPut(String objectKey, String contentType, long size, Instant signedAt, Duration ttl);

    /** 같은 signedAt · ttl이면 같은 주소가 나온다 */
    URI presignGet(String objectKey, Instant signedAt, Duration ttl);

    /** @return 파일 크기(바이트). 없으면 빈 값 */
    OptionalLong sizeOf(String objectKey);

    void delete(String objectKey);

    List<String> keysModifiedBefore(Instant cutoff);

}
