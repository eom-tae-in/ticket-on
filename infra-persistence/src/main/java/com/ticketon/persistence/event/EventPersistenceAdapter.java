package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.EVENT;
import static com.ticketon.persistence.jooq.Tables.EVENT_CANCELLATION_FEE_TIER;
import static com.ticketon.persistence.jooq.Tables.EVENT_CATEGORY;
import static com.ticketon.persistence.jooq.Tables.EVENT_SALES_PAUSE;

import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.domain.event.AgeRestriction;
import com.ticketon.domain.event.BookingSettings;
import com.ticketon.domain.event.CancellationPolicy;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventBasicInformation;
import com.ticketon.domain.event.EventCategory;
import com.ticketon.domain.event.EventCategoryName;
import com.ticketon.domain.event.EventCategorySelection;
import com.ticketon.domain.event.EventPasswordHash;
import com.ticketon.domain.event.EventPublicAddress;
import com.ticketon.domain.event.EventPublicContact;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.EventVisibility;
import com.ticketon.domain.event.EventVisibilitySetting;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.event.LeadTime;
import com.ticketon.domain.event.ReentrySetting;
import com.ticketon.domain.event.SalesPause;
import com.ticketon.domain.event.SalesPauseCause;
import com.ticketon.domain.event.SalesPauseReason;
import com.ticketon.domain.event.SeatsPerSelectionLimit;
import com.ticketon.domain.event.SessionChoiceRestriction;
import com.ticketon.domain.event.TicketsPerPersonBasis;
import com.ticketon.domain.event.TicketsPerPersonLimit;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.policy.CancellationFeeTier;
import com.ticketon.domain.policy.Percent;
import com.ticketon.persistence.jooq.tables.records.EventRecord;
import com.ticketon.persistence.jooq.tables.records.EventSalesPauseRecord;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.ResultQuery;
import org.jooq.SelectConditionStep;
import org.springframework.stereotype.Component;

/**
 * 행사 저장소(event · event_cancellation_fee_tier · event_sales_pause — V19) 어댑터.
 * <p>
 * 행사 한 줄에 기본 정보 · 공개 수준 · 예매 설정 · 상태 · 작성 진행 단계 · 마지막 수정 시각을 두고, 취소 규정 구간과 판매 중단 이력은 따로 둔다.
 * 정식 카테고리는 카테고리 표의 줄을 (id, 유형)으로 가리키고, '기타'면 장르명만 둔다. 예매 종료 · 검표 시작의 앞섬은 분 단위로 저장한다 — 분
 * 단위가 아닌 앞섬은 저장하지 않는다(들어오는 어댑터가 분으로 받는다).
 * <p>
 * 위저드 단계 저장은 {@link #findByIdForUpdate}({@code SELECT … FOR UPDATE})로 행사 줄을 잠그고 읽은 뒤 저장하므로, 저장은 조건 없이 그 행사의
 * 칸을 모두 지금 값으로 쓴다. 공개 주소 · 주최자는 바뀌지 않아 처음 넣을 때만 쓴다. 지운 행사(deleted_at)는 찾지도 저장하지도 않는다.
 * <p>
 * 포스터 칸은 포스터 업로드(#40)가 더한다. 그 전까지 포스터가 있는 행사는 저장하지 않는다 — 말없이 빠뜨리지 않게.
 */
@Component
public class EventPersistenceAdapter implements EventRepository {

    private final DSLContext dsl;

    public EventPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Event add(Event newEvent) {
        if (newEvent.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 행사입니다: " + newEvent.id().getAsLong());
        }
        requireNoPoster(newEvent);
        Map<Field<?>, Object> values = changeableColumns(newEvent);
        values.put(EVENT.ORGANIZER_ID, newEvent.organizerId());
        values.put(EVENT.PUBLIC_ADDRESS, newEvent.publicAddress().value());
        long id = dsl.insertInto(EVENT)
                .set(values)
                .returningResult(EVENT.ID)
                .fetchSingle()
                .value1();
        insertCancellationFeeTiers(id, newEvent.cancellationPolicy());
        saveSalesPauses(id, newEvent.salesPauses());
        return Event.restore(id, newEvent.organizerId(), newEvent.publicAddress(), newEvent.basicInformation(), null,
                newEvent.visibility(), newEvent.bookingSettings(), newEvent.cancellationPolicy(), newEvent.status(),
                newEvent.bookingOpenedAt().orElse(null), newEvent.salesPauses(), newEvent.wizardProgress(),
                newEvent.lastModifiedAt());
    }

