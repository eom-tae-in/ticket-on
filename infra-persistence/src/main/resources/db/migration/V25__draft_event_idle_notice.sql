-- 작성 중 행사 30일 미수정 알림을 보낸 기록 (#45)
-- 근거: docs/04 "작성 중 행사 관리"(30일 미수정 → 알림, 자동 삭제는 하지 않음), docs/12 "주최자 대상 — 작성 중 행사 30일 미수정",
--       docs/99 C "작성 중 행사 알림 — 30일 미수정"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V26__...)을 추가한다.
--
-- 알림 한 줄 = 행사 하나의 미수정 구간 하나에 보낸 알림(도메인 DraftEventNudges). 구간은 행사의 마지막 수정 시각(event.last_modified_at)으로
--   가린다 — 알린 뒤에도 고치지 않으면 마지막 수정 시각이 그대로라 같은 구간이고, 고치면 마지막 수정 시각이 바뀌어 새 구간이 시작된다.
-- 같은 구간에는 한 번만 알린다 — (행사, 마지막 수정 시각)이 기본 키라, 정해진 시각 작업이 다시 돌거나 두 벌이 동시에 돌아도 DB가 한 줄만 받는다.
--   작업은 이 줄을 넣는 데 성공했을 때만(ON CONFLICT DO NOTHING이 한 줄을 넣었을 때만) 메일을 보내고, 줄과 메일 발송 이력은 같은 트랜잭션에
--   든다 — 메일 발송이 되돌려지면 줄도 되돌려져 다음 작업이 다시 보낸다.
-- last_modified_at: 알린 구간의 마지막 수정 시각 — 알릴 때의 event.last_modified_at 그대로.
-- notified_at: 알린 시각. 구간이 시작된 뒤다(30일이 지났는지는 도메인이 본다 — 30일은 정책값이라 표에 두지 않는다).
-- 동시성: 작업은 행사 줄을 FOR KEY SHARE로 잡고 상태 · 마지막 수정 시각을 다시 읽은 뒤 이 줄을 넣는다 — 같은 행사의 위저드 저장(행사 줄 FOR
--   UPDATE)과 차례로 처리되어, 막 고친 행사에 묵은 구간의 알림이 가지 않는다. 이 줄의 외래 키 확인 잠금은 이미 쥔 그 행사 줄에 걸린다
--   (application의 StaffAccountLocks 표).
-- 보관: 지우지 않는다. 행사와 같다(행사는 지우지 않고 deleted_at을 적는다).
CREATE TABLE draft_event_idle_notice (
    event_id         BIGINT      NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    notified_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT draft_event_idle_notice_pk PRIMARY KEY (event_id, last_modified_at),
    CONSTRAINT draft_event_idle_notice_event_fk FOREIGN KEY (event_id)
        REFERENCES event (id),
    -- 알림은 구간이 시작된 뒤에 간다. 두 칸 모두 NOT NULL이라 비교가 NULL이 되지 않는다
    CONSTRAINT draft_event_idle_notice_notified_after_idle_ck CHECK (notified_at > last_modified_at)
);
