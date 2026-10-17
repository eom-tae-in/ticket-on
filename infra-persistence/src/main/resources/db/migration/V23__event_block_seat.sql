-- 행사 도면의 블록 · 좌석 (#43)
-- 근거: docs/02 "블록"(좌석 번호를 유일하게 만드는 단위, 이름 자유 입력 · 중복 검증), "에디터는 블록 단위"(이름 + 행 × 열 덩어리 얹기, 위치
--       이동 · 각도, 개별 좌석 비활성화, 구역 배정, 좌석 속성), "좌석 속성"(일반 · 휠체어 · 시야제한 — 판매 제외는 회차 단위라 여기 없음),
--       docs/14 "무엇이 무엇을 가리키나"(좌석 → 블록 + 구역, 취소 · 회수된 티켓만 남은 좌석은 지워도 기록으로 남음), docs/07 "티켓이 직접 갖는
--       것과 가리키기만 하는 것"(티켓은 좌석 id를 가리킨다 — 좌석 id가 바뀌면 안 된다)
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V24__...)을 추가한다.

-- 좌석이 가리키는 구역이 같은 행사의 구역인지를 표가 볼 수 있게 (행사, 구역) 짝을 유일 키로 둔다 — 아래 event_seat_zone_fk가 이것을
-- 가리킨다. id가 이미 유일하므로 줄을 더 막지는 않는다.
ALTER TABLE event_zone ADD CONSTRAINT event_zone_event_id_uk UNIQUE (event_id, id);

