# 16. 기술 스택과 구조

**구현 착수 전에 정한 기술 결정**입니다. 기능 규칙은 `00`~`15`에 있고, 여기는 그 규칙을 어떤 구조에 담을지를 정합니다.

## 한눈에

| 항목 | 선택 | 이유 |
|---|---|---|
| 서버 형태 | **모듈형 모놀리스** (서버 하나, 안을 모듈로 나눔) | 아래 "고르지 않은 것" 참조 |
| 언어 | **Java 25** (최신 LTS) | 헥사고날·jOOQ가 이미 새로 익힐 것 둘. 언어는 익숙한 쪽 |
| 프레임워크 | Spring Boot 4 | Java 17 이상, 최신 LTS 권장 |
| 아키텍처 | **헥사고날 + 안쪽은 클린 두 겹** (domain / application) | 바깥(DB·토스·문자·이메일·파일·배치)이 많고, 규칙이 무거움 |
| DB 접근 | **jOOQ** | 좌석 동시성·집계가 전부 SQL에 있음. 도메인을 순수하게 둔 구조와 맞음 |
| DB | PostgreSQL | 잠금·집계에 강하고 jOOQ 무료판(Apache 2.0)이 지원 |
| 표 변경 이력 | Flyway | 이 이력으로 만든 표에서 jOOQ 코드를 생성 |
| 로그인 | Spring Security + **세션 쿠키**(세션은 DB에 저장), 카카오·구글은 OAuth2 | 정지·탈퇴는 즉시 끊겨야 함 (`01`) |
| 정해진 시각 작업 | Spring 스케줄러 | 작업이 가볍고 단순함 |
| 화면 | Next.js + TypeScript | 행사·주최자 링크의 카톡 미리보기는 서버에서 그려야 뜸 |
| 화면 ↔ 서버 | springdoc(OpenAPI 명세 생성) → openapi-typescript(화면 타입 생성) | 서버가 바뀌면 화면 빌드에서 걸림 |
| 테스트 | JUnit 5 + ArchUnit + Testcontainers | 동시성은 진짜 PostgreSQL로만 검증 가능. 구조 규칙은 테스트로 검사 |
| 빌드 | Gradle 멀티모듈 + build-logic | 모듈 경계를 빌드가 막아 줌 |
| 결제 | 토스페이먼츠 테스트 키 | 승인·취소·실패 흐름 구현에 지금 필요 (`00`) |
| 문자 · 이메일 · 파일 | **배포 때 정함** | 포트로 분리돼 있어 어댑터만 추가하면 됨. 개발 중엔 "기록만"·로컬 저장 |
| 배포 | 마지막에 정함 | |

버전은 프로젝트를 만드는 시점의 안정 버전을 씁니다.

---

## 레포 구조

```
ticket-on/
├─ web/                    화면 (Next.js) — 사용자별: audience / checkin / organizer / admin
│
├─ app-api/                [들어오는 어댑터] 컨트롤러, Request/Response, 토스 웹훅 수신, 환경별 설정
├─ app-batch/              [들어오는 어댑터] 정해진 시각 작업
│
├─ application/            [안쪽 · 유스케이스] <도메인>/port/in · port/out · service
├─ domain/                 [안쪽 · 엔티티] 순수 자바. Spring·jOOQ 없음
│
├─ infra-persistence/      [나가는 어댑터] jOOQ, 리포지토리 구현, Flyway 마이그레이션, 읽기 전용 조회
├─ infra-payment/          [나가는 어댑터] 토스페이먼츠
├─ infra-message/          [나가는 어댑터] 문자·이메일 (실제 발송 / 기록만)
├─ infra-storage/          [나가는 어댑터] 포스터·로고 파일
│
├─ common/                 순수 유틸만 — 시각·영업일 계산. 설정은 넣지 않음
├─ build-logic/            공통 빌드 설정
├─ docs/                   기획 문서
└─ CLAUDE.md
```

### 모듈 의존 — 안쪽으로만

