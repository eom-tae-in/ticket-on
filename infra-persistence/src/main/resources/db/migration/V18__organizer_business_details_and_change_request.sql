-- 주최자의 사업자 정보 · 정산 계좌와 변경 요청 (#31)
-- 근거: docs/01 "신청 항목"(정산 계좌 — 승인 후 바꾸면 관리자 확인 뒤 적용), "가로채기를 막는 장치", "통신판매중개자 의무 — 2. 판매자 정보"
--       (사업자 정보 수정은 관리자 확인 뒤 적용), "관리자 2등급"(정산 계좌 · 사업자 정보 변경 확인), docs/11 "구조"(주최자 정보 · 사업자 정보 ·
--       정산 계좌 — 소유자만), "정산 계좌 변경", docs/12 "주최자 대상 — 정산 계좌 변경 확인 결과 (소유자에게)", "관리자 대상 — 정산 계좌
--       변경 확인", docs/13 "처리 목록 — 정산 계좌 · 사업자 정보 변경 확인"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V19__...)을 추가한다.

-- 주최자의 지금 사업자 여부 · 사업자 정보 · 정산 계좌(도메인 OrganizerBusinessDetails).
-- 지금까지는 신청서(organizer_application — V8)에만 있었다. 신청서는 심사받은 그때의 값이라 승인 뒤 관리자가 확인한 변경을 담을 곳이 없어,
-- 주최자 표로 옮긴다. 행사 페이지 "판매자 정보"와 정산은 이 칸을 읽는다.
--   승인할 때 그 신청서의 값을 옮겨 적고(주최자 신청 승인 — V10의 흐름), 그 뒤로는 관리자가 승인한 변경 요청(아래 표)만 이 칸을 고친다.
--   심사 중인 주최자(반려돼 남은 주최자 포함)는 비어 있다 — 신청서가 그 값을 갖는다.
--   칸의 모양은 신청서와 같다: 사업자면 사업자 정보 다섯 칸을 모두 채우고, 비사업자면 모두 비운다. 사업자등록번호는 숫자만 10자리.
--   정산 계좌 세 칸은 셋 다 있거나 셋 다 없다. 사업자 여부와 정산 계좌는 함께 채워진다.
-- 민감한 값이라 소유자 화면에는 계좌번호를 가려 내보내고, 로그 · 조작 기록 요약에는 싣지 않는다. 관리자 확인 화면은 확인에 전체가 필요해
-- 그대로 보인다(주최자 심사 화면과 같다).
ALTER TABLE organizer ADD COLUMN business_status VARCHAR(20);
ALTER TABLE organizer ADD COLUMN business_trade_name TEXT;
ALTER TABLE organizer ADD COLUMN business_representative_name TEXT;
ALTER TABLE organizer ADD COLUMN business_address TEXT;
ALTER TABLE organizer ADD COLUMN business_phone_number TEXT;
ALTER TABLE organizer ADD COLUMN business_registration_number VARCHAR(10);
ALTER TABLE organizer ADD COLUMN settlement_bank_name TEXT;
ALTER TABLE organizer ADD COLUMN settlement_account_number TEXT;
ALTER TABLE organizer ADD COLUMN settlement_account_holder TEXT;

-- 이미 승인된 주최자는 승인된 신청서의 값으로 채운다. 주최자마다 승인된 신청서는 지금 하나뿐이지만(다시 열기는 아직 없다), 여럿이면 가장 늦게
-- 승인된 것을 쓴다. 승인된 신청서가 없는 주최자(심사 중 · 반려)는 비워 둔다.
UPDATE organizer
SET (business_status, business_trade_name, business_representative_name, business_address, business_phone_number,
     business_registration_number, settlement_bank_name, settlement_account_number, settlement_account_holder) = (
        SELECT application.business_status, application.business_trade_name, application.business_representative_name,
               application.business_address, application.business_phone_number, application.business_registration_number,
               application.settlement_bank_name, application.settlement_account_number, application.settlement_account_holder
        FROM organizer_application application
        WHERE application.organizer_id = organizer.id
          AND application.status = 'APPROVED'
        ORDER BY application.reviewed_at DESC, application.id DESC
        LIMIT 1)
WHERE EXISTS (
    SELECT 1
    FROM organizer_application application
    WHERE application.organizer_id = organizer.id
      AND application.status = 'APPROVED');

ALTER TABLE organizer ADD CONSTRAINT organizer_business_status_ck CHECK (business_status IN ('BUSINESS', 'NON_BUSINESS'));

ALTER TABLE organizer ADD CONSTRAINT organizer_business_information_ck CHECK (
    (business_status = 'BUSINESS'
        AND business_trade_name IS NOT NULL
        AND business_representative_name IS NOT NULL
        AND business_address IS NOT NULL
        AND business_phone_number IS NOT NULL
        AND business_registration_number IS NOT NULL)
    OR ((business_status IS NULL OR business_status = 'NON_BUSINESS')
        AND business_trade_name IS NULL
        AND business_representative_name IS NULL
        AND business_address IS NULL
        AND business_phone_number IS NULL
        AND business_registration_number IS NULL));

ALTER TABLE organizer ADD CONSTRAINT organizer_settlement_account_ck CHECK (
    (business_status IS NOT NULL
        AND settlement_bank_name IS NOT NULL
        AND settlement_account_number IS NOT NULL
        AND settlement_account_holder IS NOT NULL)
    OR (business_status IS NULL
        AND settlement_bank_name IS NULL
        AND settlement_account_number IS NULL
        AND settlement_account_holder IS NULL));

-- 정산 계좌 · 사업자 정보 변경 요청 한 줄 = 소유자가 낸 요청 한 번(도메인 OrganizerChangeRequest).
-- 어느 주최자의 · 무엇을(kind) · 무엇으로(새 값) · 누가(요청한 소유자의 운영진 로그인) · 언제.
--   kind: SETTLEMENT_ACCOUNT(새 정산 계좌 세 칸만 채움) / BUSINESS_INFORMATION(새 사업자 정보 다섯 칸만 채움 — 사업자 주최자만).
--   새 값은 통째로 남긴다. 요청은 주최자 표의 값을 건드리지 않는다 — 확인 중에는 기존 값이 그대로 쓰인다(docs/11 "정산 계좌 변경").
-- 관리자가 처리 목록에서 확인한 뒤 승인하면 새 값을 주최자 표에 적용하고, 반려하면 사유를 남긴다. 처리 일시와 처리한 관리자를 남기고, 누가
-- 처리했는지는 관리자 조작 기록(admin_operation_record — V9)에도 남는다. 소유자가 요청한 일은 운영진 조작 기록(staff_operation_record — V11)에
-- 남는다. 결과는 소유자에게 메일로 간다.
-- 상태는 셋(도메인 OrganizerChangeRequestStatus). 승인 · 반려는 "아직 확인 중일 때만" 바꾸는 조건부 갱신이라, 두 관리자가 거의 동시에 처리하면
-- 하나만 통과하고 나머지는 충돌(409)이 된다.
-- 보관: 지우지 않는다. 정산금이 어느 계좌로 갈지 누가 언제 바꿨는지 되짚는 근거다. 문서에 정한 기간이 없다(docs/99 C "보관 기간"에 없음).
CREATE TABLE organizer_change_request (
    id                            BIGINT      GENERATED ALWAYS AS IDENTITY,
    organizer_id                  BIGINT      NOT NULL,
    kind                          VARCHAR(30) NOT NULL,
    requested_by_staff_account_id BIGINT      NOT NULL,
    requested_at                  TIMESTAMPTZ NOT NULL,
    settlement_bank_name          TEXT,
    settlement_account_number     TEXT,
    settlement_account_holder     TEXT,
    business_trade_name           TEXT,
    business_representative_name  TEXT,
    business_address              TEXT,
    business_phone_number         TEXT,
    business_registration_number  VARCHAR(10),
    status                        VARCHAR(30) NOT NULL,
    reviewed_at                   TIMESTAMPTZ,
    reviewed_by_admin_account_id  BIGINT,
    rejection_reason              TEXT,
    CONSTRAINT organizer_change_request_pk PRIMARY KEY (id),
    CONSTRAINT organizer_change_request_organizer_fk FOREIGN KEY (organizer_id)
        REFERENCES organizer (id),
    CONSTRAINT organizer_change_request_requested_by_fk FOREIGN KEY (requested_by_staff_account_id)
        REFERENCES staff_account (id),
    CONSTRAINT organizer_change_request_reviewed_by_fk FOREIGN KEY (reviewed_by_admin_account_id)
        REFERENCES admin_account (id),
    CONSTRAINT organizer_change_request_kind_ck CHECK (kind IN ('SETTLEMENT_ACCOUNT', 'BUSINESS_INFORMATION')),
    CONSTRAINT organizer_change_request_status_ck CHECK (status IN ('UNDER_REVIEW', 'APPROVED', 'REJECTED')),
    -- 종류에 맞는 새 값만 채운다(도메인 RequestedOrganizerChange)
    CONSTRAINT organizer_change_request_value_ck CHECK (
        (kind = 'SETTLEMENT_ACCOUNT'
            AND settlement_bank_name IS NOT NULL
            AND settlement_account_number IS NOT NULL
            AND settlement_account_holder IS NOT NULL
            AND business_trade_name IS NULL
            AND business_representative_name IS NULL
            AND business_address IS NULL
            AND business_phone_number IS NULL
            AND business_registration_number IS NULL)
        OR (kind = 'BUSINESS_INFORMATION'
            AND business_trade_name IS NOT NULL
            AND business_representative_name IS NOT NULL
            AND business_address IS NOT NULL
            AND business_phone_number IS NOT NULL
            AND business_registration_number IS NOT NULL
            AND settlement_bank_name IS NULL
            AND settlement_account_number IS NULL
            AND settlement_account_holder IS NULL)),
    -- 확인 중이면 처리 일시 · 관리자 · 사유가 없고, 승인이면 처리 일시 · 관리자만, 반려면 셋 다 있다. 처리는 요청 뒤다
    CONSTRAINT organizer_change_request_review_ck CHECK (
        (status = 'UNDER_REVIEW'
            AND reviewed_at IS NULL AND reviewed_by_admin_account_id IS NULL AND rejection_reason IS NULL)
        OR (status = 'APPROVED'
            AND reviewed_at IS NOT NULL AND reviewed_by_admin_account_id IS NOT NULL AND rejection_reason IS NULL
            AND reviewed_at >= requested_at)
        OR (status = 'REJECTED'
            AND reviewed_at IS NOT NULL AND reviewed_by_admin_account_id IS NOT NULL AND rejection_reason IS NOT NULL
            AND reviewed_at >= requested_at))
);

-- 한 주최자에 같은 종류의 확인 중인 요청은 하나뿐이다 — 결과를 기다린다. 새 요청을 저장하기 전에 유스케이스가 확인하므로 보통은 걸리지 않는다.
-- 거의 동시에 두 번 요청해도 이 색인이 하나만 남긴다. 소유자 화면이 그 주최자의 확인 중인 요청을 찾는 데도 쓴다.
CREATE UNIQUE INDEX organizer_change_request_under_review_ux
    ON organizer_change_request (organizer_id, kind)
    WHERE status = 'UNDER_REVIEW';

-- 처리 목록은 확인 중인 요청을 접수(요청) 순서로 모은다. 처리한 요청은 목록에서 빠지므로 확인 중인 줄만 색인한다.
CREATE INDEX organizer_change_request_queue_ix
    ON organizer_change_request (requested_at, id)
    WHERE status = 'UNDER_REVIEW';

-- 한 주최자의 변경 요청을 요청한 순서로 본다 — 언제 누가 무엇으로 바꿨는지 되짚을 때.
CREATE INDEX organizer_change_request_organizer_ix ON organizer_change_request (organizer_id, id);
