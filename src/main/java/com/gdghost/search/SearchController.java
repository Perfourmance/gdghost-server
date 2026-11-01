package com.gdghost.search;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.page.CursorPage;
import com.gdghost.common.ratelimit.RateLimit;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "검색")
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "전체 검색", description = "q는 2–30자. type=ALL(기본)은 종류별 상위 5개와 total, cursor · size는 무시. "
            + "그 밖의 type은 그 종류의 페이지(최신순). 부원: 닉네임 · 이름 · 근황 · 태그(숨김 · 탈퇴 · 정지 제외). "
            + "질문: 제목 · 본문(익명 작성자 닉네임으로는 찾을 수 없음). 공유: 제목 · 본문 · 태그. 일정: 제목 · 메모 · 장소. 부원당 분당 30회.")
    @RateLimit(limit = 30)
    @GetMapping
    public Object search(@AuthenticationPrincipal AuthMember me, @RequestParam String q,
            @RequestParam(defaultValue = "ALL") SearchType type,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_SIZE) int size) {
        return searchService.search(me, q, type, cursor, size);
    }

}
