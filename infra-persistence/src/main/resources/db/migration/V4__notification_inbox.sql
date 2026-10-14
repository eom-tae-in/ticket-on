-- 관객 알림함 (#14)
-- 근거: docs/12 "전달 수단과 알림함", "전화번호는 인증하지 않습니다", docs/99 D "알림함 읽음 표시", docs/14 "관객 계정 ─ 알림함"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V5__...)을 추가한다.

-- 알림함 한 줄 = 관객 계정에 보낸 알림 한 건을 같은 내용으로 한 벌 더 쌓아 둔 기록.
-- 이메일·문자를 모두 보낸 알림도 한 줄이다(발송 이력은 수단마다 한 줄 — notification_send_record).
-- 문자·이메일이 실패해도 남는다(docs/12 "문자가 실패해도 알림함에는 그대로 남습니다"). 관객이 끈 알림처럼 보내지 않은 알림은 없다.
-- 연락처만 아는 받는 사람(초대권 등)과 주최자 쪽 알림은 알림함이 없어 여기 들어오지 않는다.
-- kind는 알림 종류 이름(NotificationKind) 그대로다. 관객 쪽 종류만 들어온다. 종류가 많아 CHECK로 묶지 않는다.
-- title·body는 보낸 제목·본문 그대로다.
-- first_seen_at은 처음 본 시각 — 알림함을 처음 연 순간에 채우고 다시 바꾸지 않는다. 비어 있으면 '새로 온 것'이다.
-- 안 읽은 개수(탭 배지)는 두지 않는다(docs/12).
-- 목록은 계정마다 최신순(sent_at 내림차순)으로 읽는다.
-- 보관: 기간을 아직 정하지 않았다(#32). 그때까지 파기하지 않는다.
CREATE TABLE audience_notification_inbox (
    id                  BIGINT      GENERATED ALWAYS AS IDENTITY,
    audience_account_id BIGINT      NOT NULL,
    kind                VARCHAR(60) NOT NULL,
    title               TEXT        NOT NULL,
    body                TEXT        NOT NULL,
    sent_at             TIMESTAMPTZ NOT NULL,
    first_seen_at       TIMESTAMPTZ,
    CONSTRAINT audience_notification_inbox_pk PRIMARY KEY (id),
    CONSTRAINT audience_notification_inbox_account_fk FOREIGN KEY (audience_account_id)
        REFERENCES audience_account (id)
);

CREATE INDEX audience_notification_inbox_account_sent_at_ix
    ON audience_notification_inbox (audience_account_id, sent_at);