    @Override
    public Optional<Event> findById(long id) {
        return find(eventRow(id));
    }

    @Override
    public Optional<Event> findByIdForUpdate(long id) {
        return find(eventRow(id).forUpdate());
    }

    @Override
    public void save(Event event) {
        long id = event.id().orElseThrow(() -> new IllegalArgumentException("저장 전 행사는 고쳐 저장할 수 없습니다"));
        requireNoPoster(event);
        int updated = dsl.update(EVENT)
                .set(changeableColumns(event))
                .where(EVENT.ID.eq(id))
                .and(EVENT.DELETED_AT.isNull())
                .execute();
        if (updated == 0) {
            throw new IllegalArgumentException("표에 없거나 지운 행사를 저장하려 했습니다: " + id);
        }
        dsl.deleteFrom(EVENT_CANCELLATION_FEE_TIER).where(EVENT_CANCELLATION_FEE_TIER.EVENT_ID.eq(id)).execute();
        insertCancellationFeeTiers(id, event.cancellationPolicy());
        saveSalesPauses(id, event.salesPauses());
    }

    /** 공개 주소 · 주최자를 뺀 행사 줄의 칸 — 넣을 때와 고칠 때 같다. */
    private Map<Field<?>, Object> changeableColumns(Event event) {
        EventBasicInformation information = event.basicInformation();
        EventVisibilitySetting visibility = event.visibility();
        BookingSettings settings = event.bookingSettings();
        Optional<TicketsPerPersonLimit.Limited> ticketsPerPerson = limitedTicketsPerPerson(settings.ticketsPerPerson());
        Map<Field<?>, Object> values = new LinkedHashMap<>();
        values.put(EVENT.STATUS, event.status().name());
        values.put(EVENT.TITLE, information.title().value());
        values.put(EVENT.EVENT_TYPE, information.type().name());
        values.put(EVENT.CATEGORY_ID, categoryIdOf(information.category()));
        values.put(EVENT.GENRE_NAME, information.category().isOther() ? information.category().genreName().value() : null);
        values.put(EVENT.INTRODUCTION, information.introduction());
        values.put(EVENT.VENUE_NAME, information.venue().name());
        values.put(EVENT.VENUE_ADDRESS, information.venue().address());
        values.put(EVENT.VENUE_DETAILS, information.venueDetails());
        values.put(EVENT.MAP_LINK, information.mapLink() == null ? null : information.mapLink().value());
        values.put(EVENT.PUBLIC_CONTACT, information.publicContact() == null ? null : information.publicContact().value());
        values.put(EVENT.VISIBILITY, visibility.visibility().name());
        values.put(EVENT.PASSWORD_HASH, visibility.passwordHash() == null ? null : visibility.passwordHash().value());
        values.put(EVENT.BOOKING_OPENS_AT, toDatabaseTime(settings.bookingOpensAt()));
        values.put(EVENT.BOOKING_CLOSE_LEAD_MINUTES, minutesOf(settings.bookingCloseLeadTime()));
        values.put(EVENT.CHECKIN_OPEN_LEAD_MINUTES, minutesOf(settings.checkinOpenLeadTime()));
        values.put(EVENT.MAX_SEATS_PER_SELECTION, settings.seatsPerSelection().maxSeats());
        values.put(EVENT.MAX_TICKETS_PER_PERSON, ticketsPerPerson.map(TicketsPerPersonLimit.Limited::maxTickets).orElse(null));
        values.put(EVENT.TICKETS_PER_PERSON_BASIS, ticketsPerPerson.map(limit -> limit.basis().name()).orElse(null));
        values.put(EVENT.SESSION_CHOICE, settings.sessionChoice().name());
        values.put(EVENT.REENTRY, settings.reentry().name());
        values.put(EVENT.MINIMUM_AGE, settings.ageRestriction().minimumAge());
        values.put(EVENT.BOOKING_OPENED_AT, toDatabaseTime(event.bookingOpenedAt().orElse(null)));
        values.put(EVENT.WIZARD_PROGRESS_STEP, event.wizardProgress().name());
        values.put(EVENT.LAST_MODIFIED_AT, toDatabaseTime(event.lastModifiedAt()));
        return values;
    }

