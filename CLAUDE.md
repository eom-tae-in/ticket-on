# ticket-on

소규모 주최자가 좌석 배치도를 직접 만들고, 관객이 원하는 자리를 골라 예매하는 웹 서비스.

## 무엇이 기준인가

- **정책·규칙**: `docs/` 기획 문서. 기능을 만들기 전에 해당 문서를 읽는다. 처음이면 `docs/README.md` → `docs/14-domain-model.md` → `docs/02-event-model.md` → `docs/07-ticket.md` 순서.
- **화면**: Figma https://www.figma.com/design/LUBGZagmOrFkLjX892hEUT (페이지: 관객 / 검표 / 주최 / 관리자, 컴포넌트·토큰 페이지 따로)
- **기술 스택·구조**: `docs/16-architecture.md`
- 문서와 Figma가 다르거나, 어디에도 없는 판단이 필요하면 **임의로 정하지 말고 사용자에게 묻는다.**

## 레포 지도

```
web/                  화면 (Next.js) — 관객 (audience) / checkin / organizer / admin
app-api/              들어오는 어댑터 · API 서버 (실행 모듈)
app-batch/            들어오는 어댑터 · 정해진 시각 작업 (실행 모듈)
application/          안쪽 · 유스케이스 — <도메인>/port/in · port/out · service
domain/               안쪽 · 엔티티 — 순수 자바
infra-persistence/    나가는 어댑터 · DB (jOOQ, Flyway)
infra-payment/        나가는 어댑터 · 토스페이먼츠
infra-message/        나가는 어댑터 · 문자·이메일
infra-storage/        나가는 어댑터 · 파일
common/               순수 유틸만
build-logic/          공통 빌드 설정 (convention 플러그인)
docs/                 기획 문서
```

## 명령

```bash
docker compose up -d                 # 로컬 PostgreSQL 18
./gradlew build                      # 서버 전체 빌드 + 테스트 (구조 규칙 테스트 포함)
./gradlew :infra-persistence:jooqCodegen   # 마이그레이션 SQL → jOOQ 코드 생성
./gradlew :app-api:bootRun           # API 서버 (http://localhost:8080, 명세 /v3/api-docs)
cd web && npm run dev                # 화면 (http://localhost:3000)
cd web && npm run api:types          # 서버 명세 → 화면 타입 (서버 실행 중일 때)
```

## 처음 열었을 때 할 일

이 골격은 **Maven 저장소에 접근할 수 없는 환경에서 만들어져 서버 빌드를 돌려 보지 못했다.** (화면 `web/`은 빌드·린트·타입 검사를 통과했다.)

1. `./gradlew build`를 돌린다.
2. 실패하면 **빌드 설정만** 고친다 (버전, 의존성 이름, 플러그인 설정). 모듈 구성·의존 방향·패키지 구조·구조 테스트는 바꾸지 않는다.
3. 통과하면 이 섹션을 지운다.

## 일하는 방식

- 문서에 적힌 기능은 전부 만든다. "MVP"나 "나중에"로 미루거나 임시로 대충 만들지 않는다.
- `docs/99-decisions.md`의 "만들지 않기로 한 것"은 의도적 제외다. 임의로 추가하지 않는다.
- 구조 규칙은 `.claude/rules/`에 있다. **구조 테스트(ArchitectureTest)가 실패하면 테스트가 아니라 코드를 고친다.**
- 비밀값(토스 키 등)은 레포에 넣지 않는다. `.env.example` 참고.
