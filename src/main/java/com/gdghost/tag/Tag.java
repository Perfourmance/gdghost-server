package com.gdghost.tag;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 목록은 Flyway 시드로만 바꾼다 — 앱에서는 읽기만 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private TagKind kind;

    /** SKILL이면 관심 분야 id */
    private Long parentId;

    private String name;
    private String groupName;
    private int sortOrder;

}
