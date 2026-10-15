-- 주최자 심사 · 관리자 처리 목록 (#23)
-- 근거: docs/01 "주최자 신청과 승인", "심사 화면", "반려", "닫힌 주최자를 다시 열 때", docs/13 "5. 플랫폼 관리자 — 처리 목록",
--       docs/12 "관리자 대상", docs/99 B "닫힌 주최자 이름으로 새 주최자 신청"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V11__...)을 추가한다.
--
-- 표는 새로 만들지 않는다. 심사는 V8의 표에 쓴다.
--   승인: organizer_application을 'APPROVED'로, organizer를 'ACTIVE'로 바꾸고, 신청한 사람의 소유자 소속 한 줄을 staff_membership에 넣는다.
--   반려: organizer_application을 'REJECTED'로 바꾸고 사유를 남긴다. organizer는 'UNDER_REVIEW'로 남는다 — 주최자 상태는 넷뿐이라
--         '반려' 상태를 두지 않고(docs/14), 반려된 신청서가 사유와 함께 그 주최자를 가리키므로 지우지 않는다. 소속도 행사도 없어
--         공개·운영진 연락처·처리 목록에 나오지 않는다. 다시 신청하면 새 주최자와 새 신청서가 된다.
--   두 관리자가 같은 신청을 거의 동시에 처리하면 신청서를 "아직 'UNDER_REVIEW'일 때만" 바꾸는 조건부 갱신이 하나만 통과시킨다.
--   누가 승인·반려했는지는 관리자 조작 기록(admin_operation_record — V9)에 남는다.

-- 처리 목록은 심사 중인 주최자 신청을 접수(제출) 순서로 모은다. 처리한 신청은 목록에서 빠지므로 심사 중인 줄만 색인한다.
CREATE INDEX organizer_application_under_review_ix
    ON organizer_application (submitted_at, id)
    WHERE status = 'UNDER_REVIEW';

-- 새 신청의 심사 화면에 같은 소유자 또는 같은 단체명의 닫힌 주최자를 함께 보인다(이력 세탁 방지). 같은 소유자는 소속 표의 사람 색인
-- (staff_membership_staff_account_ix — V8)으로 찾는다. 이름(TEXT) 색인은 jOOQ 코드 생성(DDLDatabase)이 만들지 못해 두지 않는다 —
-- 닫힌 주최자는 많지 않아 상태로 거른 뒤 이름을 비교한다.
