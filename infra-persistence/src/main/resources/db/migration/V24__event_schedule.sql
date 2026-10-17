-- 행사의 일정 — 회차 방식 · 기본 운영 시간, 날짜, 회차 (#41)
-- 근거: docs/02 "날짜와 회차"(행사 → 날짜 → 회차, 재고는 회차에만 붙음), "회차형과 종일형 — 회차 방식", "회차의 시간 정보"(회차형 시작 필수 ·
--       종료 선택, 종일형 운영 시작~종료 필수 · 마지막 입장 선택), "출연 (선택)", "날짜마다 운영 시간이 다를 수 있습니다"(기본 운영 시간을 한 번
--       넣고 다른 날만 덮어쓴다), docs/05 "회차별 가격 조정"(금액 ± 또는 퍼센트 ±, 회차당 하나), docs/14 "무엇이 무엇을 가리키나"(티켓 → 회차,
--       취소 · 회수된 티켓만 남은 회차는 지워도 기록으로 남음), docs/03 "행사 만들기 위저드"(2단계)
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V25__...)을 추가한다.
--
-- 시각(TIME)은 그 날짜의 한국 시각이고 분 단위만 받는다(도메인 MinutePrecision, 확인 필요 #41 — 초가 붙은 값을 받으면 저장 뒤 다시 읽은 값과
-- 보낸 값이 어긋난다). 분 단위인지는 EXTRACT(SECOND ...)가 0인지로 본다 — 초와 그 아래를 함께 센다. 자정을 넘기는 회차 · 운영 시간은 받지 않는다
-- (종료 > 시작, 확인 필요 #36 · #135).
-- 동시성: 일정 · 날짜 · 회차를 고치는 흐름은 먼저 행사 줄을 FOR UPDATE로 잠근다(application의 EventEditingLocks) — 같은 행사의 일정 저장은
--   차례로 처리되고, 이 표들의 줄은 따로 잠그지 않는다. 줄을 넣을 때 거는 외래 키 확인 잠금은 이미 쥔 행사 줄과, 행사 줄을 쥔 흐름만 고치는
--   그 행사의 일정 · 날짜 줄에 걸린다.
-- CHECK와 NULL: PostgreSQL CHECK는 식이 NULL이면 통과하므로, NULL이 될 수 있는 칸은 비교 전에 IS NULL / IS NOT NULL로 먼저 가른다. 어긋난
--   CHECK가 여럿이면 이름 차례로 처음 것을 알리므로 CHECK마다 제 규칙 하나만 맡는다(칸이 있는지 · 차례 · 분 단위를 나눈다).
-- 보관: 지우지 않는다(삭제는 deleted_at). 행사와 같다.

-- 일정 한 줄 = 행사 하나의 회차 방식과 기본 운영 시간(도메인 EventSchedule). 행사에 하나다. 위저드 2단계에서 회차 방식을 처음 고를 때 생긴다 —
--   그 전에는 줄이 없다(회차 방식을 아직 고르지 않음).
-- 회차 방식(session_mode): 회차형(TIMED — 날짜 아래 회차 여러 개) / 종일형(ALL_DAY — 날짜 아래 회차 하나). 값은 도메인 enum 이름 그대로다.
--   날짜가 있으면 바꾸지 않는다(도메인이 본다).
-- 기본 운영 시간(default_*): 종일형만 있다 — 운영 시작 · 종료는 필수, 마지막 입장은 선택(비우면 운영 종료와 같게 처리). 덮어쓰지 않은 날짜는
--   모두 이 운영 시간이다. 회차형은 셋 다 없다 — 회차형 회차의 시간은 시작 · 종료다.
CREATE TABLE event_schedule (
    event_id                BIGINT      NOT NULL,
    session_mode            VARCHAR(20) NOT NULL,
    default_open_time       TIME,
    default_close_time      TIME,
    default_last_entry_time TIME,
    CONSTRAINT event_schedule_pk PRIMARY KEY (event_id),
    CONSTRAINT event_schedule_event_fk FOREIGN KEY (event_id)
        REFERENCES event (id),
    CONSTRAINT event_schedule_session_mode_ck CHECK (session_mode IN ('TIMED', 'ALL_DAY')),
    -- 종일형은 운영 시작 · 종료가 있고, 회차형은 운영 시간 칸이 모두 비어 있다. 마지막 입장만 있는 줄은 어느 쪽으로도 걸린다.
    -- session_mode는 NOT NULL이라 비교가 NULL이 되지 않는다
    CONSTRAINT event_schedule_default_hours_ck CHECK (
        (session_mode <> 'ALL_DAY' OR (default_open_time IS NOT NULL AND default_close_time IS NOT NULL))
        AND (session_mode <> 'TIMED'
             OR (default_open_time IS NULL AND default_close_time IS NULL AND default_last_entry_time IS NULL))),
    -- 운영 종료는 운영 시작보다 늦다(자정을 넘기는 운영은 받지 않는다)
    CONSTRAINT event_schedule_default_close_ck CHECK (
        default_open_time IS NULL OR default_close_time IS NULL OR default_close_time > default_open_time),
    -- 마지막 입장은 운영 시작보다 늦고 운영 종료보다 늦지 않다
    CONSTRAINT event_schedule_default_last_entry_ck CHECK (
        default_last_entry_time IS NULL OR default_open_time IS NULL OR default_close_time IS NULL
        OR (default_last_entry_time > default_open_time AND default_last_entry_time <= default_close_time)),
    CONSTRAINT event_schedule_default_minute_ck CHECK (
        (default_open_time IS NULL OR EXTRACT(SECOND FROM default_open_time) = 0)
        AND (default_close_time IS NULL OR EXTRACT(SECOND FROM default_close_time) = 0)
        AND (default_last_entry_time IS NULL OR EXTRACT(SECOND FROM default_last_entry_time) = 0))
);

-- 날짜 한 줄 = 일정의 한 날짜(도메인 EventDate). 날짜를 가리켜야 하는 것이 셋(1인 총 매수 날짜별 · 운영 시간 · 묶음권)이라 회차 위에 둔다.
-- calendar_date: 한국 날짜.
-- 덮어쓴 운영 시간(override_*): 종일형에서 그날만 다른 운영 시간 — 운영 시작 · 종료가 함께 있거나 셋 다 없다(없으면 기본 운영 시간을
--   따른다). 회차형 날짜는 늘 없다 — 일정의 회차 방식은 표를 건너 볼 수 없어 도메인이 본다.
-- deleted_at: 날짜를 지운 시각. 줄은 지우지 않는다 — 지운 회차가 그 날짜를 계속 가리킨다(취소 · 회수된 티켓 → 회차 → 날짜). 회차가 남지 않은
--   날짜는 두지 않으므로 마지막 회차를 지우거나 다른 날짜로 옮기면 그 날짜에도 지운 시각을 적는다. 지운 날짜의 날짜 값은 다시 쓸 수 있다.
-- 차례: 일정은 날짜 순으로 보인다.
CREATE TABLE event_date (
    id                       BIGINT      GENERATED ALWAYS AS IDENTITY,
    event_id                 BIGINT      NOT NULL,
    calendar_date            DATE        NOT NULL,
    override_open_time       TIME,
    override_close_time      TIME,
    override_last_entry_time TIME,
    deleted_at               TIMESTAMPTZ,
    CONSTRAINT event_date_pk PRIMARY KEY (id),
    CONSTRAINT event_date_schedule_fk FOREIGN KEY (event_id)
        REFERENCES event_schedule (event_id),
    -- 회차가 가리키는 날짜가 같은 행사의 날짜인지 표가 볼 수 있게 — event_session_date_fk가 이것을 가리킨다
    CONSTRAINT event_date_event_id_uk UNIQUE (event_id, id),
    -- 덮어쓴 운영 시간은 운영 시작 · 종료가 함께 있거나 함께 없다. 마지막 입장만 있는 줄은 받지 않는다
    CONSTRAINT event_date_override_hours_ck CHECK (
        ((override_open_time IS NULL AND override_close_time IS NULL AND override_last_entry_time IS NULL)
         OR (override_open_time IS NOT NULL AND override_close_time IS NOT NULL))),
    CONSTRAINT event_date_override_close_ck CHECK (
        override_open_time IS NULL OR override_close_time IS NULL OR override_close_time > override_open_time),
    CONSTRAINT event_date_override_last_entry_ck CHECK (
        override_last_entry_time IS NULL OR override_open_time IS NULL OR override_close_time IS NULL
        OR (override_last_entry_time > override_open_time AND override_last_entry_time <= override_close_time)),
    CONSTRAINT event_date_override_minute_ck CHECK (
        (override_open_time IS NULL OR EXTRACT(SECOND FROM override_open_time) = 0)
        AND (override_close_time IS NULL OR EXTRACT(SECOND FROM override_close_time) = 0)
        AND (override_last_entry_time IS NULL OR EXTRACT(SECOND FROM override_last_entry_time) = 0))
);

-- 한 행사의 지우지 않은 날짜끼리는 날짜 값이 겹치지 않는다(도메인 EventSchedule). 지운 날짜는 빼고 본다. 행사의 일정을 읽을 때(지우지 않은
-- 날짜)도 이 색인을 쓴다.
CREATE UNIQUE INDEX event_date_event_calendar_date_ux ON event_date (event_id, calendar_date)
    WHERE deleted_at IS NULL;

-- 회차 한 줄 = 날짜 아래에서 재고가 붙는 유일한 곳(도메인 EventSession). 재고(회차 × 좌석, 회차 × 구역) · 티켓 · 입장 기록이 이 줄의 id를
--   가리킨다 — 회차의 정체성은 id다. 회차의 일시를 바꿔도(다른 날짜로 옮겨도) 줄과 id는 그대로이고 date_id만 바뀐다(docs/04 "기존 회차의 날짜를
--   고칩니다 — 티켓이 그대로 붙어 있어 자동 이전이 공짜").
-- event_id: 그 날짜의 행사. 날짜가 같은 행사의 것인지 외래 키가 함께 본다(event_session_date_fk). 행사의 회차를 한꺼번에 읽을 때도 쓴다.
-- 시간(start_time · end_time): 회차형만 있다 — 시작 필수, 종료 선택(러닝타임). 종일형 회차는 둘 다 없다 — 그날의 운영 시간을 쓴다. 일정의 회차
--   방식은 표를 건너 볼 수 없어 도메인이 본다. 종료는 시작보다 늦다. 종료 시각은 그 회차의 시간 수정이 잠기는 시점(시작 1주일 전)부터 비어
--   있으면 입력을 요구한다(도메인 SessionTimes).
-- 출연(performers): 선택. 자유 입력 한 줄("김배우 · 박배우"). 앞뒤 공백을 걷고 NFC로 맞춘 그대로, 100자까지(ProposedPolicy
--   .SESSION_PERFORMERS_MAXIMUM_LENGTH, 확인 필요 #36). 적지 않았으면 NULL — 빈 글자는 없다. 줄바꿈이 없는지는 도메인이 본다.
-- 가격 조정(price_adjustment_kind · price_adjustment_value): 회차당 하나, 모든 구역에 같이 적용(docs/14). 종류는 조정 없음(NONE — 값 없음) /
--   금액(AMOUNT — 원, 0이 아닌 ±) / 퍼센트(PERCENT — 0이 아니고 -100보다 큰 정수 퍼센트). 값은 도메인 enum 이름 그대로다. 조정한 가격의 계산은
--   가격 계산(#48)이 한다.
-- cancelled_at: 회차를 취소한 시각. 비어 있으면 취소되지 않은 회차다. 취소된 회차도 일정에 남아 같은 자리(같은 날짜의 같은 시작 시각)를
--   차지한다. 회차를 취소하는 일은 회차 취소(#98)가 한다.
-- deleted_at: 회차를 지운 시각. 줄은 지우지 않는다 — 취소 · 회수된 티켓이 원래 회차를 계속 가리킨다(docs/04, docs/14). 지운 회차는 일정에 없는
--   회차로 다룬다(읽지 않는다). 지운 회차의 시작 시각은 다시 쓸 수 있다.
-- 차례: 일정은 날짜 순, 같은 날짜는 시작 시각 순으로 보인다.
CREATE TABLE event_session (
    id                     BIGINT       GENERATED ALWAYS AS IDENTITY,
    event_id               BIGINT       NOT NULL,
    date_id                BIGINT       NOT NULL,
    start_time             TIME,
    end_time               TIME,
    performers             VARCHAR(100),
    price_adjustment_kind  VARCHAR(20)  NOT NULL,
    price_adjustment_value BIGINT,
    cancelled_at           TIMESTAMPTZ,
    deleted_at             TIMESTAMPTZ,
    CONSTRAINT event_session_pk PRIMARY KEY (id),
    CONSTRAINT event_session_date_fk FOREIGN KEY (event_id, date_id)
        REFERENCES event_date (event_id, id),
    -- 종료 시각만 있는 회차는 없다 — 회차형은 시작이 필수다
    CONSTRAINT event_session_time_ck CHECK (end_time IS NULL OR start_time IS NOT NULL),
    -- 종료는 시작보다 늦다(자정을 넘기는 회차는 받지 않는다)
    CONSTRAINT event_session_end_after_start_ck CHECK (start_time IS NULL OR end_time IS NULL OR end_time > start_time),
    CONSTRAINT event_session_minute_ck CHECK (
        (start_time IS NULL OR EXTRACT(SECOND FROM start_time) = 0)
        AND (end_time IS NULL OR EXTRACT(SECOND FROM end_time) = 0)),
    CONSTRAINT event_session_performers_ck CHECK (performers IS NULL OR char_length(performers) > 0),
    CONSTRAINT event_session_price_adjustment_kind_ck CHECK (price_adjustment_kind IN ('NONE', 'AMOUNT', 'PERCENT')),
    -- 종류마다의 값 — 조정 없음은 값이 없고, 금액 · 퍼센트는 0이 아닌 값이 있다. 퍼센트는 -100보다 크고 정수 범위 안이다(도메인 int).
    -- price_adjustment_value가 NULL이면 금액 · 퍼센트 갈래가 (FALSE AND NULL) = FALSE가 되어 비교식의 NULL로 빠져나가지 않는다. 종류는 NOT
    -- NULL이라 비교가 NULL이 되지 않고, 모르는 종류는 종류 CHECK로만 걸린다
    CONSTRAINT event_session_price_adjustment_value_ck CHECK (
        (price_adjustment_kind <> 'NONE' OR price_adjustment_value IS NULL)
        AND (price_adjustment_kind <> 'AMOUNT' OR (price_adjustment_value IS NOT NULL AND price_adjustment_value <> 0))
        AND (price_adjustment_kind <> 'PERCENT'
             OR (price_adjustment_value IS NOT NULL AND price_adjustment_value <> 0 AND price_adjustment_value > -100
                 AND price_adjustment_value <= 2147483647)))
);

-- 같은 날짜에 시작 시각이 같은 회차 둘은 없다(회차형, 도메인 EventSchedule — 도면이 행사에 하나라 같은 시각의 회차 둘은 관객에게 같은 회차로
-- 보인다). 취소된 회차도 자리를 차지하고, 지운 회차만 뺀다. 시작 시각이 없는 종일형 회차는 이 색인에 걸리지 않는다(NULL끼리는 겹치지 않는다).
CREATE UNIQUE INDEX event_session_date_start_time_ux ON event_session (date_id, start_time)
    WHERE deleted_at IS NULL;

-- 시간이 없는 회차(종일형)는 날짜마다 하나다 — 종일형에는 시간대 분할이 없다(docs/02 "시간을 쪼개려면 회차형으로 등록합니다").
CREATE UNIQUE INDEX event_session_date_all_day_ux ON event_session (date_id)
    WHERE deleted_at IS NULL AND start_time IS NULL;

-- 행사의 지우지 않은 회차를 한꺼번에 읽을 때(일정 읽기) 쓴다.
CREATE INDEX event_session_event_ix ON event_session (event_id)
    WHERE deleted_at IS NULL;
