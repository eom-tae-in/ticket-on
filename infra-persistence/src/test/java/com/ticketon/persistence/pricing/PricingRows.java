package com.ticketon.persistence.pricing;

import static com.ticketon.persistence.jooq.Tables.AUDIENCE_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.EVENT_DATE;
import static com.ticketon.persistence.jooq.Tables.EVENT_SCHEDULE;
import static com.ticketon.persistence.jooq.Tables.EVENT_SESSION;
import static com.ticketon.persistence.jooq.Tables.EVENT_ZONE;

import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventBasicInformation;
import com.ticketon.domain.event.EventCategoryCatalog;
import com.ticketon.domain.event.EventPublicAddress;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.persistence.event.EventPersistenceAdapter;
import com.ticketon.persistence.organizer.OrganizerRows;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jooq.DSLContext;

/**
 * 가격 표 테스트가 가리킬 줄 — 늦여름(docs/15 이도현)의 페스티벌 행사, 종일형 일정의 날짜 · 회차, 수량형 · 지정형 구역, 관객 계정. 표에 바로
 * 넣는다.
 */
final class PricingRows {

    private static final Instant CREATED_AT = Instant.parse("2026-02-01T01:00:00Z");

    private final DSLContext dsl;
    private int addressSequence;

    PricingRows(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** 주최자 · 운영진 표를 비우고(행사 · 가격 표도 함께 비워진다) 활동 중인 주최자 하나를 넣는다. */
    long freshOrganizer() {
        OrganizerRows rows = new OrganizerRows(dsl);
        rows.clear();
        dsl.truncate(AUDIENCE_ACCOUNT).cascade().execute();
        return rows.organizer("늦여름", OrganizerStatus.ACTIVE);
    }

    /** 작성 중인 페스티벌 행사 한 줄. */
    long festival(long organizerId, String title) {
        addressSequence++;
        String address = (addressSequence + "Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0").substring(0, 32);
        EventBasicInformation information = new EventBasicInformation(EventTitle.of(title), EventType.FESTIVAL,
                EventCategoryCatalog.initial().select(EventType.FESTIVAL, "음악", null), "늦여름이 해마다 여는 인디 음악 페스티벌입니다.",
                new Venue("한강 난지공원", "서울특별시 마포구 하늘공원로 108"), null, null, null);
        return new EventPersistenceAdapter(dsl).add(Event.create(organizerId, new EventPublicAddress(address), information, CREATED_AT))
                .id().getAsLong();
    }

    /** 종일형 일정 줄(12:00~22:00). */
    void allDaySchedule(long eventId) {
        dsl.insertInto(EVENT_SCHEDULE)
                .set(EVENT_SCHEDULE.EVENT_ID, eventId)
                .set(EVENT_SCHEDULE.SESSION_MODE, "ALL_DAY")
                .set(EVENT_SCHEDULE.DEFAULT_OPEN_TIME, LocalTime.of(12, 0))
                .set(EVENT_SCHEDULE.DEFAULT_CLOSE_TIME, LocalTime.of(22, 0))
                .execute();
    }

    /** 날짜 한 줄과 그 종일형 회차 한 줄 — 날짜 id를 돌려준다. */
    long dateWithSession(long eventId, LocalDate date) {
        long dateId = dsl.insertInto(EVENT_DATE)
                .set(EVENT_DATE.EVENT_ID, eventId)
                .set(EVENT_DATE.CALENDAR_DATE, date)
                .returningResult(EVENT_DATE.ID)
                .fetchSingle()
                .value1();
        dsl.insertInto(EVENT_SESSION)
                .set(EVENT_SESSION.EVENT_ID, eventId)
                .set(EVENT_SESSION.DATE_ID, dateId)
                .set(EVENT_SESSION.PRICE_ADJUSTMENT_KIND, "NONE")
                .execute();
        return dateId;
    }

    /** 수량형 구역 한 줄. */
    long quantityZone(long eventId, String name, long basePrice) {
        return zone(eventId, "QUANTITY", name, basePrice, 800);
    }

    /** 지정형 구역 한 줄. */
    long designatedZone(long eventId, String name, long basePrice) {
        return zone(eventId, "DESIGNATED", name, basePrice, null);
    }

    /** 이메일 인증을 마친 관객 계정 한 줄. */
    long audience(String email, String phoneNumber) {
        return dsl.insertInto(AUDIENCE_ACCOUNT)
                .set(AUDIENCE_ACCOUNT.STATUS, "ACTIVE")
                .set(AUDIENCE_ACCOUNT.NAME, "한서윤")
                .set(AUDIENCE_ACCOUNT.BIRTH_DATE, LocalDate.of(1998, 5, 3))
                .set(AUDIENCE_ACCOUNT.EMAIL, email)
                .set(AUDIENCE_ACCOUNT.EMAIL_LOWERCASE, email.toLowerCase())
                .set(AUDIENCE_ACCOUNT.PHONE_NUMBER, phoneNumber)
                .set(AUDIENCE_ACCOUNT.PASSWORD_HASH, "{test}hashed-password")
                .set(AUDIENCE_ACCOUNT.SIGNED_UP_AT, databaseTime(CREATED_AT))
                .set(AUDIENCE_ACCOUNT.EMAIL_VERIFIED_AT, databaseTime(CREATED_AT))
                .returningResult(AUDIENCE_ACCOUNT.ID)
                .fetchSingle()
                .value1();
    }

    static OffsetDateTime databaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private long zone(long eventId, String kind, String name, long basePrice, Integer capacity) {
        return dsl.insertInto(EVENT_ZONE)
                .set(EVENT_ZONE.EVENT_ID, eventId)
                .set(EVENT_ZONE.KIND, kind)
                .set(EVENT_ZONE.NAME, name)
                .set(EVENT_ZONE.COLOR, "#30A46C")
                .set(EVENT_ZONE.BASE_PRICE, basePrice)
                .set(EVENT_ZONE.CAPACITY, capacity)
                .returningResult(EVENT_ZONE.ID)
                .fetchSingle()
                .value1();
    }
}
