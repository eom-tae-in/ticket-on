-- 비밀번호 재설정 링크 — 비밀번호 찾기 (#16)
-- 근거: docs/01 "같은 이메일로 양쪽 가입"(비밀번호 찾기 — 어느 쪽인지 물음, 가입 여부와 상관없이 같은 안내),
--       docs/99 D "비밀번호 찾기 안내", docs/13 "로그인 / 회원가입 — 비밀번호 찾기"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V7__...)을 추가한다.

-- 링크 한 줄 = 비밀번호 찾기로 보낸 메일 한 통의 링크. 값은 추측할 수 없는 무작위 값이다(#5 TokenGenerator).
-- 한 이메일에 관객 계정과 운영진 로그인이 둘 다 있을 수 있어 비밀번호 찾기에서 어느 쪽인지 고르고, 링크는 고른 쪽 계정 하나만 가리킨다.
-- 그래서 관객 계정(audience_account)과 운영진 로그인(staff_account)을 가리키는 칸을 따로 두고, 둘 중 꼭 하나만 채운다.
-- 계정 id는 종류마다 따로 매기므로(관객 7번과 운영진 7번은 다른 사람) 종류를 칸으로 가린다 — 관객 쪽 링크로 운영진 로그인의
-- 비밀번호를 바꾸지 못한다. 외래 키로 없는 계정을 가리키지 못하게 한다.
-- 유효 시간은 보낸 때부터 1시간이다(ProposedPolicy.PASSWORD_RESET_LINK_VALIDITY, 확인 필요 #16 — 문서에 없어 제안값).
-- 링크는 한 번만 쓴다(used_at). 비밀번호 찾기를 다시 하면 아직 쓰지 않은 이전 링크는 대체된다(replaced_at) — 쓸 수 있는 링크는 계정마다
-- 많아야 하나다. 쓴 링크·대체된 링크도 남겨, 다시 열면 왜 쓸 수 없는지 알려 준다.
-- 보관: 계정과 같다. 탈퇴했을 때 무엇을 지우는지는 회원 탈퇴를 만드는 이슈(#83·#26)가 정한다.
CREATE TABLE password_reset_token (
    token               VARCHAR(64)  NOT NULL,
    audience_account_id BIGINT,
    staff_account_id    BIGINT,
    issued_at           TIMESTAMPTZ  NOT NULL,
    expires_at          TIMESTAMPTZ  NOT NULL,
    used_at             TIMESTAMPTZ,
    replaced_at         TIMESTAMPTZ,
    CONSTRAINT password_reset_token_pk PRIMARY KEY (token),
    CONSTRAINT password_reset_token_audience_account_fk FOREIGN KEY (audience_account_id)
        REFERENCES audience_account (id),
    CONSTRAINT password_reset_token_staff_account_fk FOREIGN KEY (staff_account_id)
        REFERENCES staff_account (id),
    CONSTRAINT password_reset_token_one_account_ck
        CHECK ((audience_account_id IS NULL) <> (staff_account_id IS NULL)),
    CONSTRAINT password_reset_token_period_ck CHECK (expires_at > issued_at),
    CONSTRAINT password_reset_token_used_or_replaced_ck CHECK (used_at IS NULL OR replaced_at IS NULL)
);

-- 쓸 수 있는(쓰지도 대체되지도 않은) 링크는 계정마다 하나뿐이다. 새 링크를 저장하기 전에 저장소가 그 계정을 잠그고 이전 링크를 대체하므로
-- 보통은 걸리지 않는다. 이 색인은 그 약속이 깨지지 않게 하는 마지막 장치다. 계정의 링크를 찾는 색인도 겸한다.
CREATE UNIQUE INDEX password_reset_token_audience_unused_ux
    ON password_reset_token (audience_account_id)
    WHERE used_at IS NULL AND replaced_at IS NULL;

CREATE UNIQUE INDEX password_reset_token_staff_unused_ux
    ON password_reset_token (staff_account_id)
    WHERE used_at IS NULL AND replaced_at IS NULL;

CREATE INDEX password_reset_token_audience_account_ix ON password_reset_token (audience_account_id);

CREATE INDEX password_reset_token_staff_account_ix ON password_reset_token (staff_account_id);