```
domain             → 없음
application        → domain
app-api, app-batch → application, domain   (infra-*는 실행할 때만 묶음: runtimeOnly)
infra-*            → application, domain
```

**빌드가 막아 줍니다.** domain 빌드 파일에는 Spring도 jOOQ도 넣지 않으므로, 실수로 import하면 빌드가 실패합니다.

### 포트와 어댑터

| | 들어오는 쪽 | 나가는 쪽 |
|---|---|---|
| 포트 (안쪽 소유) | `CancelTicketUseCase` — 앱이 **제공하는** 기능 | `TicketRepository`, `RefundPort` — 앱이 **필요한** 기능 |
| 구현 | 서비스 (안쪽) | 어댑터 (바깥) |
| 부름 | 어댑터 (컨트롤러·배치·웹훅) | 서비스 |

- **들어오는 포트도 둡니다.** 처음 쓰는 구조라 경계를 눈에 보이게 하고, Claude Code가 일관되게 따르게 하려는 것입니다.
- **포트의 입력·출력은 Command / Result**(안쪽 DTO)로 정합니다. 컨트롤러의 Request / Response를 안쪽으로 넘기지 않습니다. 상태를 바꾸는 입력은 Command, 조회 입력은 Query.

### 예시 — 티켓 취소 하나

```
app-api/ticket/            TicketController, CancelTicketRequest, CancelTicketResponse
application/ticket/
  port/in/                 CancelTicketUseCase, CancelTicketCommand, CancelTicketResult
  port/out/                TicketRepository, RefundPort, NotificationPort
  service/                 CancelTicketService
domain/ticket/             Ticket, RefundPolicy, Refund, CancelReason
infra-persistence/ticket/  TicketPersistenceAdapter (jOOQ ↔ Ticket 변환)
infra-payment/             TossRefundAdapter
infra-message/             SmsNotificationAdapter, LogOnlyNotificationAdapter
```

흐름: `TicketController` → `CancelTicketUseCase` → `CancelTicketService` → `Ticket.cancel()`(공제 계산) → `TicketRepository` · `RefundPort` → 각 어댑터.

---

## 도메인 패키지와 순서

application과 domain 안은 도메인별 패키지로 나눕니다. 처음 14개로 잡았던 "현황·정산(report)"은 **settlement(정산)와 stats(현황·통계)로 나눴습니다** — 정산은 규칙(영업일 7일, 마이너스 정산, 미수금)이 있어 도메인이 필요하고 통계는 읽기 전용이라 성격이 다르며, report라는 이름이 "신고"(admin)와 헷갈립니다. **위 층은 아래 층을 부를 수 있고, 아래는 위를 부르지 않습니다.** ArchUnit 테스트로 검사합니다.

```
⑤ admin(관리자) · settlement(정산) · stats(현황·통계)   여러 도메인을 읽고 부름
④ eventchange(행사 변경·취소)                   회차 취소 같은 큰 흐름을 한곳에서 지휘
③ booking(예매) · invitation(초대권) · checkin(검표)
② inventory(재고) · pricing(가격) · ticket(티켓) · payment(결제)
① event(행사: 도면·회차)
⓪ account(계정) · organizer(주최자) · notification(알림)
```

