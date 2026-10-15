-- 주최자 · 주최자 신청서 · 소속 (#20)
-- 근거: docs/14 "전체 관계도"(주최자 상태 넷, 소속 이력 — 역할 · 기간), "무엇이 무엇을 가리키나"(소속 = 운영진 + 주최자 + 역할 + 기간),
--       docs/01 "주최자와 운영진", "주최자 신청과 승인", "신청 항목", "반려", docs/11 "데이터 보관"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V9__...)을 추가한다.

-- 주최자 한 줄 = 단체 하나. 사람이 아니다 — 사람은 각자 운영진 로그인(staff_account)으로 소속된다(staff_membership).
-- 상태는 넷만 저장한다(도메인 OrganizerStatus). 정지 1~3단계는 상태가 아니라 정지 기록이다(정지 이슈가 표를 만든다).
-- 새 주최자 신청을 받으면 '심사 중'으로 만들고(docs/14), 승인되면 '활동'이 된다(#23).
-- 이름은 겹쳐도 된다 — 같은 이름은 막지 않고 심사에서 사람이 본다. 값은 앞뒤 공백을 걷고 NFC로 맞춘 그대로다(도메인 OrganizerName).
-- 공개 정보(소개 · SNS 링크 · 로고)는 공개 정보 편집 이슈(#29)가 칸을 더한다. 심사 중인 주최자는 공개 정보가 비어 있다.
-- 보관: 지우지 않는다. 닫혀도 '닫힘'으로 남아 이력을 보존한다(docs/11 "데이터 보관 — 닫힌 주최자").
CREATE TABLE organizer (
    id     BIGINT      GENERATED ALWAYS AS IDENTITY,
    name   TEXT        NOT NULL,
    status VARCHAR(30) NOT NULL,
    CONSTRAINT organizer_pk PRIMARY KEY (id),
    CONSTRAINT organizer_status_ck CHECK (status IN ('UNDER_REVIEW', 'ACTIVE', 'CLOSED', 'PERMANENTLY_SUSPENDED'))
);

-- 주최자 신청서 한 줄 = 운영진 로그인을 만든 사람이 낸 신청 한 번. 심사받는 주최자를 가리킨다.
-- 재신청은 제한이 없고(docs/01 "반려"), 다시 내면 새 줄이 된다. 반려된 신청서도 사유와 함께 남는다.
-- 심사 중에는 신청한 사람이 그 주최자에 소속되지 않는다 — 소유자 소속은 승인 때 시작한다(docs/01 "승인되면 신청한 사람이 소유자가 되고").
-- 신청 항목은 docs/01 "신청 항목" 표 그대로다(도메인 OrganizerApplicationForm).
--   담당자 연락처 · 사업장 전화번호: 유선·대표번호일 수 있어 형식 없이 적은 그대로(앞뒤 공백만 걷음).
--   이메일: 통지를 받는 곳. 운영진 로그인 이메일과 같지 않아도 된다.
--   사업자 정보 다섯 칸: 사업자면 모두 채우고, 비사업자면 모두 비운다(CHECK). 사업자등록번호는 숫자만 10자리.
--   정산 계좌 세 칸(은행 · 계좌번호 · 예금주): 테스트 결제라 송금은 없지만 받고 저장한다.
--   홈페이지·SNS 링크: 선택. 여러 개라 따로 둔다(organizer_application_link).
-- 사업자 정보와 정산 계좌는 지금은 신청서에만 있다. 승인 뒤 바꾸는 일(관리자 확인)은 #31이 만든다.
-- 연락처 · 사업자 정보 · 정산 계좌는 심사(관리자)와 판매자 정보 표시에만 쓴다. 신청한 사람의 화면 응답이나 로그에 내보내지 않는다.
-- 보관: 주최자와 같다 — 심사·소유자 이전 때 관리자가 신청 때 받은 연락처로 확인한다(docs/11 "대표가 바뀔 때").
CREATE TABLE organizer_application (
    id                           BIGINT       GENERATED ALWAYS AS IDENTITY,
    organizer_id                 BIGINT       NOT NULL,
    applicant_staff_account_id   BIGINT       NOT NULL,
    status                       VARCHAR(30)  NOT NULL,
    organizer_type               VARCHAR(20)  NOT NULL,
    name                         TEXT         NOT NULL,
    contact_phone_number         TEXT         NOT NULL,
    email                        VARCHAR(254) NOT NULL,
    event_history_or_plan        TEXT         NOT NULL,
    business_status              VARCHAR(20)  NOT NULL,
    business_trade_name          TEXT,
    business_representative_name TEXT,
    business_address             TEXT,
    business_phone_number        TEXT,
    business_registration_number VARCHAR(10),
    settlement_bank_name         TEXT         NOT NULL,
    settlement_account_number    TEXT         NOT NULL,
    settlement_account_holder    TEXT         NOT NULL,
    submitted_at                 TIMESTAMPTZ  NOT NULL,
    reviewed_at                  TIMESTAMPTZ,
    rejection_reason             TEXT,
    CONSTRAINT organizer_application_pk PRIMARY KEY (id),
    CONSTRAINT organizer_application_organizer_fk FOREIGN KEY (organizer_id)
        REFERENCES organizer (id),
    CONSTRAINT organizer_application_applicant_fk FOREIGN KEY (applicant_staff_account_id)
        REFERENCES staff_account (id),
    CONSTRAINT organizer_application_status_ck CHECK (status IN ('UNDER_REVIEW', 'APPROVED', 'REJECTED')),
    CONSTRAINT organizer_application_organizer_type_ck CHECK (organizer_type IN ('INDIVIDUAL', 'GROUP')),
    CONSTRAINT organizer_application_business_status_ck CHECK (business_status IN ('BUSINESS', 'NON_BUSINESS')),
    CONSTRAINT organizer_application_business_information_ck CHECK (
        (business_status = 'BUSINESS'
            AND business_trade_name IS NOT NULL
            AND business_representative_name IS NOT NULL
            AND business_address IS NOT NULL
            AND business_phone_number IS NOT NULL
            AND business_registration_number IS NOT NULL)
        OR (business_status = 'NON_BUSINESS'
            AND business_trade_name IS NULL
            AND business_representative_name IS NULL
            AND business_address IS NULL
            AND business_phone_number IS NULL
            AND business_registration_number IS NULL)),
    -- 심사 중이면 처리 일시·사유가 없고, 승인이면 처리 일시만, 반려면 처리 일시와 사유가 있다(도메인 OrganizerApplication)
    CONSTRAINT organizer_application_review_ck CHECK (
        (status = 'UNDER_REVIEW' AND reviewed_at IS NULL AND rejection_reason IS NULL)
        OR (status = 'APPROVED' AND reviewed_at IS NOT NULL AND rejection_reason IS NULL)
        OR (status = 'REJECTED' AND reviewed_at IS NOT NULL AND rejection_reason IS NOT NULL))
);

-- 심사 중인 신청은 한 사람에게 하나뿐이다 — 승인 전까지 로그인하면 '심사 중' 화면만 보인다(docs/01 "주최자 신청과 승인").
-- 새 신청을 저장하기 전에 유스케이스가 확인하므로 보통은 걸리지 않는다. 같은 사람이 거의 동시에 두 번 내도 이 색인이 하나만 남긴다.
CREATE UNIQUE INDEX organizer_application_applicant_under_review_ux
    ON organizer_application (applicant_staff_account_id)
    WHERE status = 'UNDER_REVIEW';

-- 내 주최 홈에서 그 사람의 가장 최근 신청을 찾는다.
CREATE INDEX organizer_application_applicant_ix ON organizer_application (applicant_staff_account_id, submitted_at);

CREATE INDEX organizer_application_organizer_ix ON organizer_application (organizer_id);

-- 신청서의 홈페이지·SNS 링크 — 실재 확인의 핵심(docs/01 "신청 항목"). 선택이라 없을 수 있다.
-- 적은 순서(display_order, 0부터)대로 심사 화면에 보인다. 값은 http(s) 절대 주소다(도메인 ExternalLink).
-- 주최자 공개 정보의 SNS 링크와는 별개다(docs/01 "공개 페이지 — 편집").
CREATE TABLE organizer_application_link (
    organizer_application_id BIGINT  NOT NULL,
    display_order            INTEGER NOT NULL,
    url                      TEXT    NOT NULL,
    CONSTRAINT organizer_application_link_pk PRIMARY KEY (organizer_application_id, display_order),
    CONSTRAINT organizer_application_link_application_fk FOREIGN KEY (organizer_application_id)
        REFERENCES organizer_application (id),
    CONSTRAINT organizer_application_link_display_order_ck CHECK (display_order >= 0)
);

-- 소속 한 줄 = 어느 사람(운영진 로그인)이 어느 주최자에 어떤 역할로 언제부터 언제까지 있었는지(도메인 StaffMembership).
-- 나가도 지우지 않고 끝난 일시(ended_at)를 채워 이력으로 남긴다 — 운영진 프로필의 "참여한 주최자"와 관리자 판단에 쓰인다
-- (docs/11 "데이터 보관 — 운영진 소속 이력"). 역할이 바뀌면(소유자 넘기기) 그때까지의 줄을 끝내고 새 줄을 같은 시각에 시작한다.
-- 한 사람이 여러 주최자에 소속될 수 있다. 소속 줄은 승인(#23)과 운영자 초대 수락(#25)이 만든다 — 심사 중에는 줄이 없다.
CREATE TABLE staff_membership (
    id               BIGINT      GENERATED ALWAYS AS IDENTITY,
    organizer_id     BIGINT      NOT NULL,
    staff_account_id BIGINT      NOT NULL,
    role             VARCHAR(20) NOT NULL,
    started_at       TIMESTAMPTZ NOT NULL,
    ended_at         TIMESTAMPTZ,
    CONSTRAINT staff_membership_pk PRIMARY KEY (id),
    CONSTRAINT staff_membership_organizer_fk FOREIGN KEY (organizer_id)
        REFERENCES organizer (id),
    CONSTRAINT staff_membership_staff_account_fk FOREIGN KEY (staff_account_id)
        REFERENCES staff_account (id),
    CONSTRAINT staff_membership_role_ck CHECK (role IN ('OWNER', 'OPERATOR')),
    CONSTRAINT staff_membership_period_ck CHECK (ended_at IS NULL OR ended_at >= started_at)
);

-- 한 사람은 한 주최자에 지금 소속이 하나뿐이다(한 역할로만). 주최자의 지금 운영진을 찾는 색인도 겸한다.
CREATE UNIQUE INDEX staff_membership_active_ux
    ON staff_membership (organizer_id, staff_account_id)
    WHERE ended_at IS NULL;

-- 주최자의 지금 소유자는 많아야 한 명이다 — 배상과 정산 계좌의 책임질 사람이 분명해야 한다(docs/01, docs/99 B "소유자 여러 명").
CREATE UNIQUE INDEX staff_membership_active_owner_ux
    ON staff_membership (organizer_id)
    WHERE ended_at IS NULL AND role = 'OWNER';

-- 한 사람의 소속(지난 소속 포함)을 찾는다 — 주최자 전환, 운영진 프로필.
CREATE INDEX staff_membership_staff_account_ix ON staff_membership (staff_account_id);
