# ticket-on

소규모 주최자가 좌석 배치도를 직접 만들고, 관객이 원하는 자리를 골라 예매하는 웹 서비스.

- 기획 문서: [`docs/`](docs/README.md)
- 기술 스택·구조: [`docs/16-architecture.md`](docs/16-architecture.md)
- 화면: [Figma](https://www.figma.com/design/LUBGZagmOrFkLjX892hEUT)
- Claude Code 지시: [`CLAUDE.md`](CLAUDE.md), [`.claude/rules/`](.claude/rules)

## 실행

```bash
docker compose up -d           # PostgreSQL 18
./gradlew build                # 서버 빌드 + 테스트
./gradlew :app-api:bootRun     # API 서버 :8080
cd web && npm install && npm run dev   # 화면 :3000
```

## 구조

```
web/                 화면 (Next.js)
app-api/ app-batch/  들어오는 어댑터 (실행 모듈)
application/         유스케이스 · 포트
domain/              핵심 규칙 (순수 자바)
infra-*/             나가는 어댑터 (DB · 결제 · 문자·이메일 · 파일)
common/              순수 유틸
build-logic/         공통 빌드 설정
```
