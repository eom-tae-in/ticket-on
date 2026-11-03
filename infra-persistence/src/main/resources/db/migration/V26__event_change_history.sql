-- 제목 · 소개 · 포스터 · 회차 출연의 변경 이력 (#46)
-- 근거: docs/03 "제목 · 소개 · 포스터는 변경 이력을 남깁니다"(버전별로 기간과 함께 보관, 이력에 남길 것 — 변경 일시 · 변경 전 · 변경 후 · 통지 발송
--       여부 · 누가 바꿨는지(운영진 닉네임)), "보관은 행사가 끝난 날부터 6개월", "블록 이름은 대상이 아닙니다", docs/02 "출연 (선택)"(변경 이력),
--       docs/04 "행사가 끝난 뒤 일어나는 일"(6개월 — 변경 이력 삭제), docs/14 "무엇이 무엇을 가리키나"(변경 이력 → 행사),
--       docs/99 C "보관 기간", D "변경 이력 보관 기준점", "변경 이력 대상", "회차별 출연"
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V27__...)을 추가한다.
--
-- 이력 한 줄 = 한 대상 칸의 버전 하나(도메인 EventChangeHistoryVersion). 덮어쓰지 않고 쌓는다 — 바꾸면 그 칸의 지금 버전의 유효 끝을 그 순간으로
--   닫고, 그 순간부터 유효한 새 버전을 넣는다. "3/1~3/10 유효"처럼 예매 시각과 대조하면 관객이 어느 버전을 보고 샀는지 안다. 유효 기간은
--   [valid_from, valid_until)이다 — 끝 순간에는 다음 버전이 유효하다.
-- 언제 쌓나: 판매를 시작해 판매 대기로 넘어간(공개된) 순간의 내용이 첫 버전이고(바꾸기 전 없음), 그 뒤 바꿀 때마다 새 버전이다. 작성 중의 변경은
--   쌓지 않는다(도메인 EventChangeHistory, 확인 필요 #46). 블록 이름은 대상이 아니다.
-- 대상 칸(target): 제목(TITLE) / 소개(INTRODUCTION) / 포스터(POSTER) / 회차 출연(SESSION_PERFORMERS). 값은 도메인 enum 이름 그대로다. 회차 출연만
--   회차(session_id)를 가리키고 회차마다 따로 쌓는다. 회차는 같은 행사의 것이다(event_change_history_session_fk). 지운 회차의 이력도 남는다(회차 줄은
--   지우지 않고 지운 시각을 적는다 — V24).
-- 내용(previous_content · new_content): 바꾸기 전 · 바꾼 뒤. 제목 · 소개 · 출연은 글 그대로, 포스터는 파일 열쇠(event.poster_file_key와 같은 꼴 —
--   V21)다. 바꾸기 전이 없으면(첫 버전, 비어 있던 출연을 채움) previous_content가 NULL이고, 출연을 비우면 new_content가 NULL이다. 제목 · 소개 ·
--   포스터는 비울 수 없다. 바꾸기 전과 바꾼 뒤가 같은 줄은 버전이 아니다.
--   포스터의 바꾸기 전 파일은 이력의 근거라 보관 기간 동안 파일 저장소에서 지우지 않고, 이 표의 줄을 지울 때 함께 지운다(application의 이력 삭제).
-- 유효 기간(valid_from · valid_until): valid_from이 docs/03의 "변경 일시"다. 지금 버전은 valid_until이 NULL이다 — 한 칸(회차 출연은 회차마다)에
--   지금 버전은 하나뿐이다(아래 유일 색인 두 개). 같은 순간에 잇달아 바꾸면 길이가 0인 버전이 남을 수 있어 끝 = 시작도 받는다.
-- 바꾼 사람(changed_by_staff_account_id · changed_by_nickname): 바꾼 운영진의 계정과 그때의 닉네임. 닉네임은 바꿀 수 있고 관리자가 초기화할 수도
--   있어(V5, docs/99 C "운영진 닉네임 — 변경 가능") 그때 보이던 이름을 그대로 남긴다. 첫 버전은 판매를 시작한 운영진이다.
-- 통지 발송 여부(notice_sent): 예매자에게 알렸는지. 지금은 늘 '안 보냄'(FALSE)으로 넣는다 — '예매자에게 알릴까요?'(#110)와 출연자 변경 알림(#103)이
--   실제 값을 채운다. "바꿨는데 안 알렸다"가 이 칸으로 드러난다(docs/04 "예매자에게 알리기").
-- 동시성: 쌓는 흐름(위저드 1 · 2단계 저장, 포스터 올리기, 판매 시작)은 행사 줄을 FOR UPDATE로 잡은 채 지금 버전을 닫고 새 버전을 넣는다 — 같은
--   행사의 버전은 그 줄에서 차례로 쌓인다. 넣을 때 거는 외래 키 확인 잠금은 이미 쥔 행사 줄 · 운영진 계정 줄(KEY SHARE)과, 행사 줄을 쥔 흐름만
--   고치는 그 행사의 회차 줄에 걸린다. 지우는 흐름(정해진 시각 작업)은 행사 줄을 FOR KEY SHARE로 잡아 쌓는 흐름과 차례로 처리된다
--   (application의 StaffAccountLocks 표).
-- CHECK와 NULL: session_id · previous_content · new_content · valid_until은 NULL이 될 수 있어 IS NULL / IS NOT NULL로 먼저 가르거나 NULL을 같은
--   값으로 보는 IS DISTINCT FROM으로 견준다. 대상 칸 값 자체는 event_change_history_target_ck만 맡는다(나머지 CHECK는 칸마다의 조건으로 적어,
--   모르는 칸은 대상 칸 CHECK로만 걸린다).
-- 보관: 행사가 끝난 날(취소되지 않은 마지막 회차가 끝난 한국 날짜)부터 6개월(RecordRetentionPolicy.CHANGE_HISTORY_RETENTION_AFTER_EVENT_END)이
--   지나면 정해진 시각 작업이 그 행사의 줄을 모두 지운다 — 바꾼 날부터 세지 않는다(docs/99 D "변경 이력 보관 기준점").

-- 이력이 회차를 (회차 id, 행사 id)로 가리켜 이력의 행사와 회차의 행사가 늘 같게 한다(event_change_history_session_fk).
ALTER TABLE event_session ADD CONSTRAINT event_session_id_event_uk UNIQUE (id, event_id);

CREATE TABLE event_change_history (
    id                          BIGINT      GENERATED ALWAYS AS IDENTITY,
    event_id                    BIGINT      NOT NULL,
    target                      VARCHAR(30) NOT NULL,
    session_id                  BIGINT,
    previous_content            TEXT,
    new_content                 TEXT,
    valid_from                  TIMESTAMPTZ NOT NULL,
    valid_until                 TIMESTAMPTZ,
    changed_by_staff_account_id BIGINT      NOT NULL,
    changed_by_nickname         TEXT        NOT NULL,
    notice_sent                 BOOLEAN     NOT NULL,
    CONSTRAINT event_change_history_pk PRIMARY KEY (id),
    CONSTRAINT event_change_history_event_fk FOREIGN KEY (event_id)
        REFERENCES event (id),
    CONSTRAINT event_change_history_session_fk FOREIGN KEY (session_id, event_id)
        REFERENCES event_session (id, event_id),
    CONSTRAINT event_change_history_changed_by_fk FOREIGN KEY (changed_by_staff_account_id)
        REFERENCES staff_account (id),
    CONSTRAINT event_change_history_target_ck CHECK (target IN ('TITLE', 'INTRODUCTION', 'POSTER', 'SESSION_PERFORMERS')),
    -- 회차 출연만 회차를 가리킨다
    CONSTRAINT event_change_history_session_ck CHECK (
        (target <> 'SESSION_PERFORMERS' OR session_id IS NOT NULL)
        AND (target = 'SESSION_PERFORMERS' OR session_id IS NULL)),
    -- 제목 · 소개 · 포스터는 비울 수 없다. 출연만 비운 버전이 있다
    CONSTRAINT event_change_history_new_content_ck CHECK (target = 'SESSION_PERFORMERS' OR new_content IS NOT NULL),
    -- 내용이 있으면 빈 글자가 아니다
    CONSTRAINT event_change_history_content_not_empty_ck CHECK (
        (previous_content IS NULL OR char_length(previous_content) > 0)
        AND (new_content IS NULL OR char_length(new_content) > 0)),
    -- 바꾸기 전과 바꾼 뒤가 같으면 버전이 아니다. 둘 다 NULL인 것도 같은 것으로 본다
    CONSTRAINT event_change_history_changed_ck CHECK (previous_content IS DISTINCT FROM new_content),
    CONSTRAINT event_change_history_period_ck CHECK (valid_until IS NULL OR valid_until >= valid_from),
    CONSTRAINT event_change_history_changed_by_nickname_ck CHECK (char_length(changed_by_nickname) > 0)
);

-- 행사에 하나인 칸(제목 · 소개 · 포스터)은 행사마다 지금 버전이 하나다.
CREATE UNIQUE INDEX event_change_history_current_event_target_ux ON event_change_history (event_id, target)
    WHERE session_id IS NULL AND valid_until IS NULL;

-- 회차 출연은 회차마다 지금 버전이 하나다. 회차 id는 행사를 건너 겹치지 않는다.
CREATE UNIQUE INDEX event_change_history_current_session_ux ON event_change_history (session_id)
    WHERE session_id IS NOT NULL AND valid_until IS NULL;

-- 한 행사의 이력을 쌓은 차례로 읽고(관리자의 신고 확인), 행사마다 지운다(보관 기간이 지나면).
CREATE INDEX event_change_history_event_ix ON event_change_history (event_id, id);
