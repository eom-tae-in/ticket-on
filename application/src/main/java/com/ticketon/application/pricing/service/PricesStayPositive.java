package com.ticketon.application.pricing.service;

import com.ticketon.application.event.port.out.EventScheduleRepository;
import com.ticketon.application.event.port.out.FloorPlanRepository;
import com.ticketon.application.pricing.port.out.BundleProductRepository;
import com.ticketon.application.pricing.port.out.CouponRepository;
import com.ticketon.application.pricing.port.out.EarlyBirdRepository;
import com.ticketon.domain.event.EventPricesChanged;
import com.ticketon.domain.event.SessionAdded;
import com.ticketon.domain.event.ZoneAdded;
import com.ticketon.domain.pricing.EventPriceTable;
import java.time.Clock;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사(①)의 가격 목록이 바뀌었다는 알림을 듣고 0원이 되는 가격이 없는지 점검한다({@link EventPriceTable#requirePricesStayPositive}) — 구역 기본가 ·
 * 회차 조정을 바꾸거나({@link EventPricesChanged}) 구역({@link ZoneAdded}) · 회차({@link SessionAdded})를 더한 뒤. 막히면 예외를 던져 발행한 저장이
 * 함께 되돌려진다(409).
 * <ul>
 *   <li>50,000원 구역에 −60,000원 조정을 저장하면 그 회차를 예매할 수 없다 — 관객에게 주최자용 문구가 나간다(#48에서 넘어온 할 일). 판정은
 *       가격(②)의 회차 가격이 하고, 행사(①)는 부를 수 없어 이렇게 듣는다. 같은 판정을 행사 쪽에 두 벌 두지 않는다.</li>
 *   <li>기본가 · 조정을 낮추면 98 · 99% 같은 묶음권 할인율이나 조기예매 · 쿠폰의 할인이 0원을 만들 수 있다 — 할인을 정할 때만 보면 그 뒤에 생긴다
 *       (#50에서 넘어온 할 일).</li>
 * </ul>
 * <b>발행한 유스케이스의 트랜잭션 안에서 동기로 돈다</b>(도메인 이벤트 규약, #42). 발행한 흐름이 이미 행사 줄을 FOR UPDATE로 쥐고 있어 그 행사의
 * 가격 저장과 차례로 처리된다. 저장한 뒤에 발행하므로 바뀐 도면 · 일정을 읽는다.
 */
@Component
class PricesStayPositive {

    private final FloorPlanRepository floorPlanRepository;
    private final EventScheduleRepository scheduleRepository;
    private final BundleProductRepository bundleProductRepository;
    private final EarlyBirdRepository earlyBirdRepository;
    private final CouponRepository couponRepository;
    private final Clock clock;

    PricesStayPositive(FloorPlanRepository floorPlanRepository, EventScheduleRepository scheduleRepository,
            BundleProductRepository bundleProductRepository, EarlyBirdRepository earlyBirdRepository, CouponRepository couponRepository,
            Clock clock) {
        this.floorPlanRepository = floorPlanRepository;
        this.scheduleRepository = scheduleRepository;
        this.bundleProductRepository = bundleProductRepository;
        this.earlyBirdRepository = earlyBirdRepository;
        this.couponRepository = couponRepository;
        this.clock = clock;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void pricesChanged(EventPricesChanged notice) {
        requirePositive(notice.eventId());
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void zoneAdded(ZoneAdded notice) {
        requirePositive(notice.eventId());
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void sessionAdded(SessionAdded notice) {
        requirePositive(notice.eventId());
    }

    /**
     * @throws com.ticketon.domain.error.RuleViolationException 0원 이하가 되는 회차 가격이 있거나, 묶음권 · 조기예매 · 쿠폰을 적용해 0원이 되면
     */
    private void requirePositive(long eventId) {
        EventPriceTable.of(floorPlanRepository.findByEventId(eventId), scheduleRepository.findByEventId(eventId),
                        bundleProductRepository.findByEventId(eventId))
                .requirePricesStayPositive(earlyBirdRepository.findByEventId(eventId), couponRepository.findByEventId(eventId), clock.instant());
    }
}