    /** 고른 정식 카테고리의 줄 id. '기타'면 null. */
    private Long categoryIdOf(EventCategorySelection selection) {
        if (selection.isOther()) {
            return null;
        }
        EventCategory category = selection.category();
        return dsl.select(EVENT_CATEGORY.ID)
                .from(EVENT_CATEGORY)
                .where(EVENT_CATEGORY.EVENT_TYPE.eq(category.type().name()))
                .and(EVENT_CATEGORY.NAME.eq(category.name().value()))
                .fetchOptional(Record1::value1)
                .orElseThrow(() -> new IllegalArgumentException("카테고리 표에 없는 정식 카테고리입니다: " + category));
    }

    private void insertCancellationFeeTiers(long eventId, CancellationPolicy policy) {
        for (CancellationFeeTier tier : policy.tiers()) {
            dsl.insertInto(EVENT_CANCELLATION_FEE_TIER)
                    .set(EVENT_CANCELLATION_FEE_TIER.EVENT_ID, eventId)
                    .set(EVENT_CANCELLATION_FEE_TIER.FROM_DAYS_BEFORE, tier.fromDaysBefore())
                    .set(EVENT_CANCELLATION_FEE_TIER.DEDUCTION_RATE_PERCENT, tier.deductionRate().value())
                    .execute();
        }
    }

    /**
     * 판매 중단 이력을 멈춘 순서(sequence_number)대로 맞춘다 — 이력은 뒤에 붙기만 하고 바뀌는 칸은 끝난 시각뿐이라, 없는 순서는 넣고 있는 순서는
     * 끝난 시각만 고친다.
     */
    private void saveSalesPauses(long eventId, List<SalesPause> salesPauses) {
        for (int sequenceNumber = 0; sequenceNumber < salesPauses.size(); sequenceNumber++) {
            SalesPause pause = salesPauses.get(sequenceNumber);
            boolean byOrganizer = pause.cause() == SalesPauseCause.ORGANIZER;
            OffsetDateTime endedAt = toDatabaseTime(pause.endedAt());
            dsl.insertInto(EVENT_SALES_PAUSE)
                    .set(EVENT_SALES_PAUSE.EVENT_ID, eventId)
                    .set(EVENT_SALES_PAUSE.SEQUENCE_NUMBER, sequenceNumber)
                    .set(EVENT_SALES_PAUSE.CAUSE, pause.cause().name())
                    .set(EVENT_SALES_PAUSE.PAUSED_BY_STAFF_ACCOUNT_ID, byOrganizer ? pause.actorAccountId() : null)
                    .set(EVENT_SALES_PAUSE.PAUSED_BY_ADMIN_ACCOUNT_ID, byOrganizer ? null : pause.actorAccountId())
                    .set(EVENT_SALES_PAUSE.REASON, pause.reason() == null ? null : pause.reason().value())
                    .set(EVENT_SALES_PAUSE.PAUSED_AT, toDatabaseTime(pause.pausedAt()))
                    .set(EVENT_SALES_PAUSE.ENDED_AT, endedAt)
                    .onConflict(EVENT_SALES_PAUSE.EVENT_ID, EVENT_SALES_PAUSE.SEQUENCE_NUMBER)
                    .doUpdate()
                    .set(EVENT_SALES_PAUSE.ENDED_AT, endedAt)
                    .execute();
        }
    }

    private SelectConditionStep<EventRecord> eventRow(long id) {
        return dsl.selectFrom(EVENT).where(EVENT.ID.eq(id)).and(EVENT.DELETED_AT.isNull());
    }

    private Optional<Event> find(ResultQuery<EventRecord> query) {
        return query.fetchOptional().map(this::toEvent);
    }

    private Event toEvent(EventRecord row) {
        long id = row.getId();
        EventVisibilitySetting visibility = new EventVisibilitySetting(EventVisibility.valueOf(row.getVisibility()),
                row.getPasswordHash() == null ? null : new EventPasswordHash(row.getPasswordHash()));
        return Event.restore(id, row.getOrganizerId(), new EventPublicAddress(row.getPublicAddress()), toBasicInformation(row),
                null, visibility, toBookingSettings(row), cancellationPolicyOf(id), EventStatus.valueOf(row.getStatus()),
                toInstant(row.getBookingOpenedAt()), salesPausesOf(id), EventWizardStep.valueOf(row.getWizardProgressStep()),
                row.getLastModifiedAt().toInstant());
    }

