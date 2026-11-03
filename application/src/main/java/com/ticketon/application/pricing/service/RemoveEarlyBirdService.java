package com.ticketon.application.pricing.service;

import com.ticketon.application.pricing.port.in.RemoveEarlyBirdCommand;
import com.ticketon.application.pricing.port.in.RemoveEarlyBirdUseCase;
import com.ticketon.application.pricing.port.out.EarlyBirdRepository;
import com.ticketon.application.pricing.service.PricingEditing.LockedEvent;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.pricing.EarlyBird;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조기예매 삭제(위저드 4단계 · 판매 중) — 수정 범위 · 진행 단계는 {@link Event#savePricingStep}이 정하고, 여기서는 줄을 잠가 읽은 뒤
 * ({@link PricingEditing}) 조기예매에 지운 시각을 적고 행사를 저장하고 조작 기록을 남긴다. 줄은 남는다 — 이미 산 티켓이 받은 할인이 그 조기예매를
 * 가리킨다.
 */
@Service
public class RemoveEarlyBirdService implements RemoveEarlyBirdUseCase {

    private final PricingEditing editing;
    private final EarlyBirdRepository earlyBirdRepository;

    public RemoveEarlyBirdService(PricingEditing editing, EarlyBirdRepository earlyBirdRepository) {
        this.editing = editing;
        this.earlyBirdRepository = earlyBirdRepository;
    }

    /** 조작 기록의 요약 — "조기예매 삭제 · 30% · 2026-04-01 00:00 ~ 2026-04-20 00:00 (4단계)". */
    static String operationSummary(EarlyBird earlyBird) {
        return "조기예매 삭제 · " + PricingResults.discountLabel(earlyBird.terms().discount()) + " · "
                + PricingResults.periodLabel(earlyBird.terms().period()) + " (4단계)";
    }

    @Override
    @Transactional
    public void remove(RemoveEarlyBirdCommand command) {
        LockedEvent locked = editing.lock(command.actingStaff(), command.eventId());
        EarlyBird earlyBird = PricingLookups.earlyBird(earlyBirdRepository.findByEventId(command.eventId()), command.eventId(),
                command.earlyBirdId());

        locked.event().savePricingStep(true, locked.now());
        earlyBirdRepository.remove(command.eventId(), command.earlyBirdId(), locked.now());
        editing.saveEvent(locked);
        editing.record(locked, operationSummary(earlyBird));
    }
}
