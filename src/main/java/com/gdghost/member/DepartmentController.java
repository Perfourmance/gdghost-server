package com.gdghost.member;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "선택 목록")
@RestController
public class DepartmentController {

    public record DepartmentsResponse(List<Departments.College> colleges) {
    }

    @Operation(summary = "학과 목록", description = "단과대학별 학과 선택지. 가입 · 프로필 수정의 department는 이 name 중 하나. 비회원도 부른다.")
    @GetMapping("/api/v1/departments")
    public ResponseEntity<DepartmentsResponse> list() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .body(new DepartmentsResponse(Departments.COLLEGES));
    }

}
