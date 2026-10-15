-- 관리자 계정 · 관리자 조작 기록 (#21)
-- 근거: docs/01 "계정 4종", "관리자 2등급", docs/13 "5. 플랫폼 관리자"(관리자 계정 발급, 모든 조작 기록),
--       docs/99 B "관리자 등급 세분화", "관리자 프로필", docs/11 "데이터 보관"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V10__...)을 추가한다.

-- 관리자 계정 한 줄 = 플랫폼 관리자 한 명. 자유 가입이 없고 최고 관리자가 발급한다(docs/01 "계정 4종").
-- 맨 처음 최고 관리자를 만드는 방법은 #22가 정한다.
-- 관객 계정(audience_account) · 운영진 로그인(staff_account)과 별개다. 같은 이메일이 다른 표에 있어도 된다.
-- 등급은 둘뿐이다(도메인 AdminGrade, docs/99 B "관리자 등급 세분화"). 최고 관리자만 영구 정지와 관리자 계정 발급을 한다.
-- 프로필이 없어(docs/99 B "관리자 프로필") 이름·닉네임을 받지 않는다. 비밀번호는 해시로만 둔다.
-- 보관: 계정이 유지되는 동안(docs/11 "데이터 보관 — 계정 정보").
CREATE TABLE admin_account (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY,
    grade           VARCHAR(20)  NOT NULL,
    email           VARCHAR(254) NOT NULL,
    email_lowercase VARCHAR(254) NOT NULL,
    password_hash   TEXT         NOT NULL,
    issued_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT admin_account_pk PRIMARY KEY (id),
    CONSTRAINT admin_account_grade_ck CHECK (grade IN ('ADMIN', 'SUPER_ADMIN')),
    CONSTRAINT admin_account_email_lowercase_ck CHECK (email_lowercase = lower(email))
);

-- 같은 이메일의 관리자 계정은 하나뿐이다. 이메일이 관리자 로그인 아이디다.
-- 대소문자는 가리지 않는다 — 관객 계정(V2)·운영진 로그인(V5)과 같이 lower()로 채운 email_lowercase로 비교하고 CHECK로 묶는다.
-- 같은 이메일로 동시에 발급해도 이 색인이 하나만 남긴다.
CREATE UNIQUE INDEX admin_account_email_ux
    ON admin_account (email_lowercase);

-- 관리자 조작 기록 한 줄 = 관리자가 한 조작 하나. 누가(관리자) · 언제 · 무엇을(조작 종류) · 무엇에(대상 종류 · 대상 id) · 요약 · 사유.
-- 관리자의 모든 조작에 남긴다(docs/01 "관리자 2등급" — 되돌릴 수 없는 조작을 추적하려고). 조작과 같은 트랜잭션에서 넣는다.
-- operation_kind · target_kind는 도메인 AdminOperationKind · AdminOperationTargetKind 이름 그대로다. 관리자 기능 이슈마다 늘어나므로
-- CHECK로 묶지 않는다(발송 이력의 알림 종류와 같다). 대상 종류는 조작 종류가 정한다.
-- target_id는 대상 종류마다 다른 표의 id라 외래 키를 걸지 않는다.
-- summary는 목록에서 읽는 한 줄 요약이다(예: "관리자 계정 발급 — new-admin@example.com (관리자)").
-- reason은 관리자가 적은 사유다(정지 사유, 반려 사유 등). 사유가 없는 조작이면 비어 있다.
-- 고치거나 지우지 않는다.
-- 보관: 문서에 정한 기간이 없어 지우지 않는다(docs/11 "데이터 보관"에 없음). 기간이 정해지면 파기 작업을 더한다.
CREATE TABLE admin_operation_record (
    id               BIGINT      GENERATED ALWAYS AS IDENTITY,
    admin_account_id BIGINT      NOT NULL,
    operated_at      TIMESTAMPTZ NOT NULL,
    operation_kind   VARCHAR(60) NOT NULL,
    target_kind      VARCHAR(40) NOT NULL,
    target_id        BIGINT      NOT NULL,
    summary          TEXT        NOT NULL,
    reason           TEXT,
    CONSTRAINT admin_operation_record_pk PRIMARY KEY (id),
    CONSTRAINT admin_operation_record_admin_account_fk FOREIGN KEY (admin_account_id)
        REFERENCES admin_account (id),
    CONSTRAINT admin_operation_record_target_id_ck CHECK (target_id >= 1)
);

-- 한 관리자가 한 조작을 시각 순서로 본다.
CREATE INDEX admin_operation_record_admin_account_ix ON admin_operation_record (admin_account_id, operated_at);

-- 한 대상(예: 주최자 하나)에 관리자가 한 조작을 모아 본다 — 주최자 상세의 이력 등.
CREATE INDEX admin_operation_record_target_ix ON admin_operation_record (target_kind, target_id, operated_at);
