-- 운영진 조작 기록 (#24)
-- 근거: docs/01 "권한 두 가지 — 누가 했는지 남깁니다", docs/13 "5. 플랫폼 관리자 — 주최자 목록 → 상세", "모든 조작에 '누가 언제 무엇을'이
--       기록됩니다", docs/11 "데이터 보관"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V12__...)을 추가한다.

-- 운영진 조작 기록 한 줄 = 운영진이 주최자 안에서 한 조작 하나. 어느 주최자에서 · 누가(운영진 · 그때의 역할) · 언제 · 무엇을(조작 종류) ·
-- 무엇에(대상 종류 · 대상 id) · 요약.
-- 운영진의 모든 조작에 남긴다(docs/01 — "초대권 10장 발권 · 운영자 김○○"). 조작과 같은 트랜잭션에서 넣는다(도메인 StaffOperationRecord).
-- 관리자가 주최자 상세에서 그 주최자의 기록을 본다.
-- staff_role은 조작할 때의 역할이다. 소유자를 넘기면 역할이 바뀌므로 그때의 역할을 남긴다(도메인 StaffRole 이름 그대로).
-- staff_action은 조작 종류 — 그 조작으로 한 일(도메인 StaffAction 이름 그대로)이고, 같은 일 안의 세부(발권인지 회수인지 등)는 summary가 말한다.
-- staff_action · target_kind는 기능 이슈마다 늘어나므로 CHECK로 묶지 않는다(관리자 조작 기록 — V9와 같다).
-- target_id는 대상 종류마다 다른 표의 id라 외래 키를 걸지 않는다. 대상이 주최자 자신(ORGANIZER)이면 organizer_id와 같다.
-- summary는 목록에서 읽는 "무엇을" 한 줄이다(예: "초대권 10장 발권"). 누가 했는지는 staff_account_id · staff_role로 따로 남고, 이름은 읽을 때
-- 운영진 로그인에서 가져온다 — 닉네임을 바꿔도 기록은 그 사람을 따라간다.
-- 고치거나 지우지 않는다.
-- 보관: 문서에 정한 기간이 없어 지우지 않는다(docs/11 "데이터 보관"에 없음). 기간이 정해지면 파기 작업을 더한다.
CREATE TABLE staff_operation_record (
    id               BIGINT      GENERATED ALWAYS AS IDENTITY,
    organizer_id     BIGINT      NOT NULL,
    staff_account_id BIGINT      NOT NULL,
    staff_role       VARCHAR(20) NOT NULL,
    operated_at      TIMESTAMPTZ NOT NULL,
    staff_action     VARCHAR(60) NOT NULL,
    target_kind      VARCHAR(40) NOT NULL,
    target_id        BIGINT      NOT NULL,
    summary          TEXT        NOT NULL,
    CONSTRAINT staff_operation_record_pk PRIMARY KEY (id),
    CONSTRAINT staff_operation_record_organizer_fk FOREIGN KEY (organizer_id)
        REFERENCES organizer (id),
    CONSTRAINT staff_operation_record_staff_account_fk FOREIGN KEY (staff_account_id)
        REFERENCES staff_account (id),
    CONSTRAINT staff_operation_record_staff_role_ck CHECK (staff_role IN ('OWNER', 'OPERATOR')),
    CONSTRAINT staff_operation_record_target_id_ck CHECK (target_id >= 1)
);

-- 한 주최자의 기록을 최근 것부터 본다 — 관리자 주최자 상세의 운영진 조작 기록. 넣은 순서(id)가 조작 순서다.
CREATE INDEX staff_operation_record_organizer_ix ON staff_operation_record (organizer_id, id);
