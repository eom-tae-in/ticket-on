-- 주최자 공개 정보 — 소개 · SNS 링크 · 로고 (#29)
-- 근거: docs/01 "공개 페이지 — 주최자 페이지"(편집: 소개 · SNS 링크 · 로고는 운영진 누구나, 선택 입력, 심사용 SNS 링크와 별개 / 로고: 선택,
--       정사각형, 없으면 이름 첫 글자 + 정해진 색), docs/14 "전체 관계도"(주최자 — 공개 정보: 소개 · SNS · 로고(선택)),
--       docs/99 D "주최자 로고", docs/11 "데이터 보관 — 닫힌 주최자"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V17__...)을 추가한다.
--
-- 공개 정보는 주최자 페이지에 로그인 없이 누구나 본다. 셋 다 선택이라 비어 있을 수 있다(심사 중인 주최자는 비어 있다).
-- 소개: 앞뒤 공백을 걷은 그대로(도메인 OrganizerPublicProfile). 비운 소개는 빈 글자가 아니라 NULL로 둔다. 길이 상한은 문서에 없어 두지 않는다
--   (신청서의 글 칸과 같다).
-- 로고: 파일 자체는 파일 저장소(infra-storage)에 있고 여기에는 그 파일을 가리키는 열쇠만 둔다(도메인 OrganizerLogo). 열쇠는 추측할 수 없는
--   값 32자 + "." + 그림 형식의 확장자(png · jpg)다 — 누구나 여는 공개 주소에 그대로 들어간다. 로고가 없으면 NULL이고, 그때 화면은 이름 첫
--   글자 + 주최자 id로 정해진 색을 보인다(저장하지 않고 계산한다). 열쇠는 주최자마다 따로 붙으므로 겹치지 않는다(UNIQUE) — 바꾸거나 지운 로고의
--   파일을 지울 때 다른 주최자의 로고가 함께 지워지지 않는다.
-- 보관: 주최자와 같다. 닫혀도 지우지 않는다 — 주최자 페이지는 "활동 종료" 배지와 함께 그대로 남는다(docs/01 "탈퇴와 주최자 닫기").
ALTER TABLE organizer ADD COLUMN introduction TEXT;
ALTER TABLE organizer ADD COLUMN logo_file_key VARCHAR(40);

-- 비운 소개는 NULL이다 — 빈 글자로 저장하지 않는다. TEXT 칸이라 글자 수로 본다(jOOQ 코드 생성이 표를 흉내 내는 H2는 TEXT와 '' 비교를 풀지 못한다).
ALTER TABLE organizer ADD CONSTRAINT organizer_introduction_not_empty_ck CHECK (char_length(introduction) > 0);

ALTER TABLE organizer ADD CONSTRAINT organizer_logo_file_key_uk UNIQUE (logo_file_key);

-- 주최자 공개 정보의 SNS 링크 — 주최자 페이지에 적은 순서(display_order, 0부터)대로 보인다. 선택이라 없을 수 있고, 개수 상한은 문서에 없어
-- 두지 않는다. 값은 http(s) 절대 주소다(도메인 ExternalLink — 공개 페이지에서 누구나 누르는 링크라 javascript: 같은 주소를 받지 않는다).
-- 심사용으로 신청서에 낸 홈페이지·SNS 링크(organizer_application_link)와는 별개다(docs/01 "공개 페이지 — 편집").
-- 고칠 때는 그 주최자의 줄을 모두 지우고 새로 넣는다.
CREATE TABLE organizer_sns_link (
    organizer_id  BIGINT  NOT NULL,
    display_order INTEGER NOT NULL,
    url           TEXT    NOT NULL,
    CONSTRAINT organizer_sns_link_pk PRIMARY KEY (organizer_id, display_order),
    CONSTRAINT organizer_sns_link_organizer_fk FOREIGN KEY (organizer_id)
        REFERENCES organizer (id),
    CONSTRAINT organizer_sns_link_display_order_ck CHECK (display_order >= 0)
);
