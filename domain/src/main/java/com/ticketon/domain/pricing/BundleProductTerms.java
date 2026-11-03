package com.ticketon.domain.pricing;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.policy.Percent;
import java.util.Objects;
import java.util.Set;

/**
 * 묶음권 상품 설정 — 주최자가 위저드 4단계에서 정하는 것(docs/03 "행사 만들기 위저드", docs/05 "조합은 자동 생성하지 않습니다").
 * <blockquote>
 * 묶음권 만들기<br>
 * 이름: 3일 통권<br>
 * 포함 날짜: ☑ 5/3 ☑ 5/4 ☑ 5/5<br>
 * 할인율: 7%
 * </blockquote>
 * <b>주최자는 날짜만 고른다.</b> 날짜 아래 회차가 여럿이면 어느 회차로 들어갈지는 관객이 예매할 때 고른다(docs/14 "묶음권 상품은 날짜를, 티켓은
 * 회차를 가리킵니다"). 조합은 자동으로 만들지 않는다 — 3일 행사의 조합은 7가지, 4일이면 15가지라 주최자가 필요한 것만 정한다(docs/99 B "묶음권
 * 조합 자동 생성"). 할인율의 기본값은 {@link BundleProduct#defaultDiscountRate}가 채우고 주최자가 바꾼다.
 * <p>
 * 날짜 · 구역이 그 행사의 것인지, 구역이 수량형인지는 일정 · 도면을 보는 묶음권 상품이 본다({@link BundleProduct#create}).
 *
 * @param name         이름
 * @param dateIds      포함 날짜 — 행사 일정의 날짜 id, 2개 이상
 * @param zoneIds      대상 구역 — 도면의 구역 id, 하나 이상. 수량형만이다. 확인 필요 (#223): 문서의 "묶음권 만들기"에는 구역 칸이 없고
 *                     "묶음권은 수량형 구역에서만 팝니다"(docs/02)만 있다. 상품마다 팔 구역을 고르게 했다(#50)
 * @param discountRate 할인율 — 100% 미만. 0%는 할인 없이 묶어 판다
 */
public record BundleProductTerms(BundleProductName name, Set<Long> dateIds, Set<Long> zoneIds, Percent discountRate) {

    /** 묶음권이 묶는 날짜의 최소 수 — 날짜 하나짜리는 묶음권이 아니라 1일권이다(docs/05 "묶음권 = 날짜 N개를 묶은 상품"). */
    static final int MINIMUM_DATE_COUNT = 2;

    private static final int WHOLE_PERCENT = 100;

    /**
     * @throws RuleViolationException 날짜가 2개보다 적거나({@link PricingErrorCode#BUNDLE_DATES_TOO_FEW}), 구역이 없거나
     *                                ({@link PricingErrorCode#BUNDLE_ZONES_EMPTY}), 할인율이 100%면({@link PricingErrorCode#BUNDLE_PRICE_NOT_POSITIVE}
     *                                — 어느 가격이든 0원이다)
     * @throws IllegalArgumentException id가 1보다 작으면
     */
    public BundleProductTerms {
        Objects.requireNonNull(name, "name");
        dateIds = Set.copyOf(Objects.requireNonNull(dateIds, "dateIds"));
        zoneIds = Set.copyOf(Objects.requireNonNull(zoneIds, "zoneIds"));
        Objects.requireNonNull(discountRate, "discountRate");
        requirePositiveIds(dateIds, "날짜");
        requirePositiveIds(zoneIds, "구역");
        if (dateIds.size() < MINIMUM_DATE_COUNT) {
            throw new RuleViolationException(PricingErrorCode.BUNDLE_DATES_TOO_FEW, "날짜 " + dateIds.size() + "개");
        }
        if (zoneIds.isEmpty()) {
            throw new RuleViolationException(PricingErrorCode.BUNDLE_ZONES_EMPTY, "묶음권 " + name.value());
        }
        if (discountRate.value() >= WHOLE_PERCENT) {
            throw new RuleViolationException(PricingErrorCode.BUNDLE_PRICE_NOT_POSITIVE, "할인율 " + discountRate);
        }
    }

    /** 묶는 날짜의 수 — 3일권이면 3. */
    public int dateCount() {
        return dateIds.size();
    }

    private static void requirePositiveIds(Set<Long> ids, String kind) {
        for (long id : ids) {
            if (id < 1) {
                throw new IllegalArgumentException(kind + " id는 1 이상이어야 합니다: " + id);
            }
        }
    }
}