-- 블록 한 줄 = 좌석 번호를 유일하게 만드는 덩어리 하나(도메인 SeatBlock). 행사의 도면에 속한다 — 회차마다 다르지 않다(docs/02 "두 개의 축").
-- 이름(name): 앞뒤 공백을 걷고 NFC로 맞춘 그대로. 20자까지(ProposedPolicy.BLOCK_NAME_MAXIMUM_LENGTH, 확인 필요 #37). 비워 두면 도메인이
--   "블록 N"을 붙여 넣으므로 빈 이름은 없다. 같은 도면의 다른 블록 · 구역 이름과 겹칠 수 없다 — 구역과 겹치는지는 표를 건너 막을 수 없어
--   도메인이 행사 줄을 잠근 채 본다. 지우지 않은 블록끼리는 아래 유일 색인도 막는다. 판매 중에도 고치고 이력은 남기지 않는다(docs/02).
-- 크기(row_count · column_count): 에디터의 "5행 × 10열"(도메인 BlockSize). 행 순번이 좌석 표기의 "N열", 열 순번이 "N번"이다. 상한(50 × 50,
--   ProposedPolicy.SEAT_BLOCK_MAXIMUM_ROWS · COLUMNS, 확인 필요 #37)은 도메인이 본다 — 제안값이라 표에 박지 않는다. 얹은 뒤에는 바꾸지 않는다
--   (도메인에 크기를 바꾸는 길이 없다). 격자의 모든 자리에 좌석이 한 줄씩 있다(event_seat).
-- 위치(position_x · position_y): 배치도 위의 한 점(도메인 CanvasPoint). 단위와 원점은 배치도 화면이 정하고 표는 받은 정수를 그대로 지킨다.
-- 각도(angle_degrees): 부채꼴 객석용으로 돌린 정도, 0 이상 360 미만의 도(도메인 BlockAngle).
-- deleted_at: 블록을 지운 시각. 줄은 지우지 않는다 — 그 블록의 좌석도 같은 시각에 지운 것으로 적고 남겨, 취소 · 회수된 티켓이 원래 좌석을
--   가리킨다(docs/14). 지운 블록은 도면에 없는 블록으로 다룬다(읽지 않는다). 지운 블록의 이름은 다시 쓸 수 있다.
-- 차례: 도면은 블록을 얹은 순서(id)로 보인다.
-- 동시성: 블록 · 좌석을 고치는 흐름은 먼저 행사 줄을 FOR UPDATE로 잠근다(application의 EventEditingLocks) — 같은 행사의 도면 저장은 차례로
--   처리되고, 이 표의 줄은 따로 잠그지 않는다.
-- CHECK와 NULL: 아래 CHECK가 보는 칸은 모두 NOT NULL이라 식이 NULL이 되어 빠져나가는 일이 없다. 크기는 크기 하나의 규칙으로 묶는다.
-- 보관: 지우지 않는다(삭제는 deleted_at). 행사와 같다.
CREATE TABLE event_block (
    id            BIGINT      GENERATED ALWAYS AS IDENTITY,
    event_id      BIGINT      NOT NULL,
    name          VARCHAR(20) NOT NULL,
    row_count     INTEGER     NOT NULL,
    column_count  INTEGER     NOT NULL,
    position_x    INTEGER     NOT NULL,
    position_y    INTEGER     NOT NULL,
    angle_degrees INTEGER     NOT NULL,
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT event_block_pk PRIMARY KEY (id),
    CONSTRAINT event_block_event_fk FOREIGN KEY (event_id)
        REFERENCES event (id),
    -- 좌석이 가리키는 블록이 같은 행사의 블록인지 표가 볼 수 있게 — event_seat_block_fk가 이것을 가리킨다
    CONSTRAINT event_block_event_id_uk UNIQUE (event_id, id),
    CONSTRAINT event_block_name_ck CHECK (char_length(name) > 0),
    CONSTRAINT event_block_size_ck CHECK (row_count >= 1 AND column_count >= 1),
    CONSTRAINT event_block_angle_ck CHECK (angle_degrees >= 0 AND angle_degrees < 360)
);

-- 한 행사의 지우지 않은 블록끼리는 이름이 겹치지 않는다(docs/02 "블록 — 중복 검증을 겁니다"). 지운 블록은 빼고 본다. 행사의 도면을 읽을 때
-- (지우지 않은 블록)도 이 색인을 쓴다.
CREATE UNIQUE INDEX event_block_event_name_ux ON event_block (event_id, name)
    WHERE deleted_at IS NULL;

-- 좌석 한 줄 = 블록 안의 개별 자리 하나(도메인 Seat). 재고(회차 × 좌석) · 티켓 · 검표가 이 줄의 id를 가리킨다 — 좌석의 정체성은 id다.
--   블록을 옮기거나 돌리거나 이름을 고쳐도, 다른 좌석을 꺼도 줄과 id · 번호는 그대로다(저장소는 고친 칸만 바꾸고 좌석 줄을 다시 만들지 않는다).
-- event_id: 그 블록의 행사. 블록 · 구역이 같은 행사의 것인지 외래 키 둘이 함께 보게 둔다(event_seat_block_fk · event_seat_zone_fk). 행사의 좌석을
--   한꺼번에 읽을 때도 쓴다.
-- 번호(seat_row · seat_number): 블록 격자의 행 순번("N열")과 그 행 안의 순번("N번"), 1부터(도메인 SeatNumber). 블록을 얹을 때 정하고 다시
--   매기지 않는다(확인 필요 #37). 블록 크기 안인지는 표를 건너 볼 수 없어 도메인이 본다. 한 블록 안에서 번호는 겹치지 않는다 — 좌석은 블록과
--   함께만 지워지므로 지운 좌석까지 넣어 유일하다(event_seat_number_uk).
-- 구역(zone_id): 배정한 지정형 구역. 아직 배정하지 않았으면 NULL. 수량형 구역에 배정하지 않는 것은 도메인이 본다. 구역을 지우면 그 구역의
--   좌석은 같은 저장에서 배정이 풀린다(NULL) — 지운 구역을 가리키는 지우지 않은 좌석이 남지 않는다(도메인 FloorPlan이 도면에 없는 구역을
--   가리키는 좌석을 받지 않고, 저장소가 저장할 구역이 지우지 않은 구역인지 먼저 본다). 지운 좌석은 지울 때의 구역을 그대로 남긴다(기록).
-- 속성(attribute): 일반(GENERAL) · 휠체어(WHEELCHAIR) · 시야제한(RESTRICTED_VIEW) — 물리적 사실이라 도면에 붙는다(docs/02 "좌석 속성").
--   판매 제외는 회차 단위라 재고가 갖는다(#53). 값은 도메인 enum 이름 그대로다.
-- active: 활성 여부. 비활성(FALSE)은 통로 · 기둥 같은 빈 자리다 — 팔 자리가 아니지만 자리와 번호는 남아 다시 켜면 같은 좌석이 돌아온다.
--   지운 시각과 다르다.
-- deleted_at: 좌석을 지운 시각 — 블록을 지울 때 같은 시각으로 적는다. 줄은 지우지 않는다(취소 · 회수된 티켓이 원래 좌석을 가리킨다).
-- 차례: 블록 순서, 같은 블록은 행 · 번호 순서.
-- 동시성: 블록과 같다(행사 줄을 쥔 흐름만 고친다). 외래 키 확인 잠금은 이미 쥔 행사 줄의 블록 · 구역 줄에 걸린다.
-- CHECK와 NULL: 아래 CHECK가 보는 칸은 모두 NOT NULL이다. NULL이 될 수 있는 zone_id는 외래 키만 본다(NULL이면 보지 않는다 — 배정 안 함).
-- 보관: 지우지 않는다(삭제는 deleted_at). 행사와 같다.
CREATE TABLE event_seat (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY,
    event_id    BIGINT      NOT NULL,
    block_id    BIGINT      NOT NULL,
    seat_row    INTEGER     NOT NULL,
    seat_number INTEGER     NOT NULL,
    zone_id     BIGINT,
    attribute   VARCHAR(20) NOT NULL,
    active      BOOLEAN     NOT NULL,
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT event_seat_pk PRIMARY KEY (id),
    CONSTRAINT event_seat_block_fk FOREIGN KEY (event_id, block_id)
        REFERENCES event_block (event_id, id),
    CONSTRAINT event_seat_zone_fk FOREIGN KEY (event_id, zone_id)
        REFERENCES event_zone (event_id, id),
    CONSTRAINT event_seat_number_uk UNIQUE (block_id, seat_row, seat_number),
    CONSTRAINT event_seat_number_ck CHECK (seat_row >= 1 AND seat_number >= 1),
    CONSTRAINT event_seat_attribute_ck CHECK (attribute IN ('GENERAL', 'WHEELCHAIR', 'RESTRICTED_VIEW'))
);

-- 행사의 지우지 않은 좌석을 한꺼번에 읽고(도면 읽기), 지운 블록의 좌석에 지운 시각을 적을 때 쓴다.
CREATE INDEX event_seat_event_block_ix ON event_seat (event_id, block_id)
    WHERE deleted_at IS NULL;
