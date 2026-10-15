-- 소유자 이전 신청 (#27)
-- 근거: docs/01 "소유자 넘기기 — 소유자와 연락이 끊긴 경우", "가로채기를 막는 장치", "관리자 2등급"(소유자 이전 — 연락 두절 시),
--       docs/11 "대표가 바뀔 때", docs/12 "관리자 대상 — 소유자 이전 신청", docs/13 "운영진 관리 — 소유자 이전 신청",
--       "5. 플랫폼 관리자 — 처리 목록: 소유자 이전 신청"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V15__...)을 추가한다.

-- 소유자 이전 신청 한 줄 = 운영자가 관리자에게 낸 신청 한 번(도메인 OwnershipClaim). 소유자와 연락이 끊겨 넘겨줄 사람이 없을 때만 낸다.
-- 어느 주최자에서 · 누가(신청한 운영자) · 누구의 자리를(신청 때의 소유자 — 연락이 끊겼다는 그 사람) · 왜(사유) · 언제.
-- 관리자는 사유와 주최자 신청 때 받은 연락처 · SNS(organizer_application — V8)로 확인한 뒤 승인하거나 사유를 붙여 반려한다.
--   승인: 신청한 운영자가 소유자가 되고 신청 때의 소유자는 운영자로 남는다(소속 표 staff_membership의 역할 교대 — 소유자 넘기기와 같다).
--         소유자 변경 이력(ownership_change)에 '신청 승인'으로 한 줄이 남는다(아래).
--   반려: 사유(rejection_reason)를 남기고 운영진 전원에게 알린다. 소유자는 그대로다.
-- 상태는 셋(도메인 OwnershipClaimStatus). 승인 · 반려는 "아직 심사 중일 때만" 바꾸는 조건부 갱신이라, 두 관리자가 거의 동시에 처리하면
-- 하나만 통과하고 나머지는 충돌(409)이 된다. 누가 처리했는지는 관리자 조작 기록(admin_operation_record — V9)에 남는다.
-- 운영자가 신청한 일은 운영진 조작 기록(staff_operation_record — V11)에도 남는다.
-- 증빙 서류 · 심사 체크리스트는 문서에 없어 두지 않는다.
-- 보관: 지우지 않는다 — 소유자 변경 이력이 가리키고, 주최자 이력과 같이 남는다. 문서에 정한 기간이 없다.
CREATE TABLE ownership_claim (
    id                        BIGINT      GENERATED ALWAYS AS IDENTITY,
    organizer_id              BIGINT      NOT NULL,
    claimant_staff_account_id BIGINT      NOT NULL,
    owner_staff_account_id    BIGINT      NOT NULL,
    reason                    TEXT        NOT NULL,
    status                    VARCHAR(30) NOT NULL,
    submitted_at              TIMESTAMPTZ NOT NULL,
    reviewed_at               TIMESTAMPTZ,
    rejection_reason          TEXT,
    CONSTRAINT ownership_claim_pk PRIMARY KEY (id),
    CONSTRAINT ownership_claim_organizer_fk FOREIGN KEY (organizer_id)
        REFERENCES organizer (id),
    CONSTRAINT ownership_claim_claimant_fk FOREIGN KEY (claimant_staff_account_id)
        REFERENCES staff_account (id),
    CONSTRAINT ownership_claim_owner_fk FOREIGN KEY (owner_staff_account_id)
        REFERENCES staff_account (id),
    CONSTRAINT ownership_claim_status_ck CHECK (status IN ('UNDER_REVIEW', 'APPROVED', 'REJECTED')),
    -- 소유자는 이전 신청을 하지 않고 직접 넘긴다(도메인 OwnershipClaim)
    CONSTRAINT ownership_claim_different_people_ck CHECK (claimant_staff_account_id <> owner_staff_account_id),
    -- 심사 중이면 처리 일시·사유가 없고, 승인이면 처리 일시만, 반려면 처리 일시와 사유가 있다. 처리는 신청 뒤다
    CONSTRAINT ownership_claim_review_ck CHECK (
        (status = 'UNDER_REVIEW' AND reviewed_at IS NULL AND rejection_reason IS NULL)
        OR (status = 'APPROVED' AND reviewed_at IS NOT NULL AND rejection_reason IS NULL AND reviewed_at >= submitted_at)
        OR (status = 'REJECTED' AND reviewed_at IS NOT NULL AND rejection_reason IS NOT NULL AND reviewed_at >= submitted_at))
);

-- 한 사람은 한 주최자에 심사 중인 신청을 하나만 둔다 — 결과를 기다린다. 새 신청을 저장하기 전에 유스케이스가 확인하므로 보통은 걸리지
-- 않는다. 같은 사람이 거의 동시에 두 번 내도 이 색인이 하나만 남긴다. 다른 운영자의 신청과는 상관없다.
CREATE UNIQUE INDEX ownership_claim_claimant_under_review_ux
    ON ownership_claim (organizer_id, claimant_staff_account_id)
    WHERE status = 'UNDER_REVIEW';

-- 처리 목록은 심사 중인 신청을 접수(신청) 순서로 모은다. 처리한 신청은 목록에서 빠지므로 심사 중인 줄만 색인한다.
CREATE INDEX ownership_claim_under_review_ix
    ON ownership_claim (submitted_at, id)
    WHERE status = 'UNDER_REVIEW';

-- 소유자 변경 이력(V13)에 '어떻게 바뀌었나'를 더한다(도메인 OwnershipChangeMethod).
--   OWNER_HANDOVER: 소유자 넘기기 — 관리자를 거치지 않아 관리자가 사후 확인한다(처리 목록 '소유자 변경'). 지금까지의 줄은 모두 이것이다.
--   CLAIM_APPROVAL: 소유자 이전 신청 승인 — 관리자가 확인한 뒤 바꾼 것이라 사후 확인하지 않는다. 처리 목록 '소유자 변경'에 쌓이지 않고,
--                   확인 일시 · 확인한 관리자가 늘 비어 있다. 승인한 신청(ownership_claim_id)을 가리키고, 신청 하나에 이력은 하나다.
ALTER TABLE ownership_change ADD COLUMN method VARCHAR(30) NOT NULL DEFAULT 'OWNER_HANDOVER';
ALTER TABLE ownership_change ALTER COLUMN method DROP DEFAULT;
ALTER TABLE ownership_change ADD COLUMN ownership_claim_id BIGINT;
ALTER TABLE ownership_change ADD CONSTRAINT ownership_change_claim_fk FOREIGN KEY (ownership_claim_id)
    REFERENCES ownership_claim (id);
ALTER TABLE ownership_change ADD CONSTRAINT ownership_change_method_ck CHECK (
    (method = 'OWNER_HANDOVER' AND ownership_claim_id IS NULL)
    OR (method = 'CLAIM_APPROVAL'
        AND ownership_claim_id IS NOT NULL
        AND confirmed_at IS NULL
        AND confirmed_by_admin_account_id IS NULL));

CREATE UNIQUE INDEX ownership_change_claim_ux
    ON ownership_change (ownership_claim_id)
    WHERE ownership_claim_id IS NOT NULL;
