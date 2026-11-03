package com.ticketon.application.pricing.service;

import com.ticketon.application.pricing.port.in.AddEarlyBirdCommand;
import com.ticketon.application.pricing.port.in.AddEarlyBirdUseCase;
import com.ticketon.application.pricing.port.in.EarlyBirdResult;
import com.ticketon.application.pricing.port.out.EarlyBirdRepository;
import com.ticketon.application.pricing.service.PricingEditing.LockedEvent;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.pricing.EarlyBird;
import com.ticketon.domain.pricing.EarlyBirdTerms;
import com.ticketon.domain.pricing.EventPriceTable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조기예매 추가(위저드 4단계) — 수정 범위 · 진행 단계는 {@link Event#savePricingStep}이, 적용 범위가 이 행사의 것이고 한 티켓에라도 닿는지와 할인이
 * 닿는 가장 낮은 가격을 0원으로 만들지 않는지는 {@link EventPriceTable#requireEarlyBirdApplicable}이 본다. 여기서는 줄을 잠가 읽은 뒤
 * ({@link PricingEditing}) 조기예매를 넣고, 행사를 저장하고, 조작 기록을 남기고, 기간이 겹치는 다른 조기예매를 결과에 담는다(경고).
 * <p>
 * <b>한 트랜잭션이다.</b> 하나라도 막히면 아무것도 남지 않는다.
 */
@Service
public class AddEarlyBirdService implements AddEarlyBirdUseCase {

    private final PricingEditing editing;
    private final EarlyBirdRepository earlyBirdRepository;

    public AddEarlyBirdService(PricingEditing editing, EarlyBirdRepository earlyBirdRepository) {
        this.editing = editing;
        this.earlyBirdRepository = earlyBirdRepository;
    }

    /** 조작 기록의 요약 — "조기예매 추가 · 30% · 2026-04-01 00:00 ~ 2026-04-20 00:00 (4단계)". */
    static String operationSummary(EarlyBirdTerms terms) {
        return "조기예매 추가 · " + PricingResults.discountLabel(terms.discount()) + " · " + PricingResults.periodLabel(terms.period()) + " (4단계)";
    }

    @Override
    @Transactional
    public EarlyBirdResult add(AddEarlyBirdCommand command) {
        LockedEvent locked = editing.lock(command.actingStaff(), command.eventId());
        locked.event().savePricingStep(true, locked.now());
        editing.priceTable(command.eventId()).requireEarlyBirdApplicable(command.terms());

        EarlyBird added = earlyBirdRepository.add(EarlyBird.create(command.eventId(), command.terms()));
        editing.saveEvent(locked);
        editing.record(locked, operationSummary(command.terms()));
        return PricingResults.of(added, earlyBirdRepository.findByEventId(command.eventId()), locked.now());
    }
}
