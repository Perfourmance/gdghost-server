package com.gdghost.upload;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "업로드")
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Operation(summary = "사진 업로드 주소 발급", description = "프론트가 EXIF를 지운 뒤 uploadUrl로 R2에 직접 PUT한다(10분 유효). "
            + "headers를 그대로 보내고, 요청한 size와 같은 크기로 올려야 한다. 글에 연결되지 않은 사진은 하루 뒤 지워진다. "
            + "JPEG · PNG · WebP, 장당 1MB, 한 번에 3개, 부원당 분당 20개 · 하루 30개.")
    @PostMapping("/presigned-urls")
    public PresignResponse presign(@AuthenticationPrincipal AuthMember me,
            @Valid @RequestBody PresignRequest request) {
        return uploadService.presign(me.id(), request);
    }

}
