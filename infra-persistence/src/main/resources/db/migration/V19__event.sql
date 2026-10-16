-- 행사 · 정식 카테고리 · 취소 규정 구간 · 판매 중단 이력 (#39)
-- 근거: docs/03 "기본 정보", "카테고리", "공개 수준 3단계", "재입장 설정", "연령 제한", "검표 시작 시각", "행사 만들기 위저드"(임시저장은
--       자동으로), docs/04 "행사 상태", "'중단했던 이력'은 따로 남깁니다", "작성 중 행사 관리", "행사 삭제", docs/06 "오픈 시각",
--       "예매 종료", "매수 제한", "회차 선택 제약", docs/08 "규정은 기준 고정, 관대한 방향만 조정 가능", docs/14 "무엇이 무엇을 가리키나"
--       (행사 → 주최자)
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V20__...)을 추가한다.

-- 정식 카테고리 한 줄 = 유형별 목록에서 고르는 항목 하나(도메인 EventCategory). 예: 공연의 "연극".
-- enum이 아니라 데이터다 — 관리자가 '기타'의 자유 입력 장르명 가운데 자주 나오는 것("낭독극" 등)을 정식 카테고리로 승격하면 줄이 는다
-- (docs/03 "카테고리", docs/13). 처음 목록은 아래에 docs/03 표 그대로 넣는다(도메인 EventCategoryCatalog.initial과 같다 — 통합 테스트가 맞춰
-- 본다). "기타"는 정식 카테고리가 아니다 — 모든 유형의 목록 끝에 늘 붙고 고르면 장르명을 자유 입력받는 따로 된 선택이라 줄로 두지 않는다.
-- 이름은 앞뒤 공백을 걷고 NFC로 맞춘 그대로다. 20자까지(ProposedPolicy.EVENT_CATEGORY_NAME_MAXIMUM_LENGTH, 확인 필요 #34 — 문서에 없어
-- 제안값). PostgreSQL VARCHAR(n)은 글자 수로 세므로 도메인의 글자 수(NFC로 맞춘 뒤 code point)와 같다.
-- display_order는 한 유형 안에서 보여 줄 순서(0부터)다. 승격한 카테고리는 그 유형의 끝(기타 앞)에 붙는다.
-- 보관: 지우지 않는다. 행사가 고른 카테고리를 가리킨다.
CREATE TABLE event_category (
    id            BIGINT      GENERATED ALWAYS AS IDENTITY,
    event_type    VARCHAR(20) NOT NULL,
    name          VARCHAR(20) NOT NULL,
    display_order INTEGER     NOT NULL,
    CONSTRAINT event_category_pk PRIMARY KEY (id),
    -- 행사가 (카테고리 id, 유형)으로 가리켜 행사의 유형과 카테고리의 유형이 늘 같게 한다(event_category_fk)
    CONSTRAINT event_category_id_type_uk UNIQUE (id, event_type),
    CONSTRAINT event_category_event_type_ck CHECK (event_type IN ('PERFORMANCE', 'EXHIBITION', 'FESTIVAL')),
    CONSTRAINT event_category_name_ck CHECK (char_length(name) > 0 AND name <> '기타'),
    CONSTRAINT event_category_display_order_ck CHECK (display_order >= 0)
);

-- 한 유형 안에 같은 이름의 카테고리는 둘일 수 없다. 다른 유형끼리는 이름이 겹쳐도 된다(도메인 EventCategoryCatalog).
CREATE UNIQUE INDEX event_category_type_name_ux ON event_category (event_type, name);

INSERT INTO event_category (event_type, name, display_order) VALUES
    ('PERFORMANCE', '연극', 0),
    ('PERFORMANCE', '뮤지컬', 1),
    ('PERFORMANCE', '콘서트', 2),
    ('PERFORMANCE', '클래식', 3),
    ('PERFORMANCE', '국악', 4),
    ('PERFORMANCE', '무용', 5),
    ('PERFORMANCE', '코미디', 6),
    ('EXHIBITION', '회화', 0),
    ('EXHIBITION', '사진', 1),
    ('EXHIBITION', '미디어아트', 2),
    ('EXHIBITION', '설치', 3),
    ('EXHIBITION', '공예', 4),
    ('FESTIVAL', '음악', 0),
    ('FESTIVAL', '푸드', 1),
    ('FESTIVAL', '문화', 2),
    ('FESTIVAL', '지역축제', 3);

