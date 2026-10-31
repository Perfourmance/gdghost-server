package com.gdghost.event;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CertificationCreateRequest(
        @NotBlank @Size(max = 100) String body,
        @NotEmpty @Size(max = 3) List<@NotNull String> photoKeys) {

    public CertificationCreateRequest {
        body = EventCreateRequest.strip(body);
    }

}
