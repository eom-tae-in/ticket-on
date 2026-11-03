package com.ticketon.application.pricing.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.pricing.BundleProductTerms;
import java.util.Objects;

/**
 * 묶음권 상품 추가 입력 — 위저드 4단계(docs/05 "묶음권 만들기 — 이름 · 포함 날짜 · 할인율").
 *
 * @param actingStaff 더하는 운영진과 주최자
 * @param eventId     행사 id
 * @param terms       설정 — 이름 · 묶는 날짜 · 파는 구역 · 할인율
 */
public record AddBundleProductCommand(ActingStaff actingStaff, long eventId, BundleProductTerms terms) {

    public AddBundleProductCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(terms, "terms");
    }
}
