-- 소유자 넘기기 · 운영진 로그인 탈퇴 (#26)
-- 근거: docs/01 "소유자 넘기기", "가로채기를 막는 장치", "탈퇴와 주최자 닫기", docs/11 "대표가 바뀔 때", "데이터 보관 — 회원 탈퇴 시",
--       docs/12 "주최자 대상 — 소유자 변경", "소유자가 바뀌면 관리자에게도 알립니다", docs/13 "5. 플랫폼 관리자 — 주최자 목록"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V14__...)을 추가한다.
--
-- 소유자 넘기기의 역할 교대는 새 표 없이 소속 표(staff_membership — V8)에 쓴다: 넘긴 사람의 소유자 줄과 받은 사람의 운영자 줄을 끝내고,
-- 넘긴 사람의 운영자 줄과 받은 사람의 소유자 줄을 같은 시각에 시작한다. 두 끝내기는 "아직 끝나지 않았을 때만" 바꾸는 조건부 갱신이라, 같은
-- 소유자가 거의 동시에 두 번 넘기면 늦은 쪽은 바뀐 줄이 없어 충돌(409)로 멈춘다. 지금 소유자가 주최자마다 많아야 한 명이라는 약속은
-- V8의 유일 색인(staff_membership_active_owner_ux)이 마지막으로 지킨다.

-- 소유자 변경 이력 한 줄 = 소유자를 넘긴 한 건(도메인 OwnershipChange). 어느 주최자에서 · 누가(넘긴 사람) · 누구에게(받은 사람) · 언제.
-- 소유자가 바뀌면 관리자에게도 알린다 — 관리자는 알림 대신 처리 목록으로 받는다(docs/12 "관리자 대상"). 관리자를 거치지 않고 이미 바뀐
-- 일이라 관리자는 사후 확인만 한다. 확인하면(confirmed_at · confirmed_by_admin_account_id) 처리 목록에서 빠진다. 확인은 한 번뿐이고, 두
-- 관리자가 거의 동시에 확인하면 "아직 확인 전일 때만" 바꾸는 조건부 갱신이 하나만 통과시킨다. 누가 확인했는지는 관리자 조작 기록에도 남는다.
-- 넘긴 사람은 운영자로 남는다(소속 표). 넘기기 자체는 운영진 조작 기록(staff_operation_record — V11)에도 남는다.
-- 보관: 지우지 않는다 — 관리자 주최자 상세의 "소유자 변경 이력"이 된다(docs/13). 문서에 정한 기간이 없다.
CREATE TABLE ownership_change (
    id                              BIGINT      GENERATED ALWAYS AS IDENTITY,
    organizer_id                    BIGINT      NOT NULL,
    previous_owner_staff_account_id BIGINT      NOT NULL,
    new_owner_staff_account_id      BIGINT      NOT NULL,
    changed_at                      TIMESTAMPTZ NOT NULL,
    confirmed_at                    TIMESTAMPTZ,
    confirmed_by_admin_account_id   BIGINT,
    CONSTRAINT ownership_change_pk PRIMARY KEY (id),
    CONSTRAINT ownership_change_organizer_fk FOREIGN KEY (organizer_id)
        REFERENCES organizer (id),
    CONSTRAINT ownership_change_previous_owner_fk FOREIGN KEY (previous_owner_staff_account_id)
        REFERENCES staff_account (id),
    CONSTRAINT ownership_change_new_owner_fk FOREIGN KEY (new_owner_staff_account_id)
        REFERENCES staff_account (id),
    CONSTRAINT ownership_change_confirmed_by_fk FOREIGN KEY (confirmed_by_admin_account_id)
        REFERENCES admin_account (id),
    CONSTRAINT ownership_change_different_people_ck CHECK (previous_owner_staff_account_id <> new_owner_staff_account_id),
    -- 확인 전이면 확인 일시·관리자가 없고, 확인했으면 둘 다 있다. 넘긴 일시보다 앞서 확인할 수 없다(도메인 OwnershipChange)
    CONSTRAINT ownership_change_confirmation_ck CHECK (
        (confirmed_at IS NULL AND confirmed_by_admin_account_id IS NULL)
        OR (confirmed_at IS NOT NULL AND confirmed_by_admin_account_id IS NOT NULL AND confirmed_at >= changed_at))
);

-- 처리 목록은 확인 전인 변경을 접수(넘긴) 순서로 모은다. 확인한 변경은 목록에서 빠지므로 확인 전인 줄만 색인한다.
CREATE INDEX ownership_change_unconfirmed_ix
    ON ownership_change (changed_at, id)
    WHERE confirmed_at IS NULL;

-- 한 주최자의 소유자 변경 이력을 본다 — 관리자 주최자 상세.
CREATE INDEX ownership_change_organizer_ix ON ownership_change (organizer_id, id);

-- 운영진 로그인 탈퇴 — 이름 · 이메일 · 비밀번호 해시를 바로 파기한다(docs/11 "데이터 보관 — 회원 탈퇴 시: 법정 보존 대상 외 즉시 파기").
-- 운영진 로그인에는 법정 보존 대상이 없다. 줄은 지우지 않는다 — 소속 이력 · 운영진 조작 기록 · 주최자 신청서 · 소유자 변경 이력이 이 줄을
-- 가리키고, 운영진 프로필의 참여 이력은 그대로 남는다(docs/11 "운영진 소속 이력 — 유지").
-- 남는 것: id · 상태('WITHDRAWN') · 닉네임 · 가입 일시 · 탈퇴 일시(withdrawn_at).
--   닉네임은 탈퇴한 뒤에도 쥔다(V5 staff_account_nickname_ux — 지난 이력에 남은 이름을 다른 사람이 가져가지 않게).
--   이메일은 풀린다 — 같은 이메일로 다시 가입할 수 있다(V5 staff_account_email_ux는 탈퇴하지 않은 계정만 본다).
-- 이메일 인증 링크(staff_email_verification_token)와 비밀번호 재설정 링크(password_reset_token)의 그 계정 줄은 탈퇴할 때 지운다.
-- 탈퇴하면 그 계정의 로그인 세션(spring_session)을 모두 끊는다.
ALTER TABLE staff_account ADD COLUMN withdrawn_at TIMESTAMPTZ;
ALTER TABLE staff_account ALTER COLUMN name DROP NOT NULL;
ALTER TABLE staff_account ALTER COLUMN email DROP NOT NULL;
ALTER TABLE staff_account ALTER COLUMN email_lowercase DROP NOT NULL;
ALTER TABLE staff_account ALTER COLUMN password_hash DROP NOT NULL;

-- 탈퇴했으면 탈퇴 일시가 있고 이름 · 이메일 · 비밀번호 해시 · 이메일 인증 일시가 없다. 탈퇴하지 않았으면 탈퇴 일시가 없고 나머지는 다 있다.
ALTER TABLE staff_account ADD CONSTRAINT staff_account_withdrawal_ck CHECK (
    (status = 'WITHDRAWN'
        AND withdrawn_at IS NOT NULL
        AND name IS NULL
        AND email IS NULL
        AND email_lowercase IS NULL
        AND password_hash IS NULL
        AND email_verified_at IS NULL)
    OR (status <> 'WITHDRAWN'
        AND withdrawn_at IS NULL
        AND name IS NOT NULL
        AND email IS NOT NULL
        AND email_lowercase IS NOT NULL
        AND password_hash IS NOT NULL));
