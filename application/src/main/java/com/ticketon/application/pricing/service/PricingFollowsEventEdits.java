package com.ticketon.application.pricing.service;

import com.ticketon.application.pricing.port.out.BundleProductRepository;
import com.ticketon.application.pricing.port.out.EarlyBirdRepository;
import com.ticketon.domain.event.EventDateMoved;
import com.ticketon.domain.event.ZoneRemoved;
import com.ticketon.domain.pricing.BundleProduct;
import com.ticketon.domain.pricing.EarlyBird;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사(①)의 일정 · 도면이 바뀌었다는 알림을 듣고 묶음권 상품 · 조기예매가 따라간다 — 행사는 가격(②)을 부를 수 없어 알리기만 하고, 가격이 듣고
 * 처리한다(.claude/rules/architecture.md "도메인 패키지와 층").
 * <ul>
 *   <li><b>날짜를 옮기면</b>({@link EventDateMoved}) 그 날짜를 묶은 묶음권이 옮겨 간 날짜를 묶는다({@link BundleProduct#followMovedDate}) —
 *       빠지면 연기한 날짜를 묶은 묶음권이 지워진 날짜를 가리켜 말없이 팔리지 않는다(docs/04 "연기된 회차도 계속 팝니다").</li>
 *   <li><b>구역을 지우면</b>({@link ZoneRemoved}) 묶음권의 대상 구역에서 빼고({@link BundleProduct#dropRemovedZone} — 하나뿐인 구역이면 남겨
 *       두어 그 묶음권의 판매가 멈춘다, 확인 필요 #223), 조기예매가 그 구역을 꺼 둔 기록을 지운다({@link EarlyBird#dropRemovedZone}). 구역 삭제는
 *       막지 않는다 — 막는 기준은 티켓이 걸렸는지 하나다(docs/04, docs/99 D "수정 제한의 기준").</li>
 *   <li>확인 필요 (#223) — <b>날짜를 지우면</b>(옮긴 것이 아니라 회차 · 날짜 삭제) 그 날짜를 묶은 묶음권은 그대로 둔다. 지운 날짜를 가리켜 고를 회차가
 *       없으므로 판매가 멈추고(docs/05 "한 날짜라도 들어갈 회차가 없으면 그 묶음권은 판매가 멈춥니다"), 주최자가 날짜를 다시 고른다.</li>
 * </ul>
 * <b>발행한 유스케이스의 트랜잭션 안에서 동기로 돈다</b>(도메인 이벤트 규약, #42) — 함께 커밋되거나 함께 되돌려진다. 발행한 흐름이 이미 행사 줄을
 * FOR UPDATE로 쥐고 있어 가격 줄을 따로 잠그지 않는다. 바뀐 것이 없으면 쓰지 않는다.
 */
@Component
class PricingFollowsEventEdits {

    private final BundleProductRepository bundleProductRepository;
    private final EarlyBirdRepository earlyBirdRepository;

    PricingFollowsEventEdits(BundleProductRepository bundleProductRepository, EarlyBirdRepository earlyBirdRepository) {
        this.bundleProductRepository = bundleProductRepository;
        this.earlyBirdRepository = earlyBirdRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void dateMoved(EventDateMoved notice) {
        for (BundleProduct bundleProduct : bundleProductRepository.findByEventId(notice.eventId())) {
            if (bundleProduct.followMovedDate(notice.retiredDateId(), notice.movedToDateId())) {
                bundleProductRepository.update(bundleProduct);
            }
        }
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void zoneRemoved(ZoneRemoved notice) {
        for (BundleProduct bundleProduct : bundleProductRepository.findByEventId(notice.eventId())) {
            if (bundleProduct.dropRemovedZone(notice.zoneId())) {
                bundleProductRepository.update(bundleProduct);
            }
        }
        for (EarlyBird earlyBird : earlyBirdRepository.findByEventId(notice.eventId()).all()) {
            if (earlyBird.dropRemovedZone(notice.zoneId())) {
                earlyBirdRepository.update(earlyBird);
            }
        }
    }
}
