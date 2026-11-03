package com.ticketon.domain.pricing;

import com.ticketon.domain.event.EventDate;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventSession;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.OperatingHours;
import com.ticketon.domain.event.SessionMode;
import com.ticketon.domain.event.SessionPriceAdjustment;
import com.ticketon.domain.event.TimedSessionTime;
import com.ticketon.domain.event.Zone;
import com.ticketon.domain.event.ZoneBasePrice;
import com.ticketon.domain.event.ZoneCapacity;
import com.ticketon.domain.event.ZoneColor;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.policy.Percent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** 묶음권 상품 테스트에 쓰는 문서 속 행사들 (docs/05 "묶음권 (수량형 전용)", docs/14). */
final class BundleProductFixtures {

    /** 페스티벌(종일형, 날짜당 회차 1개) — 5/3 금 · 5/4 토 · 5/5 일 · 5/6 월 · 5/7 화. */
    static final long FESTIVAL_EVENT_ID = 7L;

    static final long MAY_3 = 53L;
    static final long MAY_4 = 54L;
    static final long MAY_5 = 55L;
    static final long MAY_6 = 56L;
    static final long MAY_7 = 57L;

    static final long SESSION_MAY_3 = 503L;
    static final long SESSION_MAY_4 = 504L;
    static final long SESSION_MAY_5 = 505L;
    static final long SESSION_MAY_6 = 506L;
    static final long SESSION_MAY_7 = 507L;

    /** 스탠딩A — 수량형, 기본가 40,000원. 금 조정 없음, 토 · 일 +10,000원 → 40,000 · 50,000 · 50,000원 (docs/05 "가격은 할인율로 계산합니다"). */
    static final long STANDING_A = 4L;

    /** 스탠딩B — 수량형, 기본가 30,000원. */
    static final long STANDING_B = 5L;

    /** 테이블석 — 지정형 (docs/02 "페스티벌에 테이블석만 지정형으로 둘 수도 있습니다"). */
    static final long TABLE_SEATS = 6L;

    /** 어린이 — 수량형, 기본가 3,000원. 할인율을 높이면 묶음권 가격이 0원이 되는 싼 구역. */
    static final long CHILDREN = 10L;

    /** 시간대를 나눈 전시(회차형) — 5/3 낮 · 5/4 낮 · 야간 · 5/5 낮 (docs/05 "조합은 자동 생성하지 않습니다"). */
    static final long EXHIBITION_EVENT_ID = 8L;

    static final long EXHIBITION_MAY_3 = 63L;
    static final long EXHIBITION_MAY_4 = 64L;
    static final long EXHIBITION_MAY_5 = 65L;

    static final long DAY_MAY_3 = 631L;
    static final long DAY_MAY_4 = 641L;
    static final long NIGHT_MAY_4 = 642L;
    static final long DAY_MAY_5 = 651L;

    /** 전시 전체 — 수량형, 기본가 15,000원. 5/4 야간만 +5,000원 → 20,000원. */
    static final long EXHIBITION_ZONE = 9L;

    private static final OperatingHours FESTIVAL_HOURS = OperatingHours.of(LocalTime.of(12, 0), LocalTime.of(22, 0));

    private BundleProductFixtures() {
    }

    static EventSchedule festivalSchedule() {
        return EventSchedule.restore(FESTIVAL_EVENT_ID, SessionMode.ALL_DAY, FESTIVAL_HOURS, List.of(
                allDayDate(MAY_3, LocalDate.of(2026, 5, 3), SESSION_MAY_3, SessionPriceAdjustment.none()),
                allDayDate(MAY_4, LocalDate.of(2026, 5, 4), SESSION_MAY_4, SessionPriceAdjustment.amount(10_000)),
                allDayDate(MAY_5, LocalDate.of(2026, 5, 5), SESSION_MAY_5, SessionPriceAdjustment.amount(10_000)),
                allDayDate(MAY_6, LocalDate.of(2026, 5, 6), SESSION_MAY_6, SessionPriceAdjustment.none()),
                allDayDate(MAY_7, LocalDate.of(2026, 5, 7), SESSION_MAY_7, SessionPriceAdjustment.none())));
    }

