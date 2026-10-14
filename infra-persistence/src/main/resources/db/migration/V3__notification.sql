-- 알림 발송 이력과 관객 알림 수신 설정 (#13)
-- 근거: docs/12 "원칙", "관객 대상", "주최자 대상", "알림 설정", "해제할 수 없는 알림은 목록에 넣지 않습니다",
--       docs/11 "데이터 보관"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V4__...)을 추가한다.

-- 발송 이력 한 줄 = 알림 한 건을 한 수단으로 한 곳에 보낸 결과.
-- 이메일·문자를 모두 보내는 알림은 두 줄이 남는다. 한 수단이 실패해도 다른 수단은 그대로 보내고 각각 남긴다
-- (docs/12 "긴급 통지는 이메일과 문자를 모두 보내므로 한쪽이 실패해도 전달될 가능성이 남습니다").
-- recipient_address는 받는 곳 — 이메일 주소 또는 숫자만 남긴 전화번호다. 초대권 받는 사람·주최자 운영진처럼 관객 계정이 없는
-- 받는 사람도 있어 계정이 아니라 받는 곳을 남긴다.
-- kind는 알림 종류 이름(NotificationKind) 그대로다. 종류가 50개에 가까워 CHECK로 묶지 않는다.
-- 실패하면 사유를 남기고, 성공이면 사유가 없다.
-- 보관: 부가 기록이라 90일 뒤 파기한다(docs/11 "데이터 보관", docs/99 C). 파기 작업(#32)이 sent_at으로 고른다.
CREATE TABLE notification_send_record (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY,
    kind              VARCHAR(60)  NOT NULL,
    channel           VARCHAR(10)  NOT NULL,
    recipient_address VARCHAR(254) NOT NULL,
    succeeded         BOOLEAN      NOT NULL,
    failure_reason    TEXT,
    sent_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT notification_send_record_pk PRIMARY KEY (id),
    CONSTRAINT notification_send_record_channel_ck CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT notification_send_record_failure_reason_ck CHECK (succeeded = (failure_reason IS NULL))
);

CREATE INDEX notification_send_record_sent_at_ix ON notification_send_record (sent_at);

-- 관객 알림 수신 설정 — 끌 수 있는 세 묶음 가운데 관객이 바꾼 적 있는 묶음만 한 줄씩 둔다(docs/12 "알림 설정").
-- 줄이 없는 묶음은 기본값(받음 — 거래 알림이라 받는 게 기본)을 따른다. 계정 단위 설정이다(행사마다 두지 않음).
-- 끌 수 없는 알림(행사 변경·취소·연기·장소·시간·출연자 변경·공연 중단)은 묶음이 아니라 여기 들어올 수 없다.
-- 묶음 하나를 바꿀 때 그 줄만 고친다 — 두 묶음을 거의 동시에 바꿔도 서로 덮어쓰지 않는다.
-- 보관: 계정과 같다 — 회원이 유지하는 동안(docs/11 "데이터 보관").
CREATE TABLE audience_notification_preference (
    audience_account_id BIGINT      NOT NULL,
    preference          VARCHAR(30) NOT NULL,
    receives            BOOLEAN     NOT NULL,
    CONSTRAINT audience_notification_preference_pk PRIMARY KEY (audience_account_id, preference),
    CONSTRAINT audience_notification_preference_account_fk FOREIGN KEY (audience_account_id)
        REFERENCES audience_account (id),
    CONSTRAINT audience_notification_preference_preference_ck
        CHECK (preference IN ('BOOKING_CONFIRMATION', 'DAY_BEFORE_REMINDER', 'BOOKING_OPEN'))
);