-- 행사 한 줄 = 주최자가 여는 공연 · 전시 · 페스티벌 하나(도메인 Event). 주최자를 가리킨다(docs/14).
-- 위저드 1단계를 넘길 때 처음 저장된다 — 빈 초안은 없다(docs/03 "임시저장은 자동으로"). 그 뒤로 단계를 넘길 때마다 같은 줄을 고친다.
--
-- 공개 주소(public_address): 관객 링크에 들어가는 추측할 수 없는 URL 안전 문자 32자(도메인 EventPublicAddress — 192비트). 행사 id는 관객
--   쪽 주소에 쓰지 않는다 — 순차 번호면 숫자를 올려 가며 비공개 행사를 전부 찾는다(docs/03 "링크가 추측 가능하면 안 됩니다"). 만들 때
--   발급하고 바뀌지 않는다. 링크 전용 행사는 이 값을 아는 것이 곧 열 자격이라 로그 · 응답에 필요 이상 싣지 않는다.
-- 상태(status): 8종 그대로 저장한다(도메인 EventStatus). 작성 중으로 시작한다. 사람이 누르는 전환이 섞여 시간만으로 다시 계산할 수 없다.
--   "매진"은 상태가 아니다.
-- 1단계 기본 정보(docs/03 "기본 정보"): 제목 · 유형 · 카테고리 · 소개 · 장소(공연장 이름 · 주소)는 필수, 상세 안내 · 지도 링크 · 공개용 연락처는
--   선택(없으면 NULL). 제목은 100자까지(ProposedPolicy.EVENT_TITLE_MAXIMUM_LENGTH, 확인 필요 #34). 카테고리는 정식 카테고리(category_id)
--   또는 '기타' + 자유 입력 장르명(genre_name, 20자까지) 중 하나다. 지도 링크는 http(s) 주소다(도메인 ExternalLink). 공개용 연락처는 행사
--   페이지에 보이라고 적는 값이라 형식을 보지 않는다. 포스터 칸은 포스터 업로드(#40)가 더한다.
-- 5단계 공개 수준(docs/03 "공개 수준 3단계"): 공개 · 링크 전용 · 암호 보호. 암호 보호면 암호 해시가 있고 아니면 없다 — 원문은 저장하지 않는다.
--   해시는 운영진 비밀번호와 같은 방식(PBKDF2)이다.
-- 5단계 예매 설정(도메인 BookingSettings — 전부 처음 값이 있다):
--   booking_opens_at           예매 오픈 시각(행사 단위). 비워 두면 판매를 시작하는 순간 연다(확인 필요 #35).
--   booking_close_lead_minutes 예매 종료 — 회차 시작(종일형은 마지막 입장)보다 이만큼 앞에서 닫는다. 처음 값 60분.
--   checkin_open_lead_minutes  검표 시작 — 회차 시작(종일형은 운영 시작)보다 이만큼 앞에서 연다. 처음 값 60분.
--     두 앞섬은 분 단위로 저장한다 — 문서가 "1시간 전"처럼 시 · 분으로만 말하고 단위를 정하지 않아(도메인 LeadTime) 화면에서 고르는 가장 작은
--     단위로 둔다(#39에서 정함). 0이면 기준 시각 그대로다.
--   max_seats_per_selection    1회 선택 매수. 처음 값 4석(docs/06).
--   max_tickets_per_person · tickets_per_person_basis  1인 총 매수 — 둘 다 없으면 제한 없음(처음 값, 확인 필요 #35), 있으면 한도와 세는
--     범위(날짜별 / 행사 전체).
--   session_choice             회차 선택 제약 — 제한 없음(처음 값) / 하루에 한 회차만 / 행사 전체에서 한 회차만.
--   reentry                    재입장 — 끔(처음 값, 유형과 상관없이) / 켬.
--   minimum_age                연령 제한 — 만 이 나이 이상만 예매. 0이면 제한 없음(처음 값).
--   이름 칸의 값은 도메인 enum 이름 그대로다(바꾸지 않는다).
-- 5단계 취소 규정 조정은 구간이 여럿이라 따로 둔다(event_cancellation_fee_tier).
-- booking_opened_at: 처음 판매 중이 된 시각. '예매가 열린 뒤' 수정 범위와 회차 시간 1주일 잠금을 상태 이름이 아니라 이것으로 판정한다(도메인
--   Event.hasBookingOpened). 작성 중 · 판매 대기에는 없고 판매 중이면 있다.
-- wizard_progress_step: 작성 진행 단계 — 작성 중에 저장한 가장 뒤의 위저드 단계(도메인 EventWizardStep 이름). 앞 단계를 다시 저장해도
--   내려가지 않는다. 작성 중 행사 목록의 진행률("4/7 단계 · 가격 설정까지 완료")과 [이어서 작성]이 이것으로 멈춘 단계를 연다(docs/04
--   "작성 중 행사 관리").
-- last_modified_at: 단계를 저장해 무엇이든 바뀐 시각. 같은 값을 다시 저장하면(자동 임시저장) 그대로다. "마지막 수정 3일 전", 30일 미수정
--   알림(docs/12)에 쓴다.
-- deleted_at: 행사 삭제(#66)가 채운다. 지운 행사는 없는 행사로 다룬다(docs/01 "작성 중 · 삭제된 행사 — 없는 행사").
--
-- 동시성: 위저드 단계 저장은 이 줄을 FOR UPDATE로 잠그고 읽은 뒤 고친다 — 두 운영진이 거의 동시에 저장하면 뒤의 저장이 앞의 저장을 기다렸다가
--   바뀐 값을 읽는다. 잠금 순서는 운영진 계정 줄 → 주최자 줄 → 행사 줄이다(application의 StaffAccountLocks).
-- 보관: 지우지 않는다(삭제는 deleted_at). 정해진 보관 기간이 문서에 없다.
CREATE TABLE event (
    id                         BIGINT       GENERATED ALWAYS AS IDENTITY,
    organizer_id               BIGINT       NOT NULL,
    public_address             VARCHAR(32)  NOT NULL,
    status                     VARCHAR(30)  NOT NULL,
    title                      VARCHAR(100) NOT NULL,
    event_type                 VARCHAR(20)  NOT NULL,
    category_id                BIGINT,
    genre_name                 VARCHAR(20),
    introduction               TEXT         NOT NULL,
    venue_name                 TEXT         NOT NULL,
    venue_address              TEXT         NOT NULL,
    venue_details              TEXT,
    map_link                   TEXT,
    public_contact             TEXT,
    visibility                 VARCHAR(30)  NOT NULL,
    password_hash              TEXT,
    booking_opens_at           TIMESTAMPTZ,
    booking_close_lead_minutes INTEGER      NOT NULL,
    checkin_open_lead_minutes  INTEGER      NOT NULL,
    max_seats_per_selection    INTEGER      NOT NULL,
    max_tickets_per_person     INTEGER,
    tickets_per_person_basis   VARCHAR(20),
    session_choice             VARCHAR(30)  NOT NULL,
    reentry                    VARCHAR(20)  NOT NULL,
    minimum_age                INTEGER      NOT NULL,
    booking_opened_at          TIMESTAMPTZ,
    wizard_progress_step       VARCHAR(40)  NOT NULL,
    last_modified_at           TIMESTAMPTZ  NOT NULL,
    deleted_at                 TIMESTAMPTZ,
    CONSTRAINT event_pk PRIMARY KEY (id),
    CONSTRAINT event_organizer_fk FOREIGN KEY (organizer_id)
        REFERENCES organizer (id),
    CONSTRAINT event_category_fk FOREIGN KEY (category_id, event_type)
        REFERENCES event_category (id, event_type),
    CONSTRAINT event_public_address_ck CHECK (char_length(public_address) = 32),
    CONSTRAINT event_status_ck CHECK (status IN ('DRAFT', 'AWAITING_OPEN', 'ON_SALE', 'SALES_PAUSED', 'SALES_CLOSED', 'ENDED',
                                                 'SETTLED', 'CANCELLED')),
    CONSTRAINT event_title_ck CHECK (char_length(title) > 0),
    CONSTRAINT event_event_type_ck CHECK (event_type IN ('PERFORMANCE', 'EXHIBITION', 'FESTIVAL')),
    -- 정식 카테고리와 '기타' 장르명 중 하나만 있다(도메인 EventCategorySelection)
    CONSTRAINT event_category_or_genre_ck CHECK (
        (category_id IS NOT NULL AND genre_name IS NULL)
        OR (category_id IS NULL AND genre_name IS NOT NULL AND char_length(genre_name) > 0)),
    CONSTRAINT event_introduction_ck CHECK (char_length(introduction) > 0),
    CONSTRAINT event_venue_ck CHECK (char_length(venue_name) > 0 AND char_length(venue_address) > 0),
    CONSTRAINT event_visibility_ck CHECK (visibility IN ('PUBLIC', 'LINK_ONLY', 'PASSWORD_PROTECTED')),
    -- 암호 보호일 때만 암호 해시가 있다(도메인 EventVisibilitySetting)
    CONSTRAINT event_password_hash_ck CHECK (
        (visibility = 'PASSWORD_PROTECTED' AND password_hash IS NOT NULL)
        OR (visibility <> 'PASSWORD_PROTECTED' AND password_hash IS NULL)),
    CONSTRAINT event_lead_minutes_ck CHECK (booking_close_lead_minutes >= 0 AND checkin_open_lead_minutes >= 0),
    CONSTRAINT event_max_seats_per_selection_ck CHECK (max_seats_per_selection >= 1),
    -- 1인 총 매수는 한도와 세는 범위가 함께 있거나 함께 없다(도메인 TicketsPerPersonLimit)
    CONSTRAINT event_tickets_per_person_ck CHECK (
        (max_tickets_per_person IS NULL AND tickets_per_person_basis IS NULL)
        OR (max_tickets_per_person >= 1 AND tickets_per_person_basis IN ('PER_DATE', 'EVENT_TOTAL'))),
    CONSTRAINT event_session_choice_ck CHECK (session_choice IN ('NONE', 'ONE_SESSION_PER_DATE', 'ONE_SESSION_PER_EVENT')),
    CONSTRAINT event_reentry_ck CHECK (reentry IN ('NOT_ALLOWED', 'ALLOWED')),
    CONSTRAINT event_minimum_age_ck CHECK (minimum_age >= 0),
    -- 작성 중 · 판매 대기에는 예매가 열린 시각이 없고, 판매 중이면 있다(도메인 Event)
    CONSTRAINT event_booking_opened_at_ck CHECK (
        (status IN ('DRAFT', 'AWAITING_OPEN') AND booking_opened_at IS NULL)
        OR (status = 'ON_SALE' AND booking_opened_at IS NOT NULL)
        OR status IN ('SALES_PAUSED', 'SALES_CLOSED', 'ENDED', 'SETTLED', 'CANCELLED')),
    CONSTRAINT event_wizard_progress_step_ck CHECK (wizard_progress_step IN ('BASIC_INFORMATION', 'SCHEDULE', 'SEATING', 'PRICING',
                                                                             'BOOKING_SETTINGS', 'ENTRANCES_AND_CHECKIN_ACCOUNTS',
                                                                             'REVIEW'))
);

-- 공개 주소로 행사를 찾는다(관객의 행사 페이지). 192비트라 겹칠 일은 사실상 없지만, 겹치면 한 링크가 두 행사를 가리키므로 색인이 막는다.
CREATE UNIQUE INDEX event_public_address_ux ON event (public_address);

-- 한 주최자의 행사를 찾는다 — 내 행사 목록(작성 중 · 진행 중 · 지난 행사).
CREATE INDEX event_organizer_ix ON event (organizer_id);

-- 행사의 취소 규정 구간 한 줄 = "관람일 N일 전부터 공제율 P%"(도메인 CancellationFeeTier). 다음 구간(더 가까운 날)이 시작하기 전날까지 이어진다.
-- 행사마다 관람일 당일(0일 전)부터 시작하는 구간이 있고, 같은 날에서 시작하는 구간은 둘일 수 없다(기본 키). 겹치는 구간을 합친 꼴로만
-- 저장한다 — 같은 규정은 어떻게 적어 넣었든 같은 줄이다(도메인 CancellationPolicy).
-- 처음 값은 소비자분쟁해결기준 그대로이고(docs/08 "규정은 기준 고정"), 주최자는 관객에게 더 유리한 쪽으로만 조정한다 — 기준보다 불리한
-- 구간은 도메인이 막는다. 기준표 그대로인 행사도 그 구간을 그대로 적어 둔다.
-- 예매가 열린 뒤에는 바꿀 수 없다 — 예매 시점 규정이 관객마다 따라다닌다(docs/04 "예매가 열린 뒤 수정 범위").
-- 고칠 때는 그 행사의 줄을 모두 지우고 새로 넣는다. 보관: 행사와 같다.
CREATE TABLE event_cancellation_fee_tier (
    event_id               BIGINT  NOT NULL,
    from_days_before       INTEGER NOT NULL,
    deduction_rate_percent INTEGER NOT NULL,
    CONSTRAINT event_cancellation_fee_tier_pk PRIMARY KEY (event_id, from_days_before),
    CONSTRAINT event_cancellation_fee_tier_event_fk FOREIGN KEY (event_id)
        REFERENCES event (id),
    CONSTRAINT event_cancellation_fee_tier_from_days_before_ck CHECK (from_days_before >= 0),
    CONSTRAINT event_cancellation_fee_tier_deduction_rate_ck CHECK (deduction_rate_percent BETWEEN 0 AND 100)
);

-- 판매 중단 이력 한 줄 = 판매를 멈춘 한 번 — 까닭 · 멈춘 사람 · 사유 · 멈춘 시각 · 끝난 시각(도메인 SalesPause).
-- 상태가 덮여도 남는다 — 판매 중단 중에 판매 종료가 돼도, 행사가 끝나도 그대로다(docs/04 "'중단했던 이력'은 따로 남깁니다").
-- sequence_number는 그 행사에서 멈춘 순서(0부터)다. 이력은 뒤에 붙기만 하고, 바뀌는 칸은 끝난 시각(ended_at)뿐이다.
-- 멈춘 사람: 주최자 중단(ORGANIZER)은 운영진 계정, 관리자 조치(정지 2단계 ADMIN_SUSPENSION · 페이지 내리기 ADMIN_PAGE_TAKEDOWN)는 관리자
--   계정을 가리킨다. 한 줄에 둘 중 하나만 있다.
-- 사유: 주최자가 적은 한 줄(선택, 50자까지 — ProposedPolicy.SALES_PAUSE_REASON_MAXIMUM_LENGTH, 확인 필요 #38). 관리자 조치의 사유는 정지 기록 ·
--   페이지 내리기가 갖는다.
-- 끝난 시각: 주최자 중단은 재개한 시각, 관리자 조치는 조치를 푼 시각. 판매 종료로 넘어가 재개하지 못한 중단은 비어 있다.
-- 판매 중단 · 재개 기능은 이 표에 줄을 넣고 끝난 시각을 적는다(이 이슈는 표와 저장만 만든다). 보관: 행사와 같다.
CREATE TABLE event_sales_pause (
    event_id                   BIGINT      NOT NULL,
    sequence_number            INTEGER     NOT NULL,
    cause                      VARCHAR(30) NOT NULL,
    paused_by_staff_account_id BIGINT,
    paused_by_admin_account_id BIGINT,
    reason                     VARCHAR(50),
    paused_at                  TIMESTAMPTZ NOT NULL,
    ended_at                   TIMESTAMPTZ,
    CONSTRAINT event_sales_pause_pk PRIMARY KEY (event_id, sequence_number),
    CONSTRAINT event_sales_pause_event_fk FOREIGN KEY (event_id)
        REFERENCES event (id),
    CONSTRAINT event_sales_pause_staff_account_fk FOREIGN KEY (paused_by_staff_account_id)
        REFERENCES staff_account (id),
    CONSTRAINT event_sales_pause_admin_account_fk FOREIGN KEY (paused_by_admin_account_id)
        REFERENCES admin_account (id),
    CONSTRAINT event_sales_pause_sequence_number_ck CHECK (sequence_number >= 0),
    CONSTRAINT event_sales_pause_cause_ck CHECK (cause IN ('ORGANIZER', 'ADMIN_SUSPENSION', 'ADMIN_PAGE_TAKEDOWN')),
    -- 주최자 중단은 운영진이, 관리자 조치는 관리자가 멈췄다. 관리자 조치는 사유를 갖지 않는다(도메인 SalesPause)
    CONSTRAINT event_sales_pause_actor_ck CHECK (
        (cause = 'ORGANIZER' AND paused_by_staff_account_id IS NOT NULL AND paused_by_admin_account_id IS NULL)
        OR (cause <> 'ORGANIZER' AND paused_by_admin_account_id IS NOT NULL AND paused_by_staff_account_id IS NULL
            AND reason IS NULL)),
    CONSTRAINT event_sales_pause_reason_ck CHECK (reason IS NULL OR char_length(reason) > 0),
    CONSTRAINT event_sales_pause_period_ck CHECK (ended_at IS NULL OR ended_at >= paused_at)
);
