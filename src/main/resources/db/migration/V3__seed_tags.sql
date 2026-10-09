-- 태그 시드 — AI 매칭 설계 부록 A(관심 분야 9 · 세부 기술 56) · 부록 B(경험 태그 18)
-- 태그는 이 마이그레이션으로만 넣는다. 추가 · 변경은 새 V 파일로

-- 관심 분야
INSERT INTO tag (kind, name, sort_order) VALUES
    ('INTEREST', '백엔드', 1),
    ('INTEREST', '프론트엔드', 2),
    ('INTEREST', '클라우드', 3),
    ('INTEREST', 'iOS', 4),
    ('INTEREST', '안드로이드', 5),
    ('INTEREST', '데이터·AI', 6),
    ('INTEREST', '디자인', 7),
    ('INTEREST', '기획', 8),
    ('INTEREST', '기타', 9);

-- 세부 기술 (부모 = 관심 분야. Flutter · 앱 출시·심사는 iOS와 안드로이드에 따로 있다)
INSERT INTO tag (kind, parent_id, name, sort_order)
SELECT 'SKILL', p.id, s.name, s.sort_order
FROM tag p
JOIN (VALUES
    ROW('백엔드', 'Spring', 1), ROW('백엔드', 'Node.js', 2), ROW('백엔드', 'Django/FastAPI', 3),
    ROW('백엔드', 'Go', 4), ROW('백엔드', 'DB 설계', 5), ROW('백엔드', 'API 설계', 6),
    ROW('백엔드', '배포·CI/CD', 7), ROW('백엔드', '테스트', 8),

    ROW('프론트엔드', 'React', 1), ROW('프론트엔드', 'Vue', 2), ROW('프론트엔드', 'Next.js', 3),
    ROW('프론트엔드', 'TypeScript', 4), ROW('프론트엔드', '상태 관리', 5), ROW('프론트엔드', '웹 성능', 6),
    ROW('프론트엔드', '웹 접근성', 7),

    ROW('클라우드', 'AWS', 1), ROW('클라우드', 'GCP', 2), ROW('클라우드', 'Docker', 3),
    ROW('클라우드', 'Kubernetes', 4), ROW('클라우드', 'Terraform', 5), ROW('클라우드', '모니터링', 6),
    ROW('클라우드', '네트워크', 7),

    ROW('iOS', 'Swift', 1), ROW('iOS', 'SwiftUI', 2), ROW('iOS', 'UIKit', 3),
    ROW('iOS', 'Flutter', 4), ROW('iOS', '앱 출시·심사', 5),

    ROW('안드로이드', 'Kotlin', 1), ROW('안드로이드', 'Jetpack Compose', 2), ROW('안드로이드', 'Flutter', 3),
    ROW('안드로이드', '앱 출시·심사', 4),

    ROW('데이터·AI', '데이터 분석', 1), ROW('데이터·AI', 'SQL', 2), ROW('데이터·AI', '머신러닝', 3),
    ROW('데이터·AI', '딥러닝', 4), ROW('데이터·AI', '컴퓨터 비전', 5), ROW('데이터·AI', '자연어 처리', 6),
    ROW('데이터·AI', 'LLM 활용', 7), ROW('데이터·AI', 'MLOps', 8),

    ROW('디자인', 'Figma', 1), ROW('디자인', 'UI 디자인', 2), ROW('디자인', 'UX 리서치', 3),
    ROW('디자인', '디자인 시스템', 4), ROW('디자인', '브랜딩·그래픽', 5), ROW('디자인', '프로토타이핑', 6),

    ROW('기획', '서비스 기획', 1), ROW('기획', 'PM', 2), ROW('기획', '데이터 기반 기획', 3),
    ROW('기획', '사업 기획', 4), ROW('기획', '요구사항 정리', 5),

    ROW('기타', '보안', 1), ROW('기타', '게임 개발', 2), ROW('기타', '임베디드·IoT', 3),
    ROW('기타', '블록체인', 4), ROW('기타', 'QA', 5), ROW('기타', '알고리즘·코딩테스트', 6)
) AS s (parent_name, name, sort_order) ON p.name = s.parent_name
WHERE p.kind = 'INTEREST';

-- 경험 태그
INSERT INTO tag (kind, name, group_name, sort_order) VALUES
    ('EXPERIENCE', '인턴', '취업 · 커리어', 1),
    ('EXPERIENCE', '정규직 취업', '취업 · 커리어', 2),
    ('EXPERIENCE', '이직', '취업 · 커리어', 3),
    ('EXPERIENCE', '창업', '취업 · 커리어', 4),
    ('EXPERIENCE', '프리랜서·외주', '취업 · 커리어', 5),
    ('EXPERIENCE', '대학원 진학', '학업', 6),
    ('EXPERIENCE', '학부연구생', '학업', 7),
    ('EXPERIENCE', '복수·부전공', '학업', 8),
    ('EXPERIENCE', '교환학생', '학업', 9),
    ('EXPERIENCE', '휴학', '학업', 10),
    ('EXPERIENCE', '편입', '학업', 11),
    ('EXPERIENCE', '군 복무', '학업', 12),
    ('EXPERIENCE', '공모전 수상', '활동', 13),
    ('EXPERIENCE', '해커톤', '활동', 14),
    ('EXPERIENCE', '오픈소스 기여', '활동', 15),
    ('EXPERIENCE', '자격증', '활동', 16),
    ('EXPERIENCE', '동아리 운영진', '활동', 17),
    ('EXPERIENCE', '대외활동', '활동', 18);
