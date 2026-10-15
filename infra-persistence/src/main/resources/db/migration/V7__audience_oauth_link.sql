-- 관객 카카오·구글 로그인 — 제공자 계정 연결, 비밀번호 선택값 (#17)
-- 근거: docs/01 "가입 경로 — OAuth", "OAuth는 중복 계정을 막지 못합니다", "계정 연동", docs/16 "로그인"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V8__...)을 추가한다.

-- 카카오·구글로 가입한 관객은 비밀번호가 없다. 비밀번호 찾기로 나중에 정할 수 있다(#16).
-- 비밀번호가 없으면 이메일·비밀번호 로그인은 늘 실패한다(틀린 비밀번호와 같은 문구).
ALTER TABLE audience_account ALTER COLUMN password_hash DROP NOT NULL;

-- 제공자 계정 연결 한 줄 = 이 카카오·구글 계정으로 로그인하면 이 관객 계정으로 들어온다.
-- 연결은 OAuth로 가입할 때 생기거나, 이미 로그인한 관객이 직접 만든다. 이메일이 같다고 저절로 만들지 않는다(docs/01 "계정 연동").
-- 제공자 계정은 제공자와 제공자가 정한 사용자 id(구글 sub, 카카오 회원번호)로 가린다. 이메일은 바뀔 수 있어 쓰지 않는다.
-- 제공자에게 받은 토큰은 저장하지 않는다 — 로그인할 때 사용자 정보를 읽는 데만 쓴다.
-- 보관: 계정과 같다. 탈퇴하면 지운다(회원 탈퇴 #83 — 남겨 두면 같은 카카오·구글 계정으로 다시 가입할 수 없다).
CREATE TABLE audience_oauth_link (
    provider            VARCHAR(20)  NOT NULL,
    provider_user_id    VARCHAR(255) NOT NULL,
    audience_account_id BIGINT       NOT NULL,
    linked_at           TIMESTAMPTZ  NOT NULL,
    -- 제공자 계정 하나는 관객 계정 하나에만 연결된다
    CONSTRAINT audience_oauth_link_pk PRIMARY KEY (provider, provider_user_id),
    CONSTRAINT audience_oauth_link_account_fk FOREIGN KEY (audience_account_id)
        REFERENCES audience_account (id),
    CONSTRAINT audience_oauth_link_provider_ck CHECK (provider IN ('KAKAO', 'GOOGLE')),
    -- 관객 계정 하나에는 제공자마다 하나씩만 연결된다. 계정의 연결을 찾는 색인도 겸한다
    CONSTRAINT audience_oauth_link_account_provider_uk UNIQUE (audience_account_id, provider)
);
