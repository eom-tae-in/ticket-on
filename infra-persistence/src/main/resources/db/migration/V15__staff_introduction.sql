-- 운영진 한 줄 소개 — 운영진 프로필 (#28)
-- 근거: docs/01 "공개 페이지 — 운영진 프로필"(보이는 것: 닉네임 · 한 줄 소개 · 참여한 주최자), "수집 정보 — 주최자 운영진",
--       docs/13 "내 프로필 — 닉네임 · 한 줄 소개 편집", docs/14 "전체 관계도"(사람: 닉네임 · 한 줄 소개, 운영진만),
--       docs/11 "데이터 보관 — 회원 탈퇴 시"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V16__...)을 추가한다.
--
-- 한 줄 소개는 사람(운영진 로그인)에 붙는다 — 닉네임과 함께 운영진 프로필에 로그인 없이 누구나 보는 값이다. 선택이라 비어 있을 수 있고(NULL),
-- 가입할 때는 받지 않는다(docs/01 "이름 · 닉네임 · 이메일 · 비밀번호만"). 비운 소개는 빈 글자가 아니라 NULL로 둔다(도메인 StaffIntroduction).
-- 값은 앞뒤 공백을 걷고 NFC로 맞춘 그대로다. 길이는 50자까지다(ProposedPolicy.STAFF_INTRODUCTION_MAXIMUM_LENGTH, 확인 필요 #28 — 문서에 없어
-- 제안값). PostgreSQL VARCHAR(n)은 글자 수로 세므로 도메인의 글자 수(NFC로 맞춘 뒤 code point)와 같다.
-- 운영진 프로필에 나가지 않는 이름 · 이메일과 달리 공개되는 값이다. 부적절하면 관리자가 초기화한다(#130).
ALTER TABLE staff_account ADD COLUMN introduction VARCHAR(50);

-- 비운 소개는 NULL이다 — 빈 글자로 저장하지 않는다.
ALTER TABLE staff_account ADD CONSTRAINT staff_account_introduction_not_empty_ck CHECK (introduction <> '');

-- 탈퇴하면 소개도 파기한다 — 회원 탈퇴 시 법정 보존 대상 외 즉시 파기(docs/11). 닉네임만 남는다(V13).
ALTER TABLE staff_account ADD CONSTRAINT staff_account_withdrawn_introduction_ck
    CHECK (status <> 'WITHDRAWN' OR introduction IS NULL);
