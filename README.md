# GDGhost Server

GDGoC SMU 커뮤니티 **GDGhost**의 백엔드입니다. Spring Boot · Java 21 · MySQL.

> 기획 문서(노션)가 기준입니다.

## 실행

```bash
cp .env.example .env          # 처음 한 번. 비밀번호 칸을 채운다 (.env는 git에 안 올라감)
docker compose up -d          # 로컬 MySQL
./gradlew bootRun             # 서버 (http://localhost:8080)
open http://localhost:8080/swagger-ui/index.html   # API 문서
```

## 테스트

```bash
./gradlew test                # Docker가 켜져 있어야 합니다 (Testcontainers)
```

## 작업 방법

이슈 → 브랜치 → PR → CI → 머지. 자세한 순서는 [CONTRIBUTING.md](CONTRIBUTING.md).
진행 상황은 [마일스톤](../../milestones)에서 봅니다.
