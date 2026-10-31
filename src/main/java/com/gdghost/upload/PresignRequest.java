package com.gdghost.upload;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PresignRequest(
        @NotNull UploadPurpose purpose,
        @NotEmpty @Size(max = 3) List<@NotNull @Valid File> files) {

    public record File(@NotBlank String contentType, @NotNull @Positive Long size) {
    }

}
