-- 관객 계정 — 직접 가입과 이메일 인증 (#11)
-- 근거: docs/01 "수집 정보 — 사용자", "전화번호 — 인증하지 않고, 본인 번호라고 가정합니다", "약관과 동의 — 가입 시",
--       docs/11 "데이터 보관"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V3__...)을 추가한다.

-- 관객 계정 한 줄 = 가입한 사람 한 명.
-- 개인정보 칸은 docs/01이 정한 넷(이름·생년월일·이메일·전화번호)뿐이고 전부 필수다. 그 밖의 정보 칸은 두지 않는다(docs/99 B).
-- 보관: 회원이 유지하는 동안. 탈퇴하면 법정 보존 대상(결제 기록) 외에는 즉시 파기하고, 결제 기록이 가리키는 이 줄에서
--       개인 식별 정보를 분리한다(docs/11 "데이터 보관", "화면에서 내리는 것과 지우는 것은 다릅니다" — #83).
-- 비밀번호는 해시로만 둔다. 원문은 어디에도 저장하지 않는다.
CREATE TABLE audience_account (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY,
    status            VARCHAR(30)  NOT NULL,
    name              TEXT         NOT NULL,
    birth_date        DATE         NOT NULL,
    email             VARCHAR(254) NOT NULL,
    email_lowercase   VARCHAR(254) NOT NULL,
    phone_number      VARCHAR(11)  NOT NULL,
    password_hash     TEXT         NOT NULL,
    signed_up_at      TIMESTAMPTZ  NOT NULL,
    email_verified_at TIMESTAMPTZ,
    CONSTRAINT audience_account_pk PRIMARY KEY (id),
    CONSTRAINT audience_account_status_ck
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    CONSTRAINT audience_account_email_lowercase_ck CHECK (email_lowercase = lower(email)),
    CONSTRAINT audience_account_email_verified_ck
        CHECK (status = 'WITHDRAWN' OR (status = 'PENDING_VERIFICATION') = (email_verified_at IS NULL))
);

-- 번호 하나에 계정 하나(docs/01 "전화번호", docs/99 C). 같은 번호로 가입한 계정이 있으면 가입할 수 없다.
-- 인증 대기 계정도 가입한 계정이므로 번호를 쥔다. 일반 탈퇴하면 번호가 풀린다.
-- 같은 번호로 동시에 가입해도 이 제약이 하나만 남긴다.
CREATE UNIQUE INDEX audience_account_phone_number_ux
    ON audience_account (phone_number)
    WHERE status <> 'WITHDRAWN';

-- 같은 이메일의 관객 계정은 하나뿐이다. 이메일이 로그인 아이디다(docs/01 "수집 정보 — 사용자").
-- 대소문자는 가리지 않는다(Seoyun@example.com과 seoyun@example.com은 같은 이메일). email은 적은 그대로 두고,
-- 비교는 PostgreSQL lower()로 소문자로 바꾼 email_lowercase로 한다. (lower(email)에 바로 거는 식 색인은 jOOQ 코드 생성이
-- 읽지 못해 칸을 따로 둔다. 두 칸이 어긋나지 않게 CHECK로 묶는다.)
-- 같은 이메일로 운영진 로그인을 따로 만드는 것은 된다 — 그쪽은 다른 표다(docs/01 "같은 이메일로 양쪽 가입").
CREATE UNIQUE INDEX audience_account_email_ux
    ON audience_account (email_lowercase)
    WHERE status <> 'WITHDRAWN';

-- 가입 동의 기록 — 항목·동의 여부·일시. 동의를 받았다는 근거다(docs/01 "약관과 동의 — 가입 시").
-- 가입 때 네 항목을 모두 남긴다(동의하지 않은 마케팅 수신도 '동의하지 않음'으로). 고치지 않고, 바뀌면 새 줄을 더한다(#18).
-- 보관: 계정과 같다 — 회원이 유지하는 동안, 탈퇴 시 파기(docs/11 "데이터 보관" — #83).
CREATE TABLE audience_consent_record (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY,
    audience_account_id BIGINT       NOT NULL,
    item                VARCHAR(50)  NOT NULL,
    agreed              BOOLEAN      NOT NULL,
    recorded_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT audience_consent_record_pk PRIMARY KEY (id),
    CONSTRAINT audience_consent_record_account_fk FOREIGN KEY (audience_account_id)
        REFERENCES audience_account (id),
    CONSTRAINT audience_consent_record_item_ck
        CHECK (item IN ('TERMS_OF_SERVICE', 'PERSONAL_INFORMATION_COLLECTION', 'MINIMUM_AGE_CONFIRMATION', 'MARKETING'))
);

CREATE INDEX audience_consent_record_account_ix ON audience_consent_record (audience_account_id);

-- 이메일 인증 링크 — 가입하면 하나 보낸다. 값은 추측할 수 없는 무작위 값이다(#5 TokenGenerator).
-- 인증한 뒤에도 남겨, 같은 링크를 다시 열면 "이미 인증을 마친 계정"으로 안내한다.
CREATE TABLE audience_email_verification_token (
    token               VARCHAR(64)  NOT NULL,
    audience_account_id BIGINT       NOT NULL,
    issued_at           TIMESTAMPTZ  NOT NULL,
    expires_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT audience_email_verification_token_pk PRIMARY KEY (token),
    CONSTRAINT audience_email_verification_token_account_fk FOREIGN KEY (audience_account_id)
        REFERENCES audience_account (id),
    CONSTRAINT audience_email_verification_token_period_ck CHECK (expires_at > issued_at)
);

CREATE INDEX audience_email_verification_token_account_ix ON audience_email_verification_token (audience_account_id);

-- 차단 번호 — 이용 정지된 계정이 탈퇴하며 남긴 번호. 이 번호로는 다시 가입할 수 없다(docs/01 "전화번호", "관객 이용 정지").
-- 가입을 막을 때는 '가입된 번호'와 같은 문구를 쓴다. 문구가 다르면 정지 이력이 드러난다.
-- 채우는 일은 회원 탈퇴(#83)가 한다.
CREATE TABLE audience_blocked_phone_number (
    phone_number VARCHAR(11)  NOT NULL,
    blocked_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT audience_blocked_phone_number_pk PRIMARY KEY (phone_number)
);
