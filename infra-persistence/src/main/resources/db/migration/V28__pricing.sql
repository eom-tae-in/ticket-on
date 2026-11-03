-- 가격 — 묶음권 상품 · 조기예매 · 쿠폰 · 쿠폰 사용 (#51)
-- 근거: docs/05 "조기예매"(재고 · 적용 범위 · 종료 조건 · 소진되면 영구 마감 · 임박 표시), "쿠폰"(코드 · 할인 방식 · 전체 사용 가능 횟수 · 1인
--       1회 · 유효 기간 · 취소하면 돌아옴), "묶음권 (수량형 전용)"(날짜 N개 · 할인율), docs/14 "무엇이 무엇을 가리키나"(조기예매 · 쿠폰 → 행사,
--       묶음권 상품 → 날짜 N개), docs/99 C "할인 값 (조기예매·쿠폰)", "쿠폰 전체 사용 가능 횟수", "할인 기간 (조기예매·쿠폰)", "입력·파일 상한"
--       (쿠폰 코드 20자 · 대소문자 무시 · 공백 없음), docs/03 "행사 만들기 위저드"(4단계)
-- 규칙: 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 땐 새 버전(V29__...)을 추가한다.
--
-- 같은 행사의 것만 가리킨다: 묶음권의 날짜 · 구역, 조기예매가 끈 구역 · 묶음권은 (event_id, id) 외래 키로 같은 행사의 줄만 가리킨다
--   (event_session_date_fk와 같은 방식).
-- 동시성: 4단계 저장(더하기 · 고치기 · 지우기)과, 구역 · 날짜 · 가격이 바뀔 때 따라가는 정리는 먼저 행사 줄을 FOR UPDATE로 잠근다
--   (application의 EventEditingLocks) — 같은 행사의 가격 저장은 차례로 처리되고 이 표들의 줄은 따로 잠그지 않는다. 사용 수(claimed_count ·
--   used_count)를 늘리는 일은 선점 · 예매(#54 · #73)가 "사용 수 + n ≤ 한도"인 한 문장으로 한다 — 주최자의 저장은 사용 수 칸을 쓰지 않는다.
-- CHECK와 NULL: NULL이 될 수 있는 칸(조기예매 수량, 쿠폰 사용의 돌려준 시각)은 비교 전에 IS NULL로 먼저 가른다. 어긋난 CHECK가 여럿이면 이름
--   차례로 처음 것을 알리므로 CHECK마다 제 규칙 하나만 맡는다.
-- 보관: 지우지 않는다(삭제는 deleted_at) — 티켓이 받은 할인과 쿠폰 사용이 원래 줄을 가리킨다(docs/14 "받은 할인 — 조기예매가 끝나도 남음").
--   행사와 같다.

-- 묶음권 상품 한 줄 = 날짜 N개를 묶은 상품(도메인 BundleProduct). 수량형 구역에서만 판다 — 구역의 종류는 표를 건너 볼 수 없어 도메인이 본다.
-- 이름(name): 앞뒤 공백을 걷고 NFC로 맞춘 그대로, 20자까지(ProposedPolicy.BUNDLE_PRODUCT_NAME_MAXIMUM_LENGTH, 확인 필요 #223).
-- 할인율(discount_rate): 정수 퍼센트, 0 이상 100 미만. 0%는 할인 없이 묶어 판다. 100%는 어느 가격이든 0원이라 받지 않는다(도메인
--   BundleProductTerms). 가격은 날짜별 가격 합계 × (1 − 할인율), 100원 내림 — 저장하지 않고 계산한다(docs/99 D "묶음권 가격").
-- 차례: 4단계 화면은 만든 순서(id)로 보인다.
CREATE TABLE bundle_product (
    id            BIGINT      GENERATED ALWAYS AS IDENTITY,
    event_id      BIGINT      NOT NULL,
    name          VARCHAR(20) NOT NULL,
    discount_rate INTEGER     NOT NULL,
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT bundle_product_pk PRIMARY KEY (id),
    CONSTRAINT bundle_product_event_fk FOREIGN KEY (event_id)
        REFERENCES event (id),
    -- 묶음권을 가리키는 줄(날짜 · 구역 · 조기예매가 끈 상품)이 같은 행사의 묶음권인지 표가 볼 수 있게
    CONSTRAINT bundle_product_event_id_uk UNIQUE (event_id, id),
    CONSTRAINT bundle_product_name_ck CHECK (char_length(name) > 0),
    CONSTRAINT bundle_product_discount_rate_ck CHECK (discount_rate >= 0 AND discount_rate <= 99)
);

CREATE INDEX bundle_product_event_ix ON bundle_product (event_id)
    WHERE deleted_at IS NULL;

-- 묶음권이 묶는 날짜 한 줄. 주최자는 날짜만 고르고, 날짜 아래 회차는 관객이 예매할 때 고른다(docs/05 "조합은 자동 생성하지 않습니다"). 날짜가
--   2개 이상인지는 도메인이 본다. 묶은 날짜가 지워지면 줄은 지운 날짜를 그대로 가리킨다 — 지운 날짜의 줄도 남으므로 외래 키가 유지된다. 회차를
--   다른 날짜로 옮겨 날짜가 빠지면 옮겨 간 날짜로 바꾼다(EventDateMoved, 도메인 BundleProduct#followMovedDate).
CREATE TABLE bundle_product_date (
    bundle_product_id BIGINT NOT NULL,
    event_id          BIGINT NOT NULL,
    date_id           BIGINT NOT NULL,
    CONSTRAINT bundle_product_date_pk PRIMARY KEY (bundle_product_id, date_id),
    CONSTRAINT bundle_product_date_product_fk FOREIGN KEY (event_id, bundle_product_id)
        REFERENCES bundle_product (event_id, id),
    CONSTRAINT bundle_product_date_date_fk FOREIGN KEY (event_id, date_id)
        REFERENCES event_date (event_id, id)
);

-- 묶음권을 파는 구역 한 줄 — 수량형만(도메인이 본다), 하나 이상(도메인이 본다). 도면에서 구역이 지워지면 줄을 지운다(도메인
--   BundleProduct#dropRemovedZone).
CREATE TABLE bundle_product_zone (
    bundle_product_id BIGINT NOT NULL,
    event_id          BIGINT NOT NULL,
    zone_id           BIGINT NOT NULL,
    CONSTRAINT bundle_product_zone_pk PRIMARY KEY (bundle_product_id, zone_id),
    CONSTRAINT bundle_product_zone_product_fk FOREIGN KEY (event_id, bundle_product_id)
        REFERENCES bundle_product (event_id, id),
    CONSTRAINT bundle_product_zone_zone_fk FOREIGN KEY (event_id, zone_id)
        REFERENCES event_zone (event_id, id)
);

-- 조기예매 한 줄 = 구역 재고에서 떼어오는 할인 몫(도메인 EarlyBird). 행사당 여러 개(1차 · 2차 얼리버드) — 기간이 겹치면 저장할 때 경고만 한다.
-- 할인(discount_kind · discount_value): 정액(FIXED_AMOUNT — 원, 1 이상) 또는 정률(RATE — 정수 퍼센트, 1~99)(docs/99 C "할인 값"). 값은 도메인
--   이름 그대로다. 할인이 닿는 가장 낮은 가격에 적용해도 1원 이상 남는지는 가격을 아는 도메인이 본다.
-- 기간(starts_at · ends_at): 시작 순간부터 끝 순간 직전까지(docs/99 C "할인 기간"). 둘 다 필수.
-- 수량(quantity): 몫의 수, 1 이상. 없으면(NULL) 기간만 있는 조기예매다(docs/05 "종료 조건").
-- 사용 수(claimed_count): 몫을 잡은 티켓 수. 좌석을 선점하는 순간 늘고 어떤 경우에도 줄지 않는다(docs/05 "소진되면 영구 마감"). 수량을 넘지 않는다.
-- 도달한 최고 임박 단계(highest_urgency_reached): 한 번 올라간 단계는 내려가지 않는다(docs/05 "임박 표시"). 값은 도메인 EarlyBirdUrgency 이름.
-- 적용 범위: 기본값은 전체 구역 · 전체 상품이고 끈 것만 적는다(도메인 EarlyBirdScope) — 1일권을 껐는지(single_session_excluded), 끈 구역과
--   끈 묶음권 상품은 아래 두 표.
-- 상태(시작 전 · 진행 중 · 소진 · 기간 종료)는 저장하지 않고 기간과 사용 수로 계산한다(docs/14 "저장하는 것과 계산하는 것").
-- 차례: 만든 순서(id) — 겹친 조기예매 가운데 고르는 마지막 기준이다(docs/99 E "가격 · 할인").
CREATE TABLE early_bird (
    id                      BIGINT      GENERATED ALWAYS AS IDENTITY,
    event_id                BIGINT      NOT NULL,
    discount_kind           VARCHAR(20) NOT NULL,
    discount_value          BIGINT      NOT NULL,
    starts_at               TIMESTAMPTZ NOT NULL,
    ends_at                 TIMESTAMPTZ NOT NULL,
    quantity                INTEGER,
    claimed_count           INTEGER     NOT NULL DEFAULT 0,
    highest_urgency_reached VARCHAR(20) NOT NULL,
    single_session_excluded BOOLEAN     NOT NULL,
    deleted_at              TIMESTAMPTZ,
    CONSTRAINT early_bird_pk PRIMARY KEY (id),
    CONSTRAINT early_bird_event_fk FOREIGN KEY (event_id)
        REFERENCES event (id),
    -- 끈 구역 · 묶음권 줄이 같은 행사의 조기예매인지 표가 볼 수 있게
    CONSTRAINT early_bird_event_id_uk UNIQUE (event_id, id),
    CONSTRAINT early_bird_discount_kind_ck CHECK (discount_kind IN ('FIXED_AMOUNT', 'RATE')),
    -- 종류마다의 값 — 정액은 1원 이상, 정률은 1~99%. 종류 · 값은 NOT NULL이라 비교가 NULL이 되지 않고, 모르는 종류는 종류 CHECK로만 걸린다
    CONSTRAINT early_bird_discount_value_ck CHECK (
        (discount_kind <> 'FIXED_AMOUNT' OR discount_value >= 1)
        AND (discount_kind <> 'RATE' OR (discount_value >= 1 AND discount_value <= 99))),
    CONSTRAINT early_bird_period_ck CHECK (ends_at > starts_at),
    CONSTRAINT early_bird_quantity_ck CHECK (quantity IS NULL OR quantity >= 1),
    CONSTRAINT early_bird_claimed_count_ck CHECK (claimed_count >= 0),
    -- 사용 수는 수량을 넘지 않는다. 수량이 없으면(기간만) 상한이 없다
    CONSTRAINT early_bird_claimed_within_quantity_ck CHECK (quantity IS NULL OR claimed_count <= quantity),
    CONSTRAINT early_bird_urgency_ck CHECK (highest_urgency_reached IN ('NONE', 'RUNNING_LOW', 'ALMOST_GONE'))
);

CREATE INDEX early_bird_event_ix ON early_bird (event_id)
    WHERE deleted_at IS NULL;

-- 조기예매가 끈 구역 한 줄(docs/05 "적용 범위 — 구역별로 끌 수 있습니다"). 도면에서 구역이 지워지면 줄을 지운다 — 지워진 구역은 팔리지 않아
--   닿는 티켓이 바뀌지 않는다(도메인 EarlyBird#dropRemovedZone).
CREATE TABLE early_bird_excluded_zone (
    early_bird_id BIGINT NOT NULL,
    event_id      BIGINT NOT NULL,
    zone_id       BIGINT NOT NULL,
    CONSTRAINT early_bird_excluded_zone_pk PRIMARY KEY (early_bird_id, zone_id),
    CONSTRAINT early_bird_excluded_zone_early_bird_fk FOREIGN KEY (event_id, early_bird_id)
        REFERENCES early_bird (event_id, id),
    CONSTRAINT early_bird_excluded_zone_zone_fk FOREIGN KEY (event_id, zone_id)
        REFERENCES event_zone (event_id, id)
);

-- 조기예매가 끈 묶음권 상품 한 줄(docs/05 "상품별로도 끌 수 있습니다 — 각 묶음권"). 묶음권 상품을 지우면 줄을 지운다(도메인
--   EarlyBird#dropRemovedBundleProduct).
CREATE TABLE early_bird_excluded_bundle_product (
    early_bird_id     BIGINT NOT NULL,
    event_id          BIGINT NOT NULL,
    bundle_product_id BIGINT NOT NULL,
    CONSTRAINT early_bird_excluded_bundle_product_pk PRIMARY KEY (early_bird_id, bundle_product_id),
    CONSTRAINT early_bird_excluded_bundle_product_early_bird_fk FOREIGN KEY (event_id, early_bird_id)
        REFERENCES early_bird (event_id, id),
    CONSTRAINT early_bird_excluded_bundle_product_product_fk FOREIGN KEY (event_id, bundle_product_id)
        REFERENCES bundle_product (event_id, id)
);

-- 쿠폰 한 줄 = 주최자가 자기 행사에 쓸 코드를 발급한 것(도메인 Coupon). 플랫폼 전체 쿠폰은 없다(docs/99 B).
-- 코드(code): 앞뒤 공백을 걷고 NFC · 대문자로 맞춘 그대로, 20자까지, 안에 공백이 없다(도메인 CouponCode, docs/99 C) — 대문자로 맞춰 두어
--   대소문자만 다른 코드는 같은 값이다. 발급한 뒤 바꾸지 않는다.
-- 할인(discount_kind · discount_value): 조기예매와 같다.
-- 전체 사용 가능 횟수(usage_limit): 선착순 N회, 꼭 있다(제한 없는 쿠폰 없음, docs/99 C).
-- 사용 수(used_count): 쿠폰을 쓴 예매 건 수. 전체 취소 · 결제가 끝나지 않은 예매면 하나 돌려준다(docs/05 "취소하면 쿠폰은 돌아옵니다").
-- 1인 1회(one_per_person), 유효 기간(valid_from · valid_until — 시작 순간부터 끝 순간 직전까지).
CREATE TABLE coupon (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY,
    event_id       BIGINT      NOT NULL,
    code           VARCHAR(20) NOT NULL,
    discount_kind  VARCHAR(20) NOT NULL,
    discount_value BIGINT      NOT NULL,
    usage_limit    INTEGER     NOT NULL,
    used_count     INTEGER     NOT NULL DEFAULT 0,
    one_per_person BOOLEAN     NOT NULL,
    valid_from     TIMESTAMPTZ NOT NULL,
    valid_until    TIMESTAMPTZ NOT NULL,
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT coupon_pk PRIMARY KEY (id),
    CONSTRAINT coupon_event_fk FOREIGN KEY (event_id)
        REFERENCES event (id),
    CONSTRAINT coupon_code_ck CHECK (char_length(code) > 0),
    -- 대소문자를 구분하지 않으려고 대문자로 맞춘 값만 받는다(운영진 초대의 소문자 이메일 칸과 같은 방식, V12)
    CONSTRAINT coupon_code_uppercase_ck CHECK (code = upper(code)),
    CONSTRAINT coupon_code_space_ck CHECK (position(' ' IN code) = 0),
    CONSTRAINT coupon_discount_kind_ck CHECK (discount_kind IN ('FIXED_AMOUNT', 'RATE')),
    CONSTRAINT coupon_discount_value_ck CHECK (
        (discount_kind <> 'FIXED_AMOUNT' OR discount_value >= 1)
        AND (discount_kind <> 'RATE' OR (discount_value >= 1 AND discount_value <= 99))),
    CONSTRAINT coupon_usage_limit_ck CHECK (usage_limit >= 1),
    CONSTRAINT coupon_used_count_ck CHECK (used_count >= 0),
    CONSTRAINT coupon_used_within_limit_ck CHECK (used_count <= usage_limit),
    CONSTRAINT coupon_validity_ck CHECK (valid_until > valid_from)
);

-- 한 행사의 지우지 않은 쿠폰끼리는 코드가 겹치지 않는다 — 관객은 코드로 쿠폰을 찾는다. 지운 쿠폰의 코드는 다시 쓸 수 있다. 행사의 쿠폰을 읽을 때
-- (지우지 않은 쿠폰)도 이 색인을 쓴다.
CREATE UNIQUE INDEX coupon_event_code_ux ON coupon (event_id, code)
    WHERE deleted_at IS NULL;

-- 쿠폰 사용 한 줄 = 한 예매 건이 그 쿠폰을 쓴 것. 1인 1회 쿠폰은 이 관객이 쓴 줄 가운데 돌려주지 않은 것이 있는지로 본다(도메인
--   Coupon#checkAvailableTo). 쓰고 돌려주는 일은 예매(#73)와 취소(#79)가 한다 — 이 이슈는 칸만 만든다.
-- booking_id: 그 쿠폰을 쓴 예매 건. 예매 건 표는 아직 없어(#73) 외래 키를 걸지 않았다 — 예매 건 표를 만드는 마이그레이션이
--   coupon_use_booking_fk를 더한다. 그 전에는 이 표에 줄을 넣는 흐름이 없다.
-- returned_at: 돌려준 시각 — 전체 취소로 돌려줬거나 결제가 끝나지 않아 되돌렸다. 돌려준 사용은 1인 1회에서 세지 않는다.
CREATE TABLE coupon_use (
    id                  BIGINT      GENERATED ALWAYS AS IDENTITY,
    coupon_id           BIGINT      NOT NULL,
    audience_account_id BIGINT      NOT NULL,
    booking_id          BIGINT      NOT NULL,
    used_at             TIMESTAMPTZ NOT NULL,
    returned_at         TIMESTAMPTZ,
    CONSTRAINT coupon_use_pk PRIMARY KEY (id),
    CONSTRAINT coupon_use_coupon_fk FOREIGN KEY (coupon_id)
        REFERENCES coupon (id),
    CONSTRAINT coupon_use_audience_account_fk FOREIGN KEY (audience_account_id)
        REFERENCES audience_account (id),
    CONSTRAINT coupon_use_returned_at_ck CHECK (returned_at IS NULL OR returned_at >= used_at)
);

-- 1인 1회 확인 — 이 관객이 이 쿠폰을 쓴, 돌려주지 않은 줄이 있는지.
CREATE INDEX coupon_use_coupon_account_ix ON coupon_use (coupon_id, audience_account_id)
    WHERE returned_at IS NULL;

-- 예매 건의 쿠폰 사용을 찾을 때(취소 · 변경).
CREATE INDEX coupon_use_booking_ix ON coupon_use (booking_id);
