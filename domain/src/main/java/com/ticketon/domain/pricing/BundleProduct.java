package com.ticketon.domain.pricing;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventDate;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventSession;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.Zone;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.policy.Percent;
import com.ticketon.domain.policy.QuantityPolicy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * 묶음권 상품 — 날짜 N개를 묶은 상품(docs/05 "묶음권 (수량형 전용)", docs/14 "묶음권 상품은 날짜를, 티켓은 회차를 가리킵니다"). 3일 행사에서
 * "1일권"과 "3일 통권"을 함께 파는 경우다. 행사를 가리키고, 행사 안에서 주최자가 필요한 조합만 직접 정한다(docs/99 B "묶음권 조합 자동 생성").
 * <ul>
 *   <li><b>날짜를 가리킨다</b> — 상품은 날짜를, 그 상품으로 산 티켓은 날짜마다 회차 하나씩 N개를 가리킨다. 주최자는 날짜만 고르고, 날짜 아래 회차가
 *       여럿이면 관객이 예매할 때 고른다. 페스티벌(날짜당 회차 1개)은 날짜가 곧 회차고, 시간대를 나눈 전시(날짜당 회차 3개)는 관객이 날짜마다
 *       시간대를 고른다.</li>
 *   <li><b>수량형 전용</b> — 지정석 묶음권은 날짜 수만큼 좌석을 동시에 선점해야 하고, 마지막 날 자리가 없으면 앞의 둘도 무의미해진다. 지정석은 회차별
 *       개별 예매만 한다(docs/05 "왜 수량형 전용인가", docs/99 B "지정석 묶음권 / 다회차 할인"). 대상 구역은 수량형만 받는다.</li>
 *   <li><b>가격은 할인율로 계산한다</b> — 가격을 직접 받지 않는다. 날짜별 가격 합계 × (1 − 할인율), 100원 내림({@link BundleDayPrices#bundlePrice}).
 *       회차별 가격 조정이 저절로 반영된다(docs/99 D "묶음권 가격"). 할인율은 기본값({@link #defaultDiscountRate})을 채워 두고 주최자가 바꾼다.
 *       상품 목록에는 가장 싼 회차 조합의 가격("3일권 40,000원~", {@link #startingPrice})이 보이고, 관객이 회차를 다 고르면 확정된다
 *       ({@link #priceFor}).</li>
 *   <li><b>매수 세는 법</b> — 1인 총 매수에서는 날짜마다 1매({@link #ticketsPerPersonCountByDate}), 1회 선택 매수에서는 티켓 장수
 *       ({@link #seatsPerSelectionCount})로 센다(docs/05 "매수 제한 계산").</li>
 * </ul>
 * 조기예매의 상품별 켜고 끔은 이 상품을 {@link TicketProduct#bundle}로 가리킨다({@link #product}). 날짜마다 들어갈 회차가 남았는지(판매 멈춤)는
 * 재고가 본다({@code domain.inventory.BundleAvailability}).
 * <p>
 * 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 상품은 id가 없다.
 */
public final class BundleProduct {

    private final Long id;
    private final long eventId;
    private BundleProductTerms terms;

    private BundleProduct(Long id, long eventId, BundleProductTerms terms) {
        if (eventId < 1) {
            throw new IllegalArgumentException("행사 id는 1 이상이어야 합니다: " + eventId);
        }
        this.id = id;
        this.eventId = eventId;
        this.terms = Objects.requireNonNull(terms, "terms");
    }

    /**
     * 주최자가 묶음권 상품을 만든다. 날짜와 구역이 그 행사의 것이어야 하고, 구역은 수량형이어야 한다. 할인율은 구역마다 가장 싼 회차 조합에 적용해도
     * 1원 이상 남겨야 한다.
     *
     * @param schedule  그 행사의 일정 — 포함 날짜를 찾는다
     * @param floorPlan 그 행사의 도면 — 대상 구역을 찾는다
     * @throws NotFoundException        일정에 없는 날짜면({@link EventErrorCode#SCHEDULE_DATE_NOT_FOUND}), 도면에 없는 구역이면
     *                                  ({@link EventErrorCode#ZONE_NOT_FOUND})
     * @throws RuleViolationException   지정형 구역이 있으면({@link PricingErrorCode#BUNDLE_ZONE_NOT_QUANTITY}), 가장 싼 회차 조합이 0원이 되면
     *                                  ({@link PricingErrorCode#BUNDLE_PRICE_NOT_POSITIVE})
     * @throws IllegalArgumentException 일정 · 도면이 다른 행사의 것이면
     */
    public static BundleProduct create(long eventId, BundleProductTerms terms, EventSchedule schedule, FloorPlan floorPlan) {
        BundleProduct product = new BundleProduct(null, eventId, terms);
        product.requireApplicable(terms, schedule, floorPlan);
        return product;
    }

    /**
     * 저장소에서 읽은 값으로 묶음권 상품을 되살린다. 구역의 종류는 바뀌지 않으므로 다시 보지 않는다.
     */
    public static BundleProduct restore(long id, long eventId, BundleProductTerms terms) {
        if (id < 1) {
            throw new IllegalArgumentException("묶음권 상품 id는 1 이상이어야 합니다: " + id);
        }
        return new BundleProduct(id, eventId, terms);
    }

    /**
     * 날짜 수에 따라 채워 둘 기본 할인율(docs/05 "가격은 할인율로 계산합니다 — 기본 할인율", docs/99 C "묶음권 할인 기본값").
     * <table>
     *   <caption>기본 할인율</caption>
     *   <tr><th>날짜 수</th><th>할인율</th></tr>
     *   <tr><td>2일</td><td>3%</td></tr>
     *   <tr><td>3일</td><td>7%</td></tr>
     *   <tr><td>4일</td><td>10%</td></tr>
     * </table>
     * <b>주최자가 바꿀 수 있다</b> — 고정하면 "우리는 3일권을 15% 깎고 싶다"를 못 한다. 기본값만 채워 두고 바꿀 수 있게 둔다.
     * <p>
     * 확인 필요 (#52) — 5일 이상은 비어 있다. 문서에는 "5일 이상 | 최대 20%까지"(docs/05), "최대 20%"(docs/99 C)만 있어 5일 이상에 채울 값과, 20%가
     * 주최자가 정할 수 있는 상한인지가 정해지지 않았다. 그때까지 5일 이상 묶음권은 주최자가 할인율을 직접 적고, 상한은 두지 않는다.
     *
     * @param dateCount 묶는 날짜의 수, 2 이상
     * @return 채워 둘 할인율. 5일 이상이면 비어 있다
     * @throws IllegalArgumentException dateCount가 2보다 작으면
     */
    public static Optional<Percent> defaultDiscountRate(int dateCount) {
        if (dateCount < BundleProductTerms.MINIMUM_DATE_COUNT) {
            throw new IllegalArgumentException("묶음권은 날짜를 2개 이상 묶습니다: " + dateCount);
        }
        return switch (dateCount) {
            case 2 -> Optional.of(QuantityPolicy.DEFAULT_BUNDLE_DISCOUNT_TWO_DAYS);
            case 3 -> Optional.of(QuantityPolicy.DEFAULT_BUNDLE_DISCOUNT_THREE_DAYS);
            case 4 -> Optional.of(QuantityPolicy.DEFAULT_BUNDLE_DISCOUNT_FOUR_DAYS);
            default -> Optional.empty();
        };
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public long eventId() {
        return eventId;
    }

    public BundleProductTerms terms() {
        return terms;
    }

    /**
     * 이 상품을 가리키는 값 — 조기예매의 상품별 켜고 끔({@link EarlyBirdScope})이 쓴다. 3일권 · 2일권처럼 상품마다 따로 켜고 끈다.
     *
     * @throws IllegalStateException 저장 전이면
     */
    public TicketProduct product() {
        if (id == null) {
            throw new IllegalStateException("저장 전 묶음권 상품은 가리킬 수 없습니다");
        }
        return TicketProduct.bundle(id);
    }

    /**
     * 설정을 바꾼다 — 이름 · 날짜 · 구역 · 할인율. 만들 때와 같은 규칙을 본다. 예매가 열린 뒤 바꿀 수 있는지는 부르는 쪽이 본다(확인 필요 #223,
     * {@code domain.event.EventEditTarget#PRICE}).
     *
     * @return 바뀌었는지. 같은 설정이면 false
     * @throws NotFoundException      일정에 없는 날짜, 도면에 없는 구역이면
     * @throws RuleViolationException 지정형 구역이 있으면({@link PricingErrorCode#BUNDLE_ZONE_NOT_QUANTITY}), 가장 싼 회차 조합이 0원이 되면
     *                                ({@link PricingErrorCode#BUNDLE_PRICE_NOT_POSITIVE})
     */
    public boolean reconfigure(BundleProductTerms newTerms, EventSchedule schedule, FloorPlan floorPlan) {
        Objects.requireNonNull(newTerms, "newTerms");
        if (newTerms.equals(terms)) {
            return false;
        }
        requireApplicable(newTerms, schedule, floorPlan);
        terms = newTerms;
        return true;
    }

    /**
     * 묶은 날짜가 지워지고 그 회차가 다른 날짜로 옮겨 가면 따라간다 — 묶음권 상품은 날짜를 가리키고, 가리키는 것은 바뀌면 따라 바뀐다(docs/14 "무엇을
     * 가리키고 무엇을 직접 갖나"). 연기된 회차도 계속 판다(docs/04 "연기된 회차도 계속 팝니다").
     * <ul>
     *   <li>종일형 회차의 날짜를 옮기면 옮겨 간 날짜가 새로 생기고(새 id) 원래 날짜는 지워진다({@link EventSchedule#changeAllDaySessionDate}).</li>
     *   <li>회차형도 날짜의 마지막 회차를 다른 날짜로 옮기면 원래 날짜가 지워진다({@link EventSchedule#changeTimedSessionDateTime}). 회차가 남은 날짜는
     *       그대로라 따라갈 것이 없다.</li>
     * </ul>
     * 확인 필요 (#223) — 옮겨 간 날짜를 이미 묶고 있으면(회차형에서 묶은 두 날짜가 합쳐지면) 따라가지 않는다. 따라가면 3일권이 말없이 2일권이 된다.
     * 지워진 날짜에는 고를 회차가 없어 그 묶음권은 팔리지 않고, 주최자가 날짜를 다시 고른다({@link #reconfigure}).
     *
     * @param retiredDateId 지워진 날짜
     * @param movedToDateId 그 날짜의 회차가 옮겨 간 날짜
     * @return 바뀌었는지 — 지워진 날짜를 묶지 않았거나, 옮겨 간 날짜를 이미 묶었으면 false
     */
    public boolean followMovedDate(long retiredDateId, long movedToDateId) {
        if (!terms.dateIds().contains(retiredDateId) || terms.dateIds().contains(movedToDateId)) {
            return false;
        }
        Set<Long> movedDateIds = new HashSet<>(terms.dateIds());
        movedDateIds.remove(retiredDateId);
        movedDateIds.add(movedToDateId);
        terms = new BundleProductTerms(terms.name(), movedDateIds, terms.zoneIds(), terms.discountRate());
        return true;
    }

    /**
     * 상품 목록에 보일 시작 가격 — <b>가장 싼 회차 조합</b>의 묶음권 가격("3일권 40,000원~", docs/05 "조합은 자동 생성하지 않습니다"). 회차를 다
     * 고르면 금액이 확정된다({@link #priceFor}).
     * <p>
     * 같은 날짜의 회차끼리 가격이 다르면 관객이 고른 회차의 가격으로 계산하므로, 날짜마다 가장 싼 회차를 고른 조합이 가장 싸다. 묶음권 가격은 날짜별
     * 가격 합계가 작을수록 작으므로({@link BundleDayPrices#bundlePrice} — 100원 내림) 구역마다 그 조합의 가격을 내고 가장 싼 구역의 것을 보인다.
     * <blockquote>
     * 전시 3일권 7%. 5/3 15,000원 · 5/4 낮 15,000원 / 야간 20,000원 · 5/5 15,000원<br>
     * 가장 싼 조합 15,000 + 15,000 + 15,000 = 45,000원 → 41,800원~
     * </blockquote>
     * 관객이 고를 수 있는 회차만 본다 — 취소되지 않았고, 부르는 쪽이 넘긴 예매할 수 있는 회차. 한 날짜라도 고를 회차가 없는 구역은 빼고, 어느 구역도
     * 조합이 없으면 비어 있다(그 묶음권은 지금 살 수 없다).
     *
     * @param bookableSessionIds 지금 예매할 수 있는 회차 — 그 회차의 예매가 마감되지 않은 회차. 부르는 쪽이 고른다
     * @return 시작 가격. 살 수 있는 조합이 없으면 비어 있다
     */
    public Optional<Money> startingPrice(EventSchedule schedule, FloorPlan floorPlan, Set<Long> bookableSessionIds) {
        requireSameEvent(schedule, floorPlan);
        Set<Long> bookable = Set.copyOf(Objects.requireNonNull(bookableSessionIds, "bookableSessionIds"));
        Predicate<EventSession> selectable = session -> !session.isCancelled()
                && session.id().isPresent() && bookable.contains(session.id().getAsLong());
        return terms.zoneIds().stream()
                .map(floorPlan::zone)
                .map(zone -> cheapestDayPrices(terms, zone, schedule, selectable))
                .flatMap(Optional::stream)
                .map(dayPrices -> dayPrices.bundlePrice(terms.discountRate()))
                .min(Comparator.naturalOrder());
    }

    /**
     * 관객이 고른 회차의 날짜별 가격 — 날짜마다 고른 회차의 가격을 날짜 순서대로 담는다(docs/05 "같은 날짜의 회차끼리 가격이 다르면, 관객이 고른
     * 회차의 가격으로 계산합니다"). 티켓이 고른 회차를 가리키므로 할인 전 합계 · 환불(그날 몫) 계산이 모두 이 가격으로 맞아떨어진다.
     * <blockquote>
     * 전시 3일권. 5/4 낮 회차 15,000원, 야간 회차 20,000원(회차별 조정 +5,000원).<br>
     * 낮을 고르면 5/4 몫은 15,000원, 야간을 고르면 20,000원으로 합계를 냅니다.
     * </blockquote>
     * 회차가 취소됐는지, 그 회차의 예매가 마감됐는지는 예매 자격 판정이 본다. 여기서는 가격만 낸다.
     *
     * @param zoneId           관객이 고른 구역 — 이 묶음권을 파는 구역
     * @param chosenSessionIds 관객이 고른 회차 — 묶은 날짜마다 하나씩
     * @throws RuleViolationException 이 묶음권을 팔지 않는 구역이면({@link PricingErrorCode#BUNDLE_ZONE_NOT_INCLUDED}), 회차가 날짜마다 하나씩이
     *                                아니면({@link PricingErrorCode#BUNDLE_SESSIONS_MISMATCH})
     * @throws NotFoundException      일정에 없는 회차면({@link EventErrorCode#SESSION_NOT_FOUND})
     */
    public BundleDayPrices dayPricesFor(long zoneId, List<Long> chosenSessionIds, EventSchedule schedule, FloorPlan floorPlan) {
        requireSameEvent(schedule, floorPlan);
        Objects.requireNonNull(chosenSessionIds, "chosenSessionIds");
        if (!terms.zoneIds().contains(zoneId)) {
            throw new RuleViolationException(PricingErrorCode.BUNDLE_ZONE_NOT_INCLUDED, "묶음권 " + id + ", 구역 " + zoneId);
        }
        Zone zone = floorPlan.zone(zoneId);
        Map<LocalDate, SessionPrice> pricesByDate = new TreeMap<>();
        for (long sessionId : chosenSessionIds) {
            EventDate eventDate = schedule.dateOf(sessionId);
            if (!includes(terms, eventDate) || pricesByDate.containsKey(eventDate.date())) {
                throw sessionsMismatch(chosenSessionIds);
            }
            pricesByDate.put(eventDate.date(), SessionPrice.of(zone.basePrice(), schedule.session(sessionId).priceAdjustment()));
        }
        if (pricesByDate.size() != terms.dateCount()) {
            throw sessionsMismatch(chosenSessionIds);
        }
        return BundleDayPrices.of(List.copyOf(pricesByDate.values()));
    }

    /**
     * 관객이 회차를 다 골라 확정된 묶음권 가격 = 날짜별 가격 합계 × (1 − 할인율), 100원 내림(docs/05 "가격은 할인율로 계산합니다").
     * <blockquote>5/3 (금) 40,000원 · 5/4 (토) 50,000원 · 5/5 (일) 50,000원 → 합계 140,000원 → 3일권 7% 할인 → 130,200원</blockquote>
     * 이 가격 위에 조기예매 또는 쿠폰 중 유리한 하나가 붙는다({@link BookingPrice}).
     *
     * @see #dayPricesFor
     */
    public Money priceFor(long zoneId, List<Long> chosenSessionIds, EventSchedule schedule, FloorPlan floorPlan) {
        return dayPricesFor(zoneId, chosenSessionIds, schedule, floorPlan).bundlePrice(terms.discountRate());
    }

    /**
     * 1인 총 매수 "날짜별"로 세는 매수 — <b>3일권 1장 = 날짜마다 1매</b>(docs/05 "매수 제한 계산"). 날짜별 4매 제한이면 3일권 1장이 5/3에 1매, 5/4에
     * 1매, 5/5에 1매를 차지한다. 매수 제한은 "그 날 몇 자리를 차지하는가"를 보는 장치라 그대로 센다. 실제로 세어 막는 일은 예매 자격 판정이 한다
     * ({@code domain.event.TicketsPerPersonLimit}).
     *
     * @param ticketCount 이번에 사는 이 묶음권의 장수, 1 이상
     * @return 묶은 날짜(id)마다 차지하는 매수 — 모두 ticketCount
     * @throws IllegalArgumentException ticketCount가 1보다 작으면
     */
    public Map<Long, Integer> ticketsPerPersonCountByDate(int ticketCount) {
        requirePositiveTicketCount(ticketCount);
        Map<Long, Integer> countByDate = new HashMap<>();
        terms.dateIds().forEach(dateId -> countByDate.put(dateId, ticketCount));
        return Map.copyOf(countByDate);
    }

    /**
     * 1인 총 매수 "행사 전체"로 세는 매수 — 날짜마다 1매를 더해 <b>3일권 1장 = 3매</b>다. 날짜마다 1매로 센다는 규칙(docs/05 "매수 제한 계산")을 행사
     * 전체에 그대로 쓴 것이다 — 1일권 세 장(5/3 · 5/4 · 5/5)과 같은 자리를 차지한다.
     *
     * @param ticketCount 이번에 사는 이 묶음권의 장수, 1 이상
     * @throws IllegalArgumentException ticketCount가 1보다 작으면
     */
    public int ticketsPerPersonCountInEvent(int ticketCount) {
        requirePositiveTicketCount(ticketCount);
        return Math.multiplyExact(ticketCount, terms.dateCount());
    }

    /**
     * 1회 선택 매수(한 번에 담는 매수)로 세는 매수 — <b>티켓 장수</b>다. 3일권 4장은 4매다(docs/05 "매수 제한 계산", docs/06 "좌석 선점", docs/99 D
     * "1회 선택 매수의 묶음권"). 한 번에 잡을 수 있는 양을 막는 장치라 날짜 수를 곱해 12매로 셀 까닭이 없다 — 친구 몫까지 한 사람이 대표로 예매하는
     * 일이 흔하다(docs/15 한서윤).
     *
     * @param ticketCount 이번에 담는 이 묶음권의 장수, 1 이상
     * @throws IllegalArgumentException ticketCount가 1보다 작으면
     */
    public int seatsPerSelectionCount(int ticketCount) {
        requirePositiveTicketCount(ticketCount);
        return ticketCount;
    }

    private static void requirePositiveTicketCount(int ticketCount) {
        if (ticketCount < 1) {
            throw new IllegalArgumentException("묶음권 장수는 1 이상이어야 합니다: " + ticketCount);
        }
    }

    private void requireApplicable(BundleProductTerms candidate, EventSchedule schedule, FloorPlan floorPlan) {
        requireSameEvent(schedule, floorPlan);
        for (long dateId : candidate.dateIds()) {
            requireDate(schedule, dateId);
        }
        for (long zoneId : candidate.zoneIds()) {
            Zone zone = floorPlan.zone(zoneId);
            if (zone.kind() != ZoneKind.QUANTITY) {
                throw new RuleViolationException(PricingErrorCode.BUNDLE_ZONE_NOT_QUANTITY,
                        "행사 " + eventId + ", 구역 " + zoneId + " — " + zone.kind());
            }
            requirePositivePrice(candidate, zone, schedule);
        }
    }

    /**
     * 할인율이 가장 싼 회차 조합에 적용해도 1원 이상 남기는지 — 할인한 가격은 날짜별 가격 합계가 작을수록 작으므로 가장 싼 조합 하나만 보면 된다.
     * 조기예매 · 쿠폰의 할인 값을 가장 낮은 가격에 견주는 것과 같다({@link Discount#requireLeavesPositivePrice}, docs/99 C "할인 값"). 정할 때 막지
     * 않으면 관객이 예매하는 순간 주최자용 문구로 막힌다. 취소되지 않은 회차를 모두 본다.
     */
    private static void requirePositivePrice(BundleProductTerms candidate, Zone zone, EventSchedule schedule) {
        cheapestDayPrices(candidate, zone, schedule, session -> !session.isCancelled())
                .ifPresent(dayPrices -> dayPrices.bundlePrice(candidate.discountRate()));
    }

    /**
     * 그 구역에서 날짜마다 가장 싼 고를 수 있는 회차의 가격 — 날짜 순서대로. 한 날짜라도 고를 회차가 없거나(지워진 날짜 포함) 비어 있다.
     */
    private static Optional<BundleDayPrices> cheapestDayPrices(BundleProductTerms candidate, Zone zone, EventSchedule schedule,
            Predicate<EventSession> selectable) {
        List<EventDate> includedDates = schedule.dates().stream().filter(eventDate -> includes(candidate, eventDate)).toList();
        if (includedDates.size() != candidate.dateCount()) {
            return Optional.empty();
        }
        List<SessionPrice> cheapestPrices = new ArrayList<>();
        for (EventDate eventDate : includedDates) {
            Optional<SessionPrice> cheapest = eventDate.sessions().stream()
                    .filter(selectable)
                    .map(session -> SessionPrice.of(zone.basePrice(), session.priceAdjustment()))
                    .min(Comparator.comparing(SessionPrice::amount));
            if (cheapest.isEmpty()) {
                return Optional.empty();
            }
            cheapestPrices.add(cheapest.get());
        }
        return Optional.of(BundleDayPrices.of(cheapestPrices));
    }

    private static boolean includes(BundleProductTerms candidate, EventDate eventDate) {
        return eventDate.id().isPresent() && candidate.dateIds().contains(eventDate.id().getAsLong());
    }

    private RuleViolationException sessionsMismatch(List<Long> chosenSessionIds) {
        return new RuleViolationException(PricingErrorCode.BUNDLE_SESSIONS_MISMATCH,
                "묶음권 " + id + ", 날짜 " + terms.dateIds() + ", 고른 회차 " + chosenSessionIds);
    }

    private void requireSameEvent(EventSchedule schedule, FloorPlan floorPlan) {
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(floorPlan, "floorPlan");
        if (schedule.eventId() != eventId || floorPlan.eventId() != eventId) {
            throw new IllegalArgumentException("다른 행사의 일정 · 도면입니다: 묶음권의 행사 " + eventId
                    + ", 일정 " + schedule.eventId() + ", 도면 " + floorPlan.eventId());
        }
    }

    private EventDate requireDate(EventSchedule schedule, long dateId) {
        return findDate(schedule, dateId).orElseThrow(
                () -> new NotFoundException(EventErrorCode.SCHEDULE_DATE_NOT_FOUND, "행사 " + eventId + ", 날짜 " + dateId));
    }

    private static Optional<EventDate> findDate(EventSchedule schedule, long dateId) {
        return schedule.dates().stream()
                .filter(eventDate -> eventDate.id().isPresent() && eventDate.id().getAsLong() == dateId)
                .findFirst();
    }
}
