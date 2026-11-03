-- 변경 이력의 대상 칸에 상세 안내 · 지도 링크 · 공개용 연락처를 더한다 (#231)
-- 근거: docs/03 "상세 안내 · 지도 링크 · 공개용 연락처도 이력을 남깁니다", docs/04 "예매가 열린 뒤(판매 중) 수정 범위"(자유 변경, 이력 보관),
--       docs/99 C "보관 기간"(행사 종료 후 6개월), D "변경 이력 대상"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V28__...)을 추가한다.
--
-- 대상 칸(target)에 상세 안내(VENUE_DETAILS) · 지도 링크(MAP_LINK) · 공개용 연락처(PUBLIC_CONTACT)를 더한다. 값은 도메인 enum 이름 그대로다.
--   셋 다 행사에 하나인 칸이라 회차를 가리키지 않는다(event_change_history_session_ck는 회차 출연만 회차를 가리키게 이미 막는다). 지금 버전이
--   하나뿐인 것도 행사에 하나인 칸의 유일 색인(event_change_history_current_event_target_ux)이 그대로 맡는다.
-- 내용: 상세 안내는 글 그대로, 지도 링크는 주소, 공개용 연락처는 적은 그대로다. 셋 다 선택 칸이라 비울 수 있다 — 비어 있던 칸을 채우면
--   previous_content가, 비우면 new_content가 NULL이다. 제목 · 소개 · 포스터는 여전히 비울 수 없다. 판매를 시작한 순간 비어 있는 칸은 첫 버전을
--   쌓지 않는다(도메인 EventChangeHistory#firstVersions, 확인 필요 #223).
-- 보관 · 지우기 · 쌓는 흐름 · 잠금은 V26 그대로다 — 행사가 끝난 날부터 6개월이 지나면 정해진 시각 작업이 그 행사의 줄을 모두 지운다.
--
-- CHECK와 NULL: target은 NOT NULL이라 IN 식이 NULL이 되지 않는다. new_content는 NULL이 될 수 있어 IS NOT NULL로 가른다 — 대상 칸 IN 식과 OR로
--   묶어도 식 전체가 NULL이 되지 않는다(V26 머리 주석과 같다). 지금 들어 있는 줄은 모두 옛 네 칸이라 새 CHECK에 어긋나는 줄은 없다.
ALTER TABLE event_change_history DROP CONSTRAINT event_change_history_target_ck;

ALTER TABLE event_change_history ADD CONSTRAINT event_change_history_target_ck CHECK (
    target IN ('TITLE', 'INTRODUCTION', 'POSTER', 'VENUE_DETAILS', 'MAP_LINK', 'PUBLIC_CONTACT', 'SESSION_PERFORMERS'));

-- 제목 · 소개 · 포스터는 비울 수 없다. 선택 칸(상세 안내 · 지도 링크 · 공개용 연락처 · 회차 출연)만 비운 버전이 있다
ALTER TABLE event_change_history DROP CONSTRAINT event_change_history_new_content_ck;

ALTER TABLE event_change_history ADD CONSTRAINT event_change_history_new_content_ck CHECK (
    target IN ('VENUE_DETAILS', 'MAP_LINK', 'PUBLIC_CONTACT', 'SESSION_PERFORMERS') OR new_content IS NOT NULL);
