# 작업 방법

혼자 개발해도 이 순서를 지킵니다. **main에는 PR로만 들어가고, CI가 통과해야 합칠 수 있습니다.**

```
이슈 고르기 → 브랜치 만들기 → 코드 + 테스트 → 커밋 → push → PR → CI 통과 → Squash merge → 이슈 자동 닫힘
```

## 0. 큰 그림

| 용어 | 뜻 | 우리 규칙 |
|---|---|---|
| 마일스톤 | 이슈 묶음 + 진행률 | M0 준비 → M1 공통 → M2–M6 (1–5단계) → M7 서버 배포 → M8–M13 (6–11단계, 머지 = 자동 배포) → M14 출시 점검(11/20). **위에서부터 순서대로** |
| 이슈 | 할 일 하나 | 본문 체크리스트를 위에서부터 채운다 |
| 브랜치 | main에서 떨어져 나온 작업 공간 | `feat/12-login` (종류/이슈번호-짧은이름) |
| PR | 브랜치를 main에 합치자는 요청 | 본문에 `Closes #12` → 머지하면 이슈가 닫히고 진행률이 오른다 |
| CI | PR마다 GitHub Actions가 빌드 + 테스트 | 빨간색이면 합칠 수 없다 |

## 1. 이슈 고르기

```bash
gh issue list --milestone "M2 1단계 계정"      # 지금 마일스톤의 할 일
gh issue view 12                              # 본문(체크리스트) 보기
```

마일스톤 안에서도 **이슈 번호가 작은 것부터** 합니다. 앞 이슈가 뒤 이슈의 바탕입니다.

## 2. 브랜치 만들기

```bash
git switch main
git pull                                      # 최신 main 받기
git switch -c feat/12-login                   # 새 브랜치
```

| 접두어 | 언제 |
|---|---|
| `feat/` | 기능 (API) |
| `fix/` | 버그 |
| `test/` | 테스트만 |
| `chore/` | 설정 · 빌드 · CI |
| `docs/` | 문서 |

## 3. 코드 + 테스트 — "다 됐다"의 기준

이슈 하나를 닫으려면 **세 가지가 모두** 있어야 합니다.

1. **통합 테스트** (`src/test/.../*IntegrationTest.java`, API 하나당 파일 하나)
   - 성공 1개
   - API 설계서 `에러` 칸의 주요 에러 코드
   - 권한 — 비회원 401 · 권한 없음 403 · 어드민은 404
2. **Swagger에서 눌러서 동작** — `./gradlew bootRun` → http://localhost:8080/swagger-ui/index.html
3. **CI 초록색**

```bash
./gradlew test                                # 전체 테스트 (Docker 켜기)
./gradlew test --tests "*LoginIntegrationTest" # 하나만
```

규칙이 복잡한 계산(익명 번호, 초대 코드 상태 등)은 **단위 테스트**를 따로 둡니다. DB 없이 빨리 돕니다.

## 4. 커밋

작게, 자주. 메시지는 `종류: 무엇을 (#이슈)`.

```bash
git add -A
git commit -m "feat: 로그인 API 추가 (#12)"
```

종류: `feat` · `fix` · `test` · `refactor` · `docs` · `chore`

## 5. push + PR

```bash
git push -u origin feat/12-login
gh pr create --fill                           # 템플릿이 열립니다. 'Closes #12'를 꼭 남기기
gh pr checks --watch                          # CI 결과 기다리기
```

## 6. 머지

CI가 초록색이면:

```bash
gh pr merge --squash --delete-branch          # 커밋 여러 개를 하나로 합쳐서 main에
git switch main && git pull
```

이슈가 자동으로 닫히고 마일스톤 진행률이 올라갑니다.

## 7. CI가 빨간색일 때

1. PR 화면 → Checks → `build` → 실패한 단계 로그 확인
2. 테스트 실패면 Actions 실행 화면의 **Artifacts → test-report**를 내려받아 `index.html`을 연다
3. 로컬에서 같은 테스트를 돌려 고치고 다시 push (같은 PR에 자동 반영)

## 8. 하지 않는 것

- main에 직접 push (규칙으로 막혀 있음)
- DB를 손으로 수정 — 스키마는 Flyway `src/main/resources/db/migration/V{n}__설명.sql`로만
- 비밀값 커밋 — `.env`는 git에서 빠져 있고, 예시는 `.env.example`. 서버 비밀값은 GitHub Secrets
- 기획에 없는 기능 추가 — 기획 문서는 동결. 바꿔야 하면 이슈로 먼저 논의
