package com.ticketon.domain.pricing;

import com.ticketon.domain.error.RuleViolationException;
import java.util.Objects;

/**
 * 쿠폰 설정 — 코드를 뺀, 주최자가 위저드 4단계에서 정하는 항목(docs/05 "쿠폰").
 * <table>
 *   <caption>쿠폰 항목 (docs/05)</caption>
 *   <tr><th>항목</th><th></th></tr>
 *   <tr><td>할인 방식</td><td>정액(5,000원) 또는 정률(20%)</td></tr>
 *   <tr><td>전체 사용 가능 횟수</td><td>선착순 N회</td></tr>
 *   <tr><td>1인 1회 제한</td><td>켜고 끔</td></tr>
 *   <tr><td>유효 기간</td><td>시작~종료</td></tr>
 * </table>
 * <b>묶음권에도 쓸 수 있다</b> — 상품별로 끄는 칸이 없다. 한 번 쓰면 예매 건 하나에 붙고, 티켓이 몇 장이든 사용 횟수 1회다(4매에 쓴 쿠폰, docs/05
 * "취소하면 쿠폰은 돌아옵니다").
 *
 * @param discount     할인 — 정액 또는 정률. 값의 범위({@link Discount#requireOfferable})를 지켜야 한다
 * @param usageLimit   전체 사용 가능 횟수 — 선착순 N회, 1 이상. 확인 필요 (#49) — 문서 표에 "선택"이 없어 꼭 받는다(제한 없는 쿠폰은 두지 않음)
 * @param onePerPerson 1인 1회 제한을 켰는지
 * @param validity     유효 기간
 */
public record CouponTerms(Discount discount, int usageLimit, boolean onePerPerson, DiscountPeriod validity) {

    /**
     * @throws RuleViolationException   할인 값이 범위 밖이면({@link PricingErrorCode#DISCOUNT_OUT_OF_RANGE})
     * @throws IllegalArgumentException 전체 사용 가능 횟수가 1보다 작으면
     */
    public CouponTerms {
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(validity, "validity");
        if (usageLimit < 1) {
            throw new IllegalArgumentException("쿠폰 전체 사용 가능 횟수는 1 이상이어야 합니다: " + usageLimit);
        }
        discount.requireOfferable();
    }
}
