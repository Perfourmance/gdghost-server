package com.gdghost.member;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 학과 선택지(상명대 서울캠퍼스). 고르는 단위는 가장 아래 단위: 전공이 있으면 전공, 없으면 학과 · 학부.
 * 저장 값이 같아야 매칭 학과 가산점이 붙으니 이름을 바꾸면 기존 member.department도 함께 옮긴다
 */
public final class Departments {

    public record College(String name, List<Department> departments) {
    }

    /** division: 속한 학부 · 학과. 선택지 자체가 학과 · 학부면 null */
    public record Department(String name, String division) {
    }

    public static final List<College> COLLEGES = List.of(
            new College("자유전공학부대학", List.of(
                    new Department("자유전공학부대학", null))),
            new College("인문사회과학대학", List.of(
                    new Department("역사콘텐츠전공", "인문콘텐츠학부"),
                    new Department("지적재산권전공", "인문콘텐츠학부"),
                    new Department("문헌정보학전공", "인문콘텐츠학부"),
                    new Department("한일문화콘텐츠전공", "인문콘텐츠학부"),
                    new Department("공간환경학부", null),
                    new Department("행정학부", null),
                    new Department("가족복지학과", null),
                    new Department("국가안보학과", null))),
            new College("사범대학", List.of(
                    new Department("국어교육과", null),
                    new Department("영어교육과", null),
                    new Department("교육학과", null),
                    new Department("수학교육과", null))),
            new College("경영경제대학", List.of(
                    new Department("경제금융학부", null),
                    new Department("경영학부", null),
                    new Department("글로벌경영학과", null),
                    new Department("융합경영학과", null))),
            new College("융합공과대학", List.of(
                    new Department("휴먼AI공학전공", "지능·데이터융합학부"),
                    new Department("핀테크전공", "지능·데이터융합학부"),
                    new Department("빅데이터융합전공", "지능·데이터융합학부"),
                    new Department("스마트생산전공", "지능·데이터융합학부"),
                    new Department("컴퓨터과학전공", "SW융합학부"),
                    new Department("지능IOT융합전공", "SW융합학부"),
                    new Department("애니메이션전공", "SW융합학부"),
                    new Department("전기공학전공", "SW융합학부"),
                    new Department("게임전공", "SW융합학부"),
                    new Department("생명공학전공", "생명화학공학부"),
                    new Department("화학에너지공학전공", "생명화학공학부"),
                    new Department("화공신소재전공", "생명화학공학부"),
                    new Department("식품영양학전공", "생명화학공학부"))),
            new College("문화예술대학", List.of(
                    new Department("의류학과", null),
                    new Department("스포츠건강관리전공", "스포츠무용학부"),
                    new Department("무용예술전공", "스포츠무용학부"),
                    new Department("조형예술전공", "미술학부"),
                    new Department("생활예술전공", "미술학부"),
                    new Department("음악학부", null))));

    private static final Set<String> NAMES = COLLEGES.stream()
            .flatMap(college -> college.departments().stream())
            .map(Department::name)
            .collect(Collectors.toUnmodifiableSet());

    private Departments() {
    }

    public static boolean contains(String name) {
        return NAMES.contains(name);
    }

}
