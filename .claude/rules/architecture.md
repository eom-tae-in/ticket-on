# 서버 구조 규칙 (헥사고날 + 안쪽 클린 두 겹)

자세한 이유는 `docs/16-architecture.md`.

## 모듈 의존 — 안쪽으로만

```
domain             → common
application        → domain, common
app-api, app-batch → application, domain, common   (infra-*는 runtimeOnly)
infra-*            → application, domain, common
```

- 이 방향은 Gradle 빌드 파일로 강제된다. **빌드 파일에 반대 방향 의존을 추가하지 않는다.**
- app-*는 infra-*를 `runtimeOnly`로만 묶는다. 컨트롤러·배치 코드에서 infra 클래스를 import하지 않는다.

## 포트와 어댑터

- **포트는 전부 application이 가진다.**
  - `application/<도메인>/port/in/` — 들어오는 포트 `XxxUseCase` (interface) + 입출력 `XxxCommand`(상태 변경) / `XxxQuery`(조회) / `XxxResult`
  - `application/<도메인>/port/out/` — 나가는 포트 `XxxRepository`, `XxxPort` (**interface만**)
  - `application/<도메인>/service/` — 들어오는 포트를 구현하고 나가는 포트를 부르는 서비스. `@Transactional`은 여기.
- **들어오는 어댑터**(컨트롤러, 배치 작업, 토스 웹훅 수신)는 들어오는 포트를 **부른다.**
- **나가는 어댑터**(infra-*)는 나가는 포트를 **구현한다.**
- 경계를 넘을 때는 그 쪽의 DTO로 바꾼다. Request → Command, Result → Response. **Request/Response를 안쪽으로 넘기지 않고, 도메인 객체를 바깥으로 그대로 내보내지 않는다.**

## 도메인 패키지와 층

application과 domain 안의 패키지는 아래 층 순서를 따른다. **위 층은 아래 층을 부를 수 있고, 아래 층은 위를 부르지 않는다.**

```
⑤ admin · settlement · stats
④ eventchange
③ booking · invitation · checkin
② inventory · pricing · ticket · payment
① event
⓪ account · organizer · notification
```

- 아래 층이 위에 알려야 하면(예: 결제 승인 → 티켓 발급) 아래는 이벤트로 알리기만 하고, 위가 듣고 처리한다.
- 회차 취소처럼 여러 도메인이 한꺼번에 움직이는 흐름은 `eventchange`(④)에서 지휘한다.
- 이 규칙은 `application/src/test/.../ArchitectureTest`가 검사한다. **실패하면 테스트가 아니라 코드를 고친다.**

## 읽기와 쓰기

- 규칙이 있는 쓰기(예매·취소·변경·검표 등)는 도메인 객체를 거친다.
- 읽기 전용 화면(`stats` 현황·통계, 정산 목록)은 도메인을 거치지 않는다. port/out의 조회 포트가 바로 화면용 DTO를 돌려준다.

## 외부 업체

- 결제는 토스페이먼츠 테스트 키.
- 문자·이메일·파일 업체는 **배포 때 정한다.** 개발 중엔 "기록만"(문자·이메일), 로컬 저장(파일) 어댑터를 쓴다. 업체를 임의로 고르지 않는다.
