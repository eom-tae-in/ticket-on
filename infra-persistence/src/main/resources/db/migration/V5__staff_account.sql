-- 운영진 로그인 계정 — 가입·닉네임·이메일 인증 (#15)
-- 근거: docs/01 "계정 4종", "수집 정보 — 주최자 운영진", "같은 이메일로 양쪽 가입", docs/99 C "운영진 닉네임"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V6__...)을 추가한다.

-- 운영진 로그인 한 줄 = 주최자 쪽 로그인을 만든 사람 한 명.
-- 관객 계정(audience_account)과 별개다. 같은 이메일로 둘 다 만들 수 있고 서로 연결하지 않는다(docs/01 "같은 이메일로 양쪽 가입").
-- 주최자(단체)에 소속되는 일은 주최자 쪽 표가 이 줄을 가리켜 나타낸다(#19·#20).
-- 받는 정보는 이름·닉네임·이메일(인증)·비밀번호뿐이다(docs/01). 비밀번호는 해시로만 둔다.
-- 이용 정지는 사람이 아니라 주최자에 걸려(docs/01 "정지 기록은 주최자에 걸립니다") 상태에 없다.
-- 보관: 회원이 유지하는 동안. 탈퇴했을 때 무엇을 지우는지는 회원 탈퇴를 만드는 이슈가 정한다.
CREATE TABLE staff_account (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY,
    status            VARCHAR(30)  NOT NULL,
    name              TEXT         NOT NULL,
    nickname          VARCHAR      NOT NULL,
    email             VARCHAR(254) NOT NULL,
    email_lowercase   VARCHAR(254) NOT NULL,
    password_hash     TEXT         NOT NULL,
    signed_up_at      TIMESTAMPTZ  NOT NULL,
    email_verified_at TIMESTAMPTZ,
    CONSTRAINT staff_account_pk PRIMARY KEY (id),
    CONSTRAINT staff_account_status_ck CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'WITHDRAWN')),
    CONSTRAINT staff_account_email_lowercase_ck CHECK (email_lowercase = lower(email)),
    CONSTRAINT staff_account_email_verified_ck
        CHECK (status = 'WITHDRAWN' OR (status = 'PENDING_VERIFICATION') = (email_verified_at IS NULL))
);

-- 같은 이메일의 운영진 로그인은 하나뿐이다. 이메일이 운영진 로그인 아이디다.
-- 대소문자는 가리지 않는다 — 관객 계정(V2)과 같이 lower()로 채운 email_lowercase로 비교하고 CHECK로 묶는다.
-- 관객 계정의 이메일과는 겹쳐도 된다(다른 표). 탈퇴하면 이메일이 풀린다. 같은 이메일로 동시에 가입해도 이 색인이 하나만 남긴다.
CREATE UNIQUE INDEX staff_account_email_ux
    ON staff_account (email_lowercase)
    WHERE status <> 'WITHDRAWN';

-- 닉네임은 운영진 사이에서 하나뿐이다(docs/99 C "운영진 닉네임 — 중복 불가"). 공개 프로필이라 같은 닉네임이 둘이면 구분되지 않는다.
-- 탈퇴한 계정의 닉네임도 쥔다 — 지난 이력에 남은 이름을 다른 사람이 가져가지 않게.
-- 값은 앞뒤 공백을 걷고 NFC로 맞춘 그대로다(도메인 Nickname). 대소문자는 가린다.
-- 길이 상한은 문서에 없어 두지 않는다 — 칸은 길이 없는 VARCHAR(PostgreSQL에서 TEXT와 같다)다. TEXT로 두면 jOOQ 코드 생성이
-- 흉내 내는 H2가 그 칸에 색인을 만들지 못한다.
-- 같은 닉네임으로 동시에 가입하거나 바꿔도 이 색인이 하나만 남긴다.
CREATE UNIQUE INDEX staff_account_nickname_ux
    ON staff_account (nickname);

-- 운영진 로그인 이메일 인증 링크 — 가입하면 하나 보낸다. 값은 추측할 수 없는 무작위 값이다(#5 TokenGenerator).
-- 관객 인증 링크(audience_email_verification_token)와 따로 둔다 — 계정 id를 따로 매겨 한쪽 링크로 다른 쪽 계정을 인증하지 않게.
-- 인증한 뒤에도 남겨, 같은 링크를 다시 열면 "이미 인증을 마친 계정"으로 안내한다.
CREATE TABLE staff_email_verification_token (
    token            VARCHAR(64)  NOT NULL,
    staff_account_id BIGINT       NOT NULL,
    issued_at        TIMESTAMPTZ  NOT NULL,
    expires_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT staff_email_verification_token_pk PRIMARY KEY (token),
    CONSTRAINT staff_email_verification_token_account_fk FOREIGN KEY (staff_account_id)
        REFERENCES staff_account (id),
    CONSTRAINT staff_email_verification_token_period_ck CHECK (expires_at > issued_at)
);

CREATE INDEX staff_email_verification_token_account_ix ON staff_email_verification_token (staff_account_id);