    private EventBasicInformation toBasicInformation(EventRecord row) {
        EventType type = EventType.valueOf(row.getEventType());
        EventCategorySelection category = row.getCategoryId() == null
                ? EventCategorySelection.other(type, row.getGenreName())
                : EventCategorySelection.of(new EventCategory(type, new EventCategoryName(categoryNameOf(row.getCategoryId()))));
        return new EventBasicInformation(new EventTitle(row.getTitle()), type, category, row.getIntroduction(),
                new Venue(row.getVenueName(), row.getVenueAddress()), row.getVenueDetails(),
                row.getMapLink() == null ? null : new ExternalLink(row.getMapLink()),
                row.getPublicContact() == null ? null : new EventPublicContact(row.getPublicContact()));
    }

    private static BookingSettings toBookingSettings(EventRecord row) {
        TicketsPerPersonLimit ticketsPerPerson = row.getMaxTicketsPerPerson() == null
                ? TicketsPerPersonLimit.unlimited()
                : TicketsPerPersonLimit.of(row.getMaxTicketsPerPerson(), TicketsPerPersonBasis.valueOf(row.getTicketsPerPersonBasis()));
        return new BookingSettings(toInstant(row.getBookingOpensAt()),
                LeadTime.of(Duration.ofMinutes(row.getBookingCloseLeadMinutes())),
                LeadTime.of(Duration.ofMinutes(row.getCheckinOpenLeadMinutes())),
                SeatsPerSelectionLimit.of(row.getMaxSeatsPerSelection()), ticketsPerPerson,
                SessionChoiceRestriction.valueOf(row.getSessionChoice()), ReentrySetting.valueOf(row.getReentry()),
                new AgeRestriction(row.getMinimumAge()));
    }

    private String categoryNameOf(long categoryId) {
        return dsl.select(EVENT_CATEGORY.NAME)
                .from(EVENT_CATEGORY)
                .where(EVENT_CATEGORY.ID.eq(categoryId))
                .fetchSingle()
                .value1();
    }

    private CancellationPolicy cancellationPolicyOf(long eventId) {
        return CancellationPolicy.of(dsl.select(EVENT_CANCELLATION_FEE_TIER.FROM_DAYS_BEFORE,
                        EVENT_CANCELLATION_FEE_TIER.DEDUCTION_RATE_PERCENT)
                .from(EVENT_CANCELLATION_FEE_TIER)
                .where(EVENT_CANCELLATION_FEE_TIER.EVENT_ID.eq(eventId))
                .fetch(row -> new CancellationFeeTier(row.value1(), Percent.of(row.value2()))));
    }

    private List<SalesPause> salesPausesOf(long eventId) {
        return dsl.selectFrom(EVENT_SALES_PAUSE)
                .where(EVENT_SALES_PAUSE.EVENT_ID.eq(eventId))
                .orderBy(EVENT_SALES_PAUSE.SEQUENCE_NUMBER)
                .fetch(EventPersistenceAdapter::toSalesPause);
    }

    private static SalesPause toSalesPause(EventSalesPauseRecord row) {
        SalesPauseCause cause = SalesPauseCause.valueOf(row.getCause());
        long actorAccountId = cause == SalesPauseCause.ORGANIZER ? row.getPausedByStaffAccountId()
                : row.getPausedByAdminAccountId();
        return new SalesPause(cause, actorAccountId, row.getReason() == null ? null : new SalesPauseReason(row.getReason()),
                row.getPausedAt().toInstant(), toInstant(row.getEndedAt()));
    }

    private static Optional<TicketsPerPersonLimit.Limited> limitedTicketsPerPerson(TicketsPerPersonLimit limit) {
        return limit instanceof TicketsPerPersonLimit.Limited limited ? Optional.of(limited) : Optional.empty();
    }

    /** 앞섬을 분으로 — 분 단위가 아니면 저장하지 않는다. */
    private static int minutesOf(LeadTime leadTime) {
        Duration duration = leadTime.duration();
        if (duration.toSecondsPart() != 0 || duration.toNanosPart() != 0) {
            throw new IllegalArgumentException("기준 시각보다 앞서는 시간은 분 단위로 저장합니다: " + duration);
        }
        return Math.toIntExact(duration.toMinutes());
    }

    private static void requireNoPoster(Event event) {
        if (event.poster().isPresent()) {
            throw new IllegalStateException("포스터 칸이 아직 없습니다(#40) — 포스터가 있는 행사를 저장하지 않습니다: " + event.id());
        }
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }
}
