---
paths:
  - "app-api/**"
  - "app-batch/**"
---

# 들어오는 어댑터 규칙 (app-api, app-batch)

- 컨트롤러·배치 작업·웹훅 수신은 **들어오는 포트(XxxUseCase)를 부르기만 한다.** 규칙 계산, 저장, 외부 호출을 직접 하지 않는다.
- 컨트롤러는 Request를 Command/Query로 바꿔 유스케이스를 부르고, Result를 Response로 바꿔 돌려준다. 로그인 사용자 같은 값은 여기서 Command에 담는다.
- Request/Response는 `app-api/.../api/<도메인>/`에 둔다. 화면 타입은 이 Response에서 OpenAPI로 생성되므로 이름과 칸을 신중하게 정한다.
- infra-* 클래스를 import하지 않는다 (빌드에서 runtimeOnly라 import 자체가 안 된다).
- Flyway 마이그레이션은 app-api만 실행한다. app-batch는 `spring.flyway.enabled=false`.
- 로그인은 세션 쿠키(세션은 DB 저장) + 카카오·구글 OAuth2로 만든다 (`docs/16`). 정지·탈퇴는 세션을 즉시 끊어야 한다 (`docs/01`). JWT로 바꾸지 않는다.
