-- 행사 도면의 구역 · 무대 위치와 입구 목록 (#42)
-- 근거: docs/02 "두 개의 축"(도면은 행사에 하나), "구역"(지정형 / 수량형, 정원), "설정 흐름", "에디터는 블록 단위"(무대 위치 지정),
--       "입구 (게이트)"(도면에 속함), docs/03 "행사 만들기 위저드"(3단계 구역 · 6단계 입구), docs/04 "판매 종료는 공연이 끝난 때가 아닙니다"
--       (입구는 판매가 끝난 뒤에도 수정), docs/05 "가격의 층위"(가격은 구역에 붙음), docs/14 "무엇이 무엇을 가리키나"(블록 · 구역 · 입구 → 행사,
--       취소 · 회수된 티켓만 남은 구역은 지워도 기록으로 남음)
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V23__...)을 추가한다.
--
-- 블록 · 좌석 표는 #43이 더한다.

-- 구역 한 줄 = 가격이 붙는 재고 묶음 하나(도메인 Zone). 행사의 도면에 속한다 — 회차마다 다르지 않다(docs/02 "두 개의 축").
-- 종류(kind): 지정형(DESIGNATED — 좌석을 배정받는다) / 수량형(QUANTITY — 정원 숫자만 있다). 만들 때 정하고 바꾸지 않는다(도메인 Zone, 확인 필요
--   #37). 값은 도메인 enum 이름 그대로다.
-- 이름(name): 앞뒤 공백을 걷고 NFC로 맞춘 그대로. 20자까지(ProposedPolicy.ZONE_NAME_MAXIMUM_LENGTH, 확인 필요 #37). 같은 도면의 다른 구역 ·
--   블록 이름과 겹칠 수 없다 — 블록과 겹치는지는 도메인이 행사 줄을 잠근 채 본다(블록 표는 #43). 지우지 않은 구역끼리는 아래 유일 색인도 막는다.
-- 색(color): 좌석 선택 화면에서 구역을 가르는 웹 색 #RRGGBB, 16진수는 대문자(도메인 ZoneColor). 꼴은 regexp_like(PostgreSQL 15부터)로
--   본다 — SIMILAR TO는 jOOQ 코드 생성의 SQL 파서가 읽지 못한다.
-- 기본가(base_price): 원, 1원 이상(도메인 ZoneBasePrice). 가격은 구역에 하나이고 회차에는 조정값만 둔다(docs/05 "가격의 층위").
-- 정원(capacity): 수량형만 있다(1 이상). 지정형은 없다(NULL) — 배정한 좌석이 자리다. 회차마다의 정원 · 잔여는 재고(#54)가 갖는다.
-- deleted_at: 구역을 지운 시각. 줄은 지우지 않는다 — 취소 · 회수된 티켓이 원래 구역을 계속 가리킨다(docs/14). 지운 구역은 도면에 없는 구역으로
--   다룬다(읽지 않는다). 지운 구역의 이름은 다시 쓸 수 있다.
-- 차례: 도면은 구역을 더한 순서(id)로 보인다.
-- 동시성: 구역을 더하고 고치고 지우는 흐름은 먼저 행사 줄을 FOR UPDATE로 잠근다(application의 EventEditingLocks) — 같은 행사의 도면 저장은
--   차례로 처리되고, 이 표의 줄은 따로 잠그지 않는다.
-- CHECK와 NULL: 정원은 NULL이 될 수 있어 IS NULL / IS NOT NULL로 먼저 가른다. 종류 값 자체는 event_zone_kind_ck가, 종류와 정원의 관계는
--   event_zone_capacity_ck가 맡는다(종류마다의 조건으로 적어, 모르는 종류는 종류 CHECK로만 걸린다).
-- 보관: 지우지 않는다(삭제는 deleted_at). 행사와 같다.
CREATE TABLE event_zone (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY,
    event_id   BIGINT      NOT NULL,
    kind       VARCHAR(20) NOT NULL,
    name       VARCHAR(20) NOT NULL,
    color      VARCHAR(7)  NOT NULL,
    base_price BIGINT      NOT NULL,
    capacity   INTEGER,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT event_zone_pk PRIMARY KEY (id),
    CONSTRAINT event_zone_event_fk FOREIGN KEY (event_id)
        REFERENCES event (id),
    CONSTRAINT event_zone_kind_ck CHECK (kind IN ('DESIGNATED', 'QUANTITY')),
    CONSTRAINT event_zone_name_ck CHECK (char_length(name) > 0),
    CONSTRAINT event_zone_color_ck CHECK (regexp_like(color, '^#[0-9A-F]{6}$')),
    CONSTRAINT event_zone_base_price_ck CHECK (base_price >= 1),
    -- 수량형은 1 이상의 정원이 있고, 지정형은 정원이 없다(도메인 Zone). capacity가 NULL이면 수량형 갈래가 (FALSE AND NULL) = FALSE가 되어
    -- 비교식의 NULL로 빠져나가지 않는다. kind는 NOT NULL이라 비교가 NULL이 되지 않는다
    CONSTRAINT event_zone_capacity_ck CHECK (
        (kind <> 'QUANTITY' OR (capacity IS NOT NULL AND capacity >= 1))
        AND (kind <> 'DESIGNATED' OR capacity IS NULL))
);

-- 한 행사의 지우지 않은 구역끼리는 이름이 겹치지 않는다(도메인 FloorPlan — 수량형 티켓 표기 "스탠딩A · 0091-2"가 구역 이름으로 구역을
-- 가리킨다). 지운 구역은 빼고 본다 — 지운 구역의 이름으로 새 구역을 만들 수 있다. 행사의 도면을 읽을 때(지우지 않은 구역)도 이 색인을 쓴다.
CREATE UNIQUE INDEX event_zone_event_name_ux ON event_zone (event_id, name)
    WHERE deleted_at IS NULL;

-- 무대 위치 = 배치도 위의 한 점(도메인 FloorPlan.stagePosition, CanvasPoint). 행사의 도면에 하나다. 좌표의 단위와 원점은 배치도 화면이 정하고
-- 표는 받은 정수를 그대로 지킨다. 정하지 않았으면 줄이 없다. 무대 위치를 정하는 기능은 배치도 편집(#43)이 쓴다.
-- 보관: 행사와 같다.
CREATE TABLE event_stage_position (
    event_id BIGINT  NOT NULL,
    x        INTEGER NOT NULL,
    y        INTEGER NOT NULL,
    CONSTRAINT event_stage_position_pk PRIMARY KEY (event_id),
    CONSTRAINT event_stage_position_event_fk FOREIGN KEY (event_id)
        REFERENCES event (id)
);

-- 입구 한 줄 = 주최자가 미리 등록하는 입장 지점 하나 — "정문", "2층 전용", "VIP 입구"(도메인 Entrance, docs/02 "입구 (게이트)"). 도면에 속해
-- 회차마다 달라지지 않는다. 검표 시작 때 고르고, 입장 기록이 입구를 가리켜 게이트별 입장 현황이 나온다(docs/14, docs/11).
-- 판매가 끝나 도면이 고정된 뒤에도 행사가 끝날 때까지 고친다(docs/04 — 18:10 정문 줄이 길어 "옆문" 추가).
-- 이름(name): 앞뒤 공백을 걷고 NFC로 맞춘 그대로. 20자까지(ProposedPolicy.ENTRANCE_NAME_MAXIMUM_LENGTH, 확인 필요 #37). 같은 행사의 지우지
--   않은 입구끼리는 겹치지 않는다(아래 유일 색인, 확인 필요 #37 — 검표원이 고르는 목록과 게이트별 입장 현황이 이름으로 입구를 보여 준다).
-- deleted_at: 입구를 지운 시각. 줄은 지우지 않는다 — 그 입구를 가리키는 입장 기록이 남는다. 지운 입구는 목록에 없는 입구로 다룬다.
-- 차례: 목록은 입구를 더한 순서(id)로 보인다.
-- 동시성: 입구를 더하고 고치고 지우는 흐름도 먼저 행사 줄을 FOR UPDATE로 잠근다 — 같은 행사의 입구 저장은 차례로 처리된다.
-- 보관: 지우지 않는다(삭제는 deleted_at). 행사와 같다.
CREATE TABLE event_entrance (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY,
    event_id   BIGINT      NOT NULL,
    name       VARCHAR(20) NOT NULL,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT event_entrance_pk PRIMARY KEY (id),
    CONSTRAINT event_entrance_event_fk FOREIGN KEY (event_id)
        REFERENCES event (id),
    CONSTRAINT event_entrance_name_ck CHECK (char_length(name) > 0)
);

-- 한 행사의 지우지 않은 입구끼리는 이름이 겹치지 않는다. 지운 입구의 이름은 다시 쓸 수 있다. 행사의 입구 목록을 읽을 때도 이 색인을 쓴다.
CREATE UNIQUE INDEX event_entrance_event_name_ux ON event_entrance (event_id, name)
    WHERE deleted_at IS NULL;
