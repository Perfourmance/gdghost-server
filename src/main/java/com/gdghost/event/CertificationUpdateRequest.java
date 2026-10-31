package com.gdghost.event;

import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 보낸 필드만 바꾼다. photoKeys를 보내면 사진을 통째로 교체한다 */
public record CertificationUpdateRequest(
        @Size(min = 1, max = 100) String body,
        @Size(min = 1, max = 3) List<@NotNull String> photoKeys) {

    public CertificationUpdateRequest {
        body = EventCreateRequest.strip(body);
    }

    @AssertTrue(message = "바꿀 필드를 하나 이상 보내야 합니다.")
    boolean isChanging() {
        return body != null || photoKeys != null;
    }

}
