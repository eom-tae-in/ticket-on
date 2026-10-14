---
paths:
  - "infra-persistence/**"
---

# infra-persistence 모듈 규칙

## 마이그레이션 (Flyway)

- 위치: `infra-persistence/src/main/resources/db/migration/`, 이름: `V<번호>__<설명>.sql`
- **이미 적용된 마이그레이션 파일은 고치지 않는다.** 바꿀 땐 새 번호의 파일을 추가한다.
- 표·칸 이름은 소문자 snake_case.
- jOOQ 코드는 이 SQL에서 자동 생성된다(`./gradlew :infra-persistence:jooqCodegen`, 패키지 `com.ticketon.persistence.jooq`). 생성된 코드는 손으로 고치지 않는다.
- jOOQ의 SQL 파서가 이해하지 못하는 PostgreSQL 전용 문법이 필요하면, 그 부분만 jOOQ 무시 주석으로 감싸거나 Testcontainers 기반 코드 생성으로 바꾸는 것을 사용자와 상의한다.

## 어댑터

- 도메인별 하위 패키지에 `XxxPersistenceAdapter`를 두고 application의 port/out을 구현한다.
- jOOQ 레코드 ↔ 도메인 객체 변환은 이 모듈 안에서만 한다. jOOQ 타입을 포트 밖으로 내보내지 않는다.
- 읽기 전용 조회(현황·통계·정산 목록)는 도메인 객체를 만들지 않고 바로 조회용 DTO(port/out에 정의)를 돌려준다.

## 동시성 — SQL에 드러나게

- 좌석 선점은 조건부 갱신으로 한다: `UPDATE … WHERE 판매 상태 = 빈자리`. 바뀐 줄 수가 기대보다 적으면 "방금 다른 분이 선택했습니다"(`docs/06`)로 처리한다.
- 조기예매 몫처럼 한도가 있는 숫자는 `사용 수 + n ≤ 한도`일 때만 늘리는 한 문장으로 갱신한다(`docs/14` — 선점 순간 누적, 돌려주지 않음).
- 동시성 테스트는 진짜 PostgreSQL(Testcontainers)로 한다. 가짜 DB로는 잠금 동작을 검증할 수 없다.
