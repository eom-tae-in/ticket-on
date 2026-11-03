package com.ticketon.application.pricing.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.pricing.BundleProductTerms;
import java.util.Objects;

/**
 * 묶음권 상품 수정 입력 — 설정의 칸을 모두 보낸다.
 *
 * @param actingStaff     고치는 운영진과 주최자
 * @param eventId         행사 id
 * @param bundleProductId 묶음권 상품 id
 * @param terms           새 설정
 */
public record ChangeBundleProductCommand(ActingStaff actingStaff, long eventId, long bundleProductId, BundleProductTerms terms) {

    public ChangeBundleProductCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(terms, "terms");
    }
}