    static FloorPlan festivalFloorPlan() {
        return FloorPlan.restore(FESTIVAL_EVENT_ID, List.of(
                quantityZone(STANDING_A, "스탠딩A", 40_000),
                quantityZone(STANDING_B, "스탠딩B", 30_000),
                quantityZone(CHILDREN, "어린이", 3_000),
                Zone.restore(TABLE_SEATS, ZoneKind.DESIGNATED, ZoneName.of("테이블석"), ZoneColor.of("#FFAA00"), ZoneBasePrice.of(60_000), null)),
                List.of(), null);
    }

    static EventSchedule exhibitionSchedule() {
        return exhibitionSchedule(timedSession(DAY_MAY_4, 10, SessionPriceAdjustment.none()));
    }

    /** 5/4 낮 회차가 취소된 전시. */
    static EventSchedule exhibitionScheduleWithDayMay4Cancelled() {
        return exhibitionSchedule(EventSession.restore(DAY_MAY_4, TimedSessionTime.startingAt(LocalTime.of(10, 0)), null,
                SessionPriceAdjustment.none(), Instant.parse("2026-04-20T00:00:00Z")));
    }

    private static EventSchedule exhibitionSchedule(EventSession dayMay4) {
        return EventSchedule.restore(EXHIBITION_EVENT_ID, SessionMode.TIMED, null, List.of(
                EventDate.restore(EXHIBITION_MAY_3, LocalDate.of(2026, 5, 3), null, List.of(
                        timedSession(DAY_MAY_3, 10, SessionPriceAdjustment.none()))),
                EventDate.restore(EXHIBITION_MAY_4, LocalDate.of(2026, 5, 4), null, List.of(
                        dayMay4,
                        timedSession(NIGHT_MAY_4, 18, SessionPriceAdjustment.amount(5_000)))),
                EventDate.restore(EXHIBITION_MAY_5, LocalDate.of(2026, 5, 5), null, List.of(
                        timedSession(DAY_MAY_5, 10, SessionPriceAdjustment.none())))));
    }

    static FloorPlan exhibitionFloorPlan() {
        return FloorPlan.restore(EXHIBITION_EVENT_ID, List.of(quantityZone(EXHIBITION_ZONE, "전체", 15_000)), List.of(), null);
    }

    /** 페스티벌 스탠딩A "3일 통권" — 5/3 · 5/4 · 5/5, 7% (docs/05 "묶음권 만들기"). */
    static BundleProductTerms festivalThreeDayPass() {
        return new BundleProductTerms(BundleProductName.of("3일 통권"), Set.of(MAY_3, MAY_4, MAY_5), Set.of(STANDING_A), Percent.of(7));
    }

    /** 전시 3일권 — 5/3 · 5/4 · 5/5, 7%. */
    static BundleProductTerms exhibitionThreeDayPass() {
        return new BundleProductTerms(BundleProductName.of("3일권"), Set.of(EXHIBITION_MAY_3, EXHIBITION_MAY_4, EXHIBITION_MAY_5),
                Set.of(EXHIBITION_ZONE), Percent.of(7));
    }

    static BundleProductTerms terms(Set<Long> dateIds, Set<Long> zoneIds, int discountPercent) {
        return new BundleProductTerms(BundleProductName.of("묶음권"), dateIds, zoneIds, Percent.of(discountPercent));
    }

    private static EventDate allDayDate(long dateId, LocalDate date, long sessionId, SessionPriceAdjustment adjustment) {
        return EventDate.restore(dateId, date, null, List.of(EventSession.restore(sessionId, null, null, adjustment, null)));
    }

    private static EventSession timedSession(long sessionId, int startHour, SessionPriceAdjustment adjustment) {
        return EventSession.restore(sessionId, TimedSessionTime.startingAt(LocalTime.of(startHour, 0)), null, adjustment, null);
    }

    private static Zone quantityZone(long zoneId, String name, long basePrice) {
        return Zone.restore(zoneId, ZoneKind.QUANTITY, ZoneName.of(name), ZoneColor.of("#3366FF"), ZoneBasePrice.of(basePrice),
                ZoneCapacity.of(500));
    }
}
