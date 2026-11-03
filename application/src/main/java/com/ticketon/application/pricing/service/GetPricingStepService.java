package com.ticketon.application.pricing.service;

import com.ticketon.application.event.port.in.SessionPriceAdjustmentResult;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.event.port.out.EventScheduleRepository;
import com.ticketon.application.event.port.out.FloorPlanRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.pricing.port.in.GetPricingStepQuery;
import com.ticketon.application.pricing.port.in.GetPricingStepUseCase;
import com.ticketon.application.pricing.port.in.PricingStepResult;
import com.ticketon.application.pricing.port.in.PricingStepResult.BundleDiscountDefault;
import com.ticketon.application.pricing.port.in.PricingStepResult.SessionPrices;
import com.ticketon.application.pricing.port.in.PricingStepResult.ZonePrice;
import com.ticketon.application.pricing.port.in.PricingStepResult.ZoneSessionPrice;
import com.ticketon.application.pricing.port.out.BundleProductRepository;
import com.ticketon.application.pricing.port.out.CouponRepository;
import com.ticketon.application.pricing.port.out.EarlyBirdRepository;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.event.EventDate;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventSession;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.TimedSessionTime;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.policy.Percent;
import com.ticketon.domain.pricing.BundleProduct;
import com.ticketon.domain.pricing.EarlyBirds;
import com.ticketon.domain.pricing.EventPriceTable;
import com.ticketon.domain.pricing.Money;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 위저드 4단계 조회 — 고칠 수 있는 사람인지({@link StaffAction#EDIT_EVENT}, 운영진 누구나) 확인한 뒤 그 주최자의 행사인지 보고, 도면 · 일정 · 묶음권
 * 상품 · 조기예매 · 쿠폰을 읽어 가격표({@link EventPriceTable})로 회차 가격과 묶음권 시작 가격을 낸다. 다른 주최자의 행사는 없는 것으로 다룬다. 보기만
 * 하므로 잠그지 않고 조작 기록을 남기지 않는다.
 * <p>
 * <b>스냅숏 하나로 읽는다(REPEATABLE READ).</b> 행사 줄을 잠그지 않고 서로 가리키는 여러 표(도면 · 일정 · 묶음권 · 조기예매 · 쿠폰)를 여러 문장으로
 * 읽는다 — 문장마다 새 스냅숏을 잡으면 그 사이에 커밋된 편집(구역을 지우며 묶음권의 구역을 뺀 것 등)을 반쯤만 읽어 가격표를 만들지 못한다. 이유는
 * 도면 조회(event 서비스의 GetEventFloorPlanService)와 같다.
 */
@Service
public class GetPricingStepService implements GetPricingStepUseCase {

    private static final int FIRST_BUNDLE_DATE_COUNT = 2;

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final EventRepository eventRepository;
    private final EventScheduleRepository scheduleRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final BundleProductRepository bundleProductRepository;
    private final EarlyBirdRepository earlyBirdRepository;
    private final CouponRepository couponRepository;
    private final Clock clock;

    public GetPricingStepService(CheckStaffPermissionUseCase checkStaffPermissionUseCase, EventRepository eventRepository,
            EventScheduleRepository scheduleRepository, FloorPlanRepository floorPlanRepository,
            BundleProductRepository bundleProductRepository, EarlyBirdRepository earlyBirdRepository, CouponRepository couponRepository,
            Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.eventRepository = eventRepository;
        this.scheduleRepository = scheduleRepository;
        this.floorPlanRepository = floorPlanRepository;
        this.bundleProductRepository = bundleProductRepository;
        this.earlyBirdRepository = earlyBirdRepository;
        this.couponRepository = couponRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PricingStepResult get(GetPricingStepQuery query) {
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(query.actingStaff(), StaffAction.EDIT_EVENT));
        long eventId = query.eventId();
        eventRepository.findById(eventId)
                .filter(event -> permission.covers(event.organizerId()))
                .orElseThrow(() -> new NotFoundException(EventErrorCode.EVENT_NOT_FOUND, "행사 " + eventId + ", 주최자 " + permission.organizerId()));
        Instant now = clock.instant();
        FloorPlan floorPlan = floorPlanRepository.findByEventId(eventId);
        Optional<EventSchedule> schedule = scheduleRepository.findByEventId(eventId);
        List<BundleProduct> bundleProducts = bundleProductRepository.findByEventId(eventId);
        EventPriceTable priceTable = EventPriceTable.of(floorPlan, schedule, bundleProducts);
        EarlyBirds earlyBirds = earlyBirdRepository.findByEventId(eventId);

        return new PricingStepResult(eventId,
                floorPlan.zones().stream()
                        .map(zone -> new ZonePrice(zone.id().orElseThrow(), zone.kind(), zone.name().value(), zone.basePrice().won()))
                        .toList(),
                schedule.map(found -> sessionPrices(found, priceTable)).orElse(List.of()),
                earlyBirds.all().stream().map(earlyBird -> PricingResults.of(earlyBird, earlyBirds, now)).toList(),
                couponRepository.findByEventId(eventId).stream().map(PricingResults::of).toList(),
                bundleProducts.stream().map(product -> PricingResults.of(product, priceTable)).toList(),
                bundleDiscountDefaults());
    }

    /** 취소되지 않은 회차마다 구역별 회차 가격 — 일정 순서. */
    private static List<SessionPrices> sessionPrices(EventSchedule schedule, EventPriceTable priceTable) {
        Map<Long, List<ZoneSessionPrice>> pricesBySession = priceTable.sessionPrices().stream()
                .collect(Collectors.groupingBy(EventPriceTable.SessionZonePrice::sessionId,
                        Collectors.mapping(price -> new ZoneSessionPrice(price.zoneId(), price.price().map(Money::won).orElse(null)),
                                Collectors.toList())));
        List<SessionPrices> sessions = new ArrayList<>();
        for (EventDate eventDate : schedule.dates()) {
            for (EventSession session : eventDate.sessions()) {
                if (session.isCancelled()) {
                    continue;
                }
                long sessionId = session.id().orElseThrow();
                sessions.add(new SessionPrices(sessionId, eventDate.id().orElseThrow(), eventDate.date(),
                        session.time().map(TimedSessionTime::startTime).orElse(null),
                        SessionPriceAdjustmentResult.of(session.priceAdjustment()), pricesBySession.getOrDefault(sessionId, List.of())));
            }
        }
        return sessions;
    }

    /** 날짜 수마다의 묶음권 할인율 기본값 — 2일부터 기본값이 있는 데까지(2일 3% · 3일 7% · 4일 10%). */
    private static List<BundleDiscountDefault> bundleDiscountDefaults() {
        List<BundleDiscountDefault> defaults = new ArrayList<>();
        for (int dateCount = FIRST_BUNDLE_DATE_COUNT; ; dateCount++) {
            Optional<Percent> rate = BundleProduct.defaultDiscountRate(dateCount);
            if (rate.isEmpty()) {
                return defaults;
            }
            defaults.add(new BundleDiscountDefault(dateCount, rate.get().value()));
        }
    }
}
