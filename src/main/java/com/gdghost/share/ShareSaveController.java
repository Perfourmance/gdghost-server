package com.gdghost.share;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.page.CursorPage;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "공유")
@RestController
@RequestMapping("/api/v1")
public class ShareSaveController {

    private final ShareSaveService saveService;

    public ShareSaveController(ShareSaveService saveService) {
        this.saveService = saveService;
    }

    @Operation(summary = "공유 저장", description = "멱등: 이미 저장했으면 그대로 200. 본인 글도 저장할 수 있다. 없거나 삭제 · 숨김이면 404.")
    @PutMapping("/shares/{shareId}/save")
    public ShareSaveResponse save(@AuthenticationPrincipal AuthMember me, @PathVariable long shareId) {
        return saveService.save(me.id(), shareId);
    }

    @Operation(summary = "공유 저장 취소", description = "멱등: 저장하지 않았어도 200. 삭제된 글도 저장 행을 뺄 수 있게 404를 내지 않는다.")
    @DeleteMapping("/shares/{shareId}/save")
    public ShareSaveResponse unsave(@AuthenticationPrincipal AuthMember me, @PathVariable long shareId) {
        return saveService.unsave(me.id(), shareId);
    }

    @Operation(summary = "저장한 공유", description = "최근 저장한 순. 공유 목록 항목 + savedAt. 삭제 · 숨김된 글은 빠진다.")
    @GetMapping("/me/saved-shares")
    public CursorPage<SavedShareSummary> saved(@AuthenticationPrincipal AuthMember me,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_SIZE) int size) {
        return saveService.saved(me.id(), cursor, size);
    }

}
