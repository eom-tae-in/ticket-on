-- 운영자 초대 (#25)
-- 근거: docs/01 "운영자 초대", "권한 두 가지 — 운영진 초대·내보내기", docs/12 "주최자 대상 — 운영자 초대 (받는 사람에게)",
--       docs/13 "3. 주최 관리 — 소속된 주최자가 없으면 → 받은 초대", "운영진 관리"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V13__...)을 추가한다.

-- 운영자 초대 한 줄 = 소유자가 이메일로 보낸 초대 하나(도메인 StaffInvitation).
-- 받는 사람은 이메일로 정한다 — 초대받은 이메일과 같은 이메일(대소문자 무시)의 운영진 로그인만 수락·거절한다. 아직 주최자 쪽 로그인이 없는
-- 사람에게도 보내므로 운영진 계정을 가리키지 않고 이메일을 둔다. 대소문자는 운영진 로그인(V5)과 같이 lower()로 채운 칸으로 비교하고 CHECK로 묶는다.
-- 상태는 셋만 저장한다(도메인 StaffInvitationStatus). "만료"는 저장하지 않는다 — 답을 기다리는 초대가 expires_at을 지났는지로 계산한다.
-- expires_at은 보낸 때부터 유효 기간(ProposedPolicy.STAFF_INVITATION_VALIDITY — 확인 필요 #25) 뒤이고, 그 순간부터 답할 수 없다.
-- 지난 초대를 같은 이메일로 다시 초대하면 새 줄을 만들지 않고 그 줄의 보낸 사람 · 보낸 일시 · 유효 기간을 다시 쓴다(도메인 StaffInvitation.reinvite).
-- 수락·거절하면 답한 일시와 답한 운영진 계정을 남긴다. 수락하면 운영자 소속 줄(staff_membership)이 같은 트랜잭션에서 생긴다.
-- 초대를 보내고 수락한 일은 운영진 조작 기록(staff_operation_record — 대상 STAFF_INVITATION, 대상 id = 이 표의 id)에도 남는다.
-- 보관: 문서에 정한 기간이 없어 지우지 않는다(docs/11 "데이터 보관"에 없음 — 확인 과제로 올린다). 기간이 정해지면 파기 작업을 더한다.
CREATE TABLE staff_invitation (
    id                           BIGINT       GENERATED ALWAYS AS IDENTITY,
    organizer_id                 BIGINT       NOT NULL,
    invitee_email                VARCHAR(254) NOT NULL,
    invitee_email_lowercase      VARCHAR(254) NOT NULL,
    invited_by_staff_account_id  BIGINT       NOT NULL,
    invited_at                   TIMESTAMPTZ  NOT NULL,
    expires_at                   TIMESTAMPTZ  NOT NULL,
    status                       VARCHAR(20)  NOT NULL,
    answered_at                  TIMESTAMPTZ,
    answered_by_staff_account_id BIGINT,
    CONSTRAINT staff_invitation_pk PRIMARY KEY (id),
    CONSTRAINT staff_invitation_organizer_fk FOREIGN KEY (organizer_id)
        REFERENCES organizer (id),
    CONSTRAINT staff_invitation_invited_by_fk FOREIGN KEY (invited_by_staff_account_id)
        REFERENCES staff_account (id),
    CONSTRAINT staff_invitation_answered_by_fk FOREIGN KEY (answered_by_staff_account_id)
        REFERENCES staff_account (id),
    CONSTRAINT staff_invitation_status_ck CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    CONSTRAINT staff_invitation_email_lowercase_ck CHECK (invitee_email_lowercase = lower(invitee_email)),
    CONSTRAINT staff_invitation_period_ck CHECK (expires_at > invited_at),
    -- 답을 기다리면 답한 일시·사람이 없고, 수락·거절이면 둘 다 있다(도메인 StaffInvitation)
    CONSTRAINT staff_invitation_answer_ck CHECK (
        (status = 'PENDING' AND answered_at IS NULL AND answered_by_staff_account_id IS NULL)
        OR (status IN ('ACCEPTED', 'DECLINED') AND answered_at IS NOT NULL
            AND answered_by_staff_account_id IS NOT NULL AND answered_at >= invited_at))
);

-- 한 주최자가 한 이메일로 답을 기다리는 초대는 하나뿐이다 — 아직 유효하면 다시 보내지 않고, 지났으면 그 줄을 다시 연다.
-- 같은 이메일로 거의 동시에 두 번 초대해도 이 색인이 하나만 남긴다. 그 주최자 · 이메일의 기다리는 초대를 찾는 색인도 겸한다.
CREATE UNIQUE INDEX staff_invitation_pending_ux
    ON staff_invitation (organizer_id, invitee_email_lowercase)
    WHERE status = 'PENDING';

-- 한 사람이 받은 초대를 찾는다 — 내 주최 홈의 "받은 초대". 운영진 로그인 이메일로 찾는다.
CREATE INDEX staff_invitation_invitee_pending_ix
    ON staff_invitation (invitee_email_lowercase)
    WHERE status = 'PENDING';
