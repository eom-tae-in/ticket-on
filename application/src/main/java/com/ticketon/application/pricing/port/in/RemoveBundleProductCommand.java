package com.ticketon.application.pricing.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;

/**
 * 묶음권 상품 삭제 입력.
 *
 * @param actingStaff     지우는 운영진과 주최자
 * @param eventId         행사 id
 * @param bundleProductId 묶음권 상품 id
 */
public record RemoveBundleProductCommand(ActingStaff actingStaff, long eventId, long bundleProductId) {

    public RemoveBundleProductCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
