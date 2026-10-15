-- 주최자명 변경 이력 (#30)
-- 근거: docs/01 "공개 페이지 — 주최자명 변경"(소유자가 바로 변경, 이름 변경 이력 — 언제, 무엇에서 무엇으로), "주최자명을 소유자가 바로 바꿀
--       수 있게 한 이유", docs/12 "주최자 대상 — 주최자명 변경 (운영진 전원에게)", "관리자 대상 — 주최자명 변경 (사후 확인용)",
--       docs/13 "처리 목록 — 주최자명 변경 (사후 확인)", docs/14 "무엇이 무엇을 가리키나"(주최자명 변경 이력 → 주최자)
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V18__...)을 추가한다.
--
-- 주최자명은 소유자가 바로 바꾼다 — 주최자 표(organizer — V8)의 name을 그 자리에서 고친다. 행사는 주최자를 가리키기만 하므로 지난 행사
-- 페이지에도 새 이름이 뜬다. 그래서 "샀을 때 무슨 이름이었나"를 확인할 근거로 이 표에 바꾼 한 건씩 남긴다.

-- 주최자명 변경 이력 한 줄 = 이름을 바꾼 한 건(도메인 OrganizerNameChangeEntry). 어느 주최자의 · 언제 · 무엇에서(previous_name) · 무엇으로
-- (new_name) · 누가(바꾼 소유자의 운영진 로그인). 이름 값은 바꿀 때의 그대로 남긴다 — 주최자가 이름을 또 바꿔도 이 줄은 바뀌지 않는다.
-- 소유자가 바꾼 일이라 관리자에게도 알린다 — 관리자는 알림 대신 처리 목록으로 받는다(docs/12 "관리자 대상"). 관리자를 거치지 않고 이미 바뀐
-- 일이라 관리자는 사후 확인만 한다. 확인하면(confirmed_at · confirmed_by_admin_account_id) 처리 목록에서 빠진다. 확인은 한 번뿐이고, 두
-- 관리자가 거의 동시에 확인하면 "아직 확인 전일 때만" 바꾸는 조건부 갱신이 하나만 통과시킨다. 누가 확인했는지는 관리자 조작 기록에도 남는다.
-- 이름을 바꾼 일 자체는 운영진 조작 기록(staff_operation_record — V11)에도 남는다.
-- 공개: 문서는 확인할 근거로 남기라고만 하고 이 이력을 보여 줄 화면을 정하지 않았다 — 관객 화면에 이전 이름을 보이지 않는다.
-- 보관: 지우지 않는다. 문서에 정한 기간이 없다(docs/99 C "보관 기간"에 없음). 바꾼 사람이 운영진 로그인을 탈퇴해도 줄은 남는다(그 계정 줄은
--       닉네임과 함께 남는다 — V13).
CREATE TABLE organizer_name_change (
    id                            BIGINT      GENERATED ALWAYS AS IDENTITY,
    organizer_id                  BIGINT      NOT NULL,
    previous_name                 TEXT        NOT NULL,
    new_name                      TEXT        NOT NULL,
    changed_by_staff_account_id   BIGINT      NOT NULL,
    changed_at                    TIMESTAMPTZ NOT NULL,
    confirmed_at                  TIMESTAMPTZ,
    confirmed_by_admin_account_id BIGINT,
    CONSTRAINT organizer_name_change_pk PRIMARY KEY (id),
    CONSTRAINT organizer_name_change_organizer_fk FOREIGN KEY (organizer_id)
        REFERENCES organizer (id),
    CONSTRAINT organizer_name_change_changed_by_fk FOREIGN KEY (changed_by_staff_account_id)
        REFERENCES staff_account (id),
    CONSTRAINT organizer_name_change_confirmed_by_fk FOREIGN KEY (confirmed_by_admin_account_id)
        REFERENCES admin_account (id),
    -- 지금 이름과 같은 이름으로 바꾸면 줄이 생기지 않는다(도메인 Organizer.rename · OrganizerNameChange)
    CONSTRAINT organizer_name_change_different_names_ck CHECK (previous_name <> new_name),
    -- 확인 전이면 확인 일시·관리자가 없고, 확인했으면 둘 다 있다. 바꾼 일시보다 앞서 확인할 수 없다(도메인 OrganizerNameChangeEntry)
    CONSTRAINT organizer_name_change_confirmation_ck CHECK (
        (confirmed_at IS NULL AND confirmed_by_admin_account_id IS NULL)
        OR (confirmed_at IS NOT NULL AND confirmed_by_admin_account_id IS NOT NULL AND confirmed_at >= changed_at))
);

-- 처리 목록은 확인 전인 변경을 접수(바꾼) 순서로 모은다. 확인한 변경은 목록에서 빠지므로 확인 전인 줄만 색인한다.
CREATE INDEX organizer_name_change_unconfirmed_ix
    ON organizer_name_change (changed_at, id)
    WHERE confirmed_at IS NULL;

-- 한 주최자의 이름 변경 이력을 바꾼 순서로 본다 — "샀을 때 무슨 이름이었나"를 확인할 때.
CREATE INDEX organizer_name_change_organizer_ix ON organizer_name_change (organizer_id, id);
