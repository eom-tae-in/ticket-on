---
paths:
  - "web/**"
---

# 화면(web) 규칙

- `web/AGENTS.md`를 먼저 읽는다. 설치된 Next.js 버전은 학습 데이터와 다를 수 있다 — `web/node_modules/next/dist/docs/`의 문서를 기준으로 쓴다.
- 영역은 **사용하는 사람별**로 나눈다 (`docs/13-ia.md`, Figma 페이지와 같음):
  - `src/app/(audience)/` 관객 — 모바일 우선, URL에 영역 이름 없음
  - `src/app/checkin/` 검표 — 모바일·태블릿
  - `src/app/organizer/` 주최 — 데스크톱 우선, 현황은 모바일도
  - `src/app/admin/` 관리자
- 화면 모양은 Figma가 기준이다. 색·글꼴·간격은 Figma 변수(토큰)를 그대로 옮기고, 값을 임의로 만들지 않는다.
- 서버 데이터 타입은 `src/shared/api/schema.d.ts`(OpenAPI에서 자동 생성)만 쓴다. 손으로 타입을 따로 만들지 않는다.
- 행사·주최자 페이지는 카톡 링크 미리보기(제목·포스터)가 떠야 하므로 서버에서 그린다.
- **스타일링 방식과 화면 폴더 세부 구조는 아직 정하지 않았다.** 첫 화면을 만들기 전에 사용자에게 묻는다.
