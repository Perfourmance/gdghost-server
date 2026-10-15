package com.gdghost.tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;

@Service
public class TagService {

    static final int MAX_INTERESTS = 4;
    static final int MAX_SKILLS_PER_INTEREST = 3;
    static final int MAX_EXPERIENCES = 6;

    private final TagRepository tags;

    public TagService(TagRepository tags) {
        this.tags = tags;
    }

    @Transactional(readOnly = true)
    public TagsResponse list() {
        List<Tag> all = tags.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, List<TagsResponse.Skill>> skills = all.stream()
                .filter(tag -> tag.getKind() == TagKind.SKILL)
                .collect(Collectors.groupingBy(Tag::getParentId,
                        Collectors.mapping(tag -> new TagsResponse.Skill(tag.getId(), tag.getName()),
                                Collectors.toList())));
        List<TagsResponse.Interest> interests = all.stream()
                .filter(tag -> tag.getKind() == TagKind.INTEREST)
                .map(tag -> new TagsResponse.Interest(tag.getId(), tag.getName(), tag.getSortOrder(),
                        skills.getOrDefault(tag.getId(), List.of())))
                .toList();
        List<TagsResponse.Experience> experiences = all.stream()
                .filter(tag -> tag.getKind() == TagKind.EXPERIENCE)
                .map(tag -> new TagsResponse.Experience(tag.getId(), tag.getName(), tag.getGroupName()))
                .toList();
        return new TagsResponse(interests, experiences);
    }

    /**
     * 가입 · 프로필 수정의 태그 선택 규칙. 어긋나면 400 VALIDATION_FAILED (errors에 필드별 사유).
     * 관심 분야 1–4개, 세부 기술은 고른 분야의 것만 분야당 3개, 경험 태그 6개.
     * @param field 에러 필드 이름 앞부분 (예: "profile")
     * @return 저장할 태그 id 전체
     */
    @Transactional(readOnly = true)
    public List<Long> validateSelection(String field, Collection<Long> interestIds, Collection<Long> skillIds,
            Collection<Long> experienceIds) {
        Map<Long, Tag> found = tags.findAllById(
                Stream.of(interestIds, skillIds, experienceIds).flatMap(Collection::stream).distinct().toList())
                .stream().collect(Collectors.toMap(Tag::getId, Function.identity()));
        List<ErrorField> errors = new ArrayList<>();

        if (interestIds.isEmpty() || interestIds.size() > MAX_INTERESTS) {
            errors.add(new ErrorField(field + ".interestTagIds", "관심 분야는 1–4개 골라 주세요."));
        }
        if (!allOfKind(interestIds, found, TagKind.INTEREST)) {
            errors.add(new ErrorField(field + ".interestTagIds", "관심 분야 목록에 없는 값이 있습니다."));
        }
        if (!allOfKind(skillIds, found, TagKind.SKILL)
                || !skillIds.stream().allMatch(id -> interestIds.contains(found.get(id).getParentId()))) {
            errors.add(new ErrorField(field + ".skillTagIds", "고른 관심 분야의 세부 기술만 고를 수 있습니다."));
        } else if (skillIds.stream().collect(Collectors.groupingBy(id -> found.get(id).getParentId(),
                Collectors.counting())).values().stream().anyMatch(count -> count > MAX_SKILLS_PER_INTEREST)) {
            errors.add(new ErrorField(field + ".skillTagIds", "세부 기술은 분야마다 3개까지 고를 수 있습니다."));
        }
        if (experienceIds.size() > MAX_EXPERIENCES) {
            errors.add(new ErrorField(field + ".experienceTagIds", "경험 태그는 6개까지 고를 수 있습니다."));
        }
        if (!allOfKind(experienceIds, found, TagKind.EXPERIENCE)) {
            errors.add(new ErrorField(field + ".experienceTagIds", "경험 태그 목록에 없는 값이 있습니다."));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null, errors);
        }
        return found.keySet().stream().sorted().toList();
    }

    // 중복 id도 여기서 걸러낸다 — 같은 값을 두 번 보내면 개수 제한을 피할 수 있으므로
    private static boolean allOfKind(Collection<Long> ids, Map<Long, Tag> found, TagKind kind) {
        return new HashSet<>(ids).size() == ids.size()
                && ids.stream().allMatch(id -> found.containsKey(id) && found.get(id).getKind() == kind);
    }

}