| 패키지 | 맡는 것 | 문서 |
|---|---|---|
| account | 로그인(관객·운영진·관리자·검표), 가입·탈퇴, 비밀번호 찾기, 관객 제재 | `01` |
| organizer | 단체, 소유자·운영자 소속, 심사 신청, 공개 정보(로고), 사업자·정산 계좌, 주최자 정지 | `01` `11` |
| notification | 이메일·문자 발송, 알림함, 수신 설정 | `12` |
| event | 행사 정보·위저드, 도면, 날짜·회차·출연, 변경 이력, 상태 자동 전환, 공개 목록·검색 | `02` `03` `04` `13` |
| inventory | 회차별 좌석 판매 상태·잔여, 판매 제외, 관계자 확보분 | `02` `14` |
| pricing | 기본가·회차 조정, 묶음권 상품, 조기예매 몫, 쿠폰 | `05` |
| ticket | 티켓 번호, QR 열쇠·재발행, 공유 링크·무효 사유 | `07` |
| payment | 결제·환불 내역 | `06` `08` |
| booking | 선점·타이머, 예매 건, 관객 취소·변경(공제 계산) | `06` `08` |
| invitation | 발권·연석 추천, 단체 링크, 회수 | `09` |
| checkin | 검표 계정의 행사·입구 배정, 스캔 판정, 수동 검색, 입장 기록(30일 후 삭제), 티켓 없는 입장 | `10` |
| eventchange | 출연자 변경·연기·장소 변경·회차/행사 취소·공연 중단 → 환불·배상·알림 | `04` `08` |
| settlement | 정산 금액·예정일(영업일 7일)·미수금 | `11` |
| stats | 예매 현황·오늘 판매·연령대, 입장 현황 (읽기 전용 — domain 패키지 없음) | `11` |
| admin | 심사, 신고, 정지 단계 실행, 예매 찾기·대신 취소, 로고 초기화 | `01` `13` |

**재고를 따로 둔 이유** — 관객 선점, 초대권 발권, 주최자의 판매 제외가 모두 같은 좌석을 잡습니다. 예매 안에 두면 초대권과 행사 설정이 예매 속을 건드리게 됩니다.

**아래에서 위로 알려야 할 때** (결제 승인 → 티켓 발급)는 아래가 "승인됐다"고 알리기만 하고 위가 듣고 처리합니다. 아래가 위를 직접 부르지 않습니다.

**읽기 전용 화면은 도메인을 거치지 않습니다.** stats의 현황·통계와 정산 목록은 규칙이 없으므로 infra-persistence에서 바로 화면용 DTO로 꺼냅니다. 도메인 ↔ 표 매핑은 규칙이 있는 쓰기(예매·취소·변경·검표)에만 씁니다.

---

## 고르지 않은 것

| 고르지 않은 것 | 이유 |
|---|---|
| **마이크로서비스** | 예매는 선점 → 예매 건 → 결제 → 티켓 → 알림이 함께 성공하거나 함께 되돌려져야 하고(`06`), 회차 취소는 환불·배상·초대권·알림·정산이 한꺼번에 움직임(`04`). 서버를 나누면 되돌리는 처리를 경우마다 직접 만들어야 함. 이용자 1만 명 이하라 따로 늘릴 기능도 없고, 1인 개발이라 배포·장애 대응만 늘어남 |
| **JPA** | 도메인과 겸용하면 어노테이션과 JPA 요구 사항(빈 생성자, 프록시, 열린 값)이 도메인에 남음. 분리하면 변경 감지·지연 로딩 같은 이점이 사라짐 |
| **Spring Data JDBC · MyBatis** | 앞은 복잡한 조회·집계에 약하고, 뒤는 SQL이 문자열이라 칸 이름 오류를 실행해야 앎 |
| **DB를 domain 모듈에 두는 실용안** | 규칙이 무거워(공제율 `08`, 조기예매 몫 `14`, 티켓 상태 계산 `14`, 검표 판정 `14`) DB 없이 빠르게 테스트할 가치가 큼 |
| **Redis** | 1만 명 이하. 선점은 DB 조건부 갱신으로 충분 |
| **JWT 로그인** | 이미 발급한 토큰을 바로 끊기 어려움. 정지·탈퇴는 즉시 반영돼야 함 |
| **Spring Batch** | 대용량 처리용. 이 서비스의 예약 작업은 가벼움 |
| **TypeScript 서버 + 공용 패키지** | 서버를 Spring으로 정해 공용 TS 패키지는 쓸 수 없음. OpenAPI 명세로 대신함 |
| **도메인마다 모듈 하나씩 (15개)** | 빌드 설정만 늘어남. 패키지 + ArchUnit 검사로 같은 효과 |
