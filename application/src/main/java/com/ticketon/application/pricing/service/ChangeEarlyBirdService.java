package com.ticketon.application.pricing.service;

import com.ticketon.application.pricing.port.in.ChangeEarlyBirdCommand;
import com.ticketon.application.pricing.port.in.ChangeEarlyBirdUseCase;
import com.ticketon.application.pricing.port.in.EarlyBirdResult;
import com.ticketon.application.pricing.port.out.EarlyBirdRepository;
import com.ticketon.application.pricing.service.PricingEditing.LockedEvent;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.pricing.EarlyBird;
import com.ticketon.domain.pricing.EarlyBirdTerms;
import com.ticketon.domain.pricing.EarlyBirds;
import com.ticketon.domain.pricing.EventPriceTable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조기예매 수정(위저드 4단계 · 판매 중) — 끝난 조기예매를 바꾸지 않고 수량을 사용 수 아래로 줄이지 않는 것, 같은 값이면 바꾸지 않는 것, 임박 단계를
 * 내리지 않는 것은 {@link EarlyBird#reconfigure}가, 수정 범위 · 진행 단계는 {@link Event#savePricingStep}이, 범위 · 0원은
 * {@link EventPriceTable#requireEarlyBirdApplicable}이 본다. 여기서는 줄을 잠가 읽은 뒤({@link PricingEditing}) 바뀌었으면 조기예매 · 행사를
 * 저장하고 조작 기록을 남긴다. 사용 수는 쓰지 않는다 — 선점이 한 문장으로 늘린다(#54).
 * <p>
 * <b>한 트랜잭션이다.</b> 같은 값이면(자동 임시저장을 여러 번 해도) 저장도 기록도 하지 않는다.
 */
@Service
public class ChangeEarlyBirdService implements ChangeEarlyBirdUseCase {

    private final PricingEditing editing;
    private final EarlyBirdRepository earlyBirdRepository;

    public ChangeEarlyBirdService(PricingEditing editing, EarlyBirdRepository earlyBirdRepository) {
        this.editing = editing;
        this.earlyBirdRepository = earlyBirdRepository;
    }

    /** 조작 기록의 요약 — 고친 뒤의 값으로 "조기예매 수정 · 20% · 2026-04-01 00:00 ~ 2026-04-25 00:00 (4단계)". */
    static String operationSummary(EarlyBirdTerms terms) {
        return "조기예매 수정 · " + PricingResults.discountLabel(terms.discount()) + " · " + PricingResults.periodLabel(terms.period()) + " (4단계)";
    }

    @Override
    @Transactional
    public EarlyBirdResult change(ChangeEarlyBirdCommand command) {
        LockedEvent locked = editing.lock(command.actingStaff(), command.eventId());
        EarlyBirds earlyBirds = earlyBirdRepository.findByEventId(command.eventId());
        EarlyBird earlyBird = PricingLookups.earlyBird(earlyBirds, command.eventId(), command.earlyBirdId());

        boolean changed = earlyBird.reconfigure(command.terms(), locked.now());
        if (changed) {
            editing.priceTable(command.eventId()).requireEarlyBirdApplicable(command.terms());
        }
        if (locked.event().savePricingStep(changed, locked.now())) {
            editing.saveEvent(locked);
        }
        if (changed) {
            earlyBirdRepository.update(earlyBird);
            editing.record(locked, operationSummary(command.terms()));
        }
        return PricingResults.of(earlyBird, earlyBirds, locked.now());
    }
}
