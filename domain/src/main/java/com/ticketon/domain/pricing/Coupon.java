package com.ticketon.domain.pricing;

import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 쿠폰 — <b>주최자가 자기 행사에 쓸 코드를 발급</b>한다(docs/05 "쿠폰"). 행사를 가리킨다(docs/14 "조기예매 · 쿠폰 → 행사"). 플랫폼 전체 쿠폰은
 * 만들지 않는다(docs/99 B "플랫폼 전체 쿠폰").
 * <ul>
 *   <li><b>코드</b>({@link CouponCode}) — 발급한 뒤 바꾸지 않는다. 확인 필요 (#49) — 이미 나눠 준 코드가 바뀌면 받은 사람이 쓸 수 없다. 잘못
 *       만들었으면 지우고 새로 발급한다.</li>
 *   <li><b>설정</b>({@link CouponTerms}) — 할인(정액 · 정률), 전체 사용 가능 횟수(선착순 N회), 1인 1회 켜고 끔, 유효 기간. 묶음권에도 쓸 수
 *       있다.</li>
 *   <li><b>사용 수</b>({@link #usedCount}) — 쿠폰을 쓴 예매 건 수. 티켓이 몇 장이든 예매 건 하나에 1회다.</li>
 * </ul>
 *
 * <h2>쓰는 순서</h2>
 * <ol>
 *   <li>관객이 적은 코드로 찾은 쿠폰이 <b>이 행사에서 지금 쓸 수 있는 코드인지</b> 본다({@link #requireUsableIn}) — 없는 코드, 다른 행사의 코드,
 *       유효 기간 밖이면 모두 "이 행사에서 사용할 수 없는 코드입니다". 구분하면 코드를 넣어 보며 다른 행사의 쿠폰이 있는지 알아낼 수 있다(docs/05
 *       "잘못된 코드").</li>
 *   <li>남은 횟수와 1인 1회를 본다({@link #checkAvailableTo}).</li>
 *   <li>할인을 가격 계산({@link BookingPrice#calculate})에 넘겨 티켓마다 조기예매와 견준다. <b>어느 티켓도 쿠폰을 고르지 않았으면 쓴 것이
 *       아니다</b>({@link BookingPrice#usesCoupon}) — 횟수를 차지하지 않는다.</li>
 *   <li>한 장이라도 골랐으면 쓴다({@link #use}) — 사용 수 + 1. 동시에 쓰는 사람이 여럿이면 저장소가 "사용 수 + 1 ≤ 전체 횟수"일 때만 늘린다(#51,
 *       #54).</li>
 * </ol>
 *
 * <h2>돌려줄 때</h2>
 * <b>전체 취소일 때만 돌려준다</b>({@link #returnAfterCancellation}) — 조기예매 몫은 선착순으로 다투는 자원이라 영구 마감이지만, 쿠폰은 특정 개인에게
 * 준 권리라 취소했다고 잃으면 억울하다. <b>유효 기간은 그대로</b>다 — 기간이 지났으면 돌아와도 쓸 수 없다. 부분 취소는 남은 티켓이 아직 쓰고 있어
 * 돌려주지 않고, 변경은 예매가 이어져 쿠폰이 따라간다({@link TicketCancellationKind}). 결제가 끝나지 않은 예매(결제 실패 · 만료)는 쓰지 않은 것으로
 * 되돌린다({@link #returnUnpaidUse}, #73).
 * <p>
 * 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 쿠폰은 id가 없다.
 */
public final class Coupon {

    private final Long id;
    private final long eventId;
    private final CouponCode code;
    private CouponTerms terms;
    private int usedCount;

    private Coupon(Long id, long eventId, CouponCode code, CouponTerms terms, int usedCount) {
        if (eventId < 1) {
            throw new IllegalArgumentException("행사 id는 1 이상이어야 합니다: " + eventId);
        }
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(terms, "terms");
        if (usedCount < 0 || usedCount > terms.usageLimit()) {
            throw new IllegalArgumentException("쿠폰 사용 수가 범위 밖입니다: " + usedCount + " / " + terms.usageLimit());
        }
        this.id = id;
        this.eventId = eventId;
        this.code = code;
        this.terms = terms;
        this.usedCount = usedCount;
    }

    /** 새 쿠폰을 발급한다 — 아직 쓴 사람이 없다. */
    public static Coupon issue(long eventId, CouponCode code, CouponTerms terms) {
        return new Coupon(null, eventId, code, terms, 0);
    }

    /**
     * 저장소에서 읽은 값으로 쿠폰을 되살린다.
     *
     * @throws IllegalArgumentException 사용 수가 음수거나 전체 사용 가능 횟수보다 많으면
     */
    public static Coupon restore(long id, long eventId, CouponCode code, CouponTerms terms, int usedCount) {
        if (id < 1) {
            throw new IllegalArgumentException("쿠폰 id는 1 이상이어야 합니다: " + id);
        }
        return new Coupon(id, eventId, code, terms, usedCount);
    }

    /**
     * 관객이 적은 코드로 찾은 쿠폰이 이 행사에서 지금 쓸 수 있는 코드인지 본다(위 "쓰는 순서" 1). 없는 코드, 다른 행사의 코드, 유효 기간 밖(시작
     * 전 · 끝난 뒤)을 <b>구분하지 않는다</b>.
     *
     * @param eventId 관객이 예매하는 행사
     * @param found   적은 코드로 찾은 쿠폰. 코드가 될 수 없는 값이거나 찾지 못했으면 비어 있다
     * @return 쓸 수 있는 코드면 그 쿠폰
     * @throws RuleViolationException 쓸 수 없는 코드면({@link PricingErrorCode#COUPON_NOT_USABLE_FOR_EVENT} — "이 행사에서 사용할 수 없는 코드입니다")
     */
    public static Coupon requireUsableIn(long eventId, Optional<Coupon> found, Instant now) {
        Objects.requireNonNull(found, "found");
        Objects.requireNonNull(now, "now");
        return found
                .filter(coupon -> coupon.eventId == eventId)
                .filter(coupon -> coupon.terms.validity().contains(now))
                .orElseThrow(() -> new RuleViolationException(PricingErrorCode.COUPON_NOT_USABLE_FOR_EVENT, "행사 " + eventId));
    }

    /**
     * 남은 횟수와 1인 1회를 본다(위 "쓰는 순서" 2). 이 행사의 코드라는 것을 확인한 뒤라 문구를 나눠도 다른 행사의 쿠폰은 드러나지 않는다.
     * 확인 필요 (#49) — 두 경우의 문구는 문서에 없다.
     *
     * @param alreadyUsedByAccount 이 관객이 이 쿠폰을 쓴 예매가 아직 살아 있는지. 전체 취소로 돌려받았거나 결제가 끝나지 않아 되돌린 사용은 세지
     *                             않는다 — 돌려받은 권리는 다시 쓸 수 있다
     * @throws RuleViolationException 전체 사용 가능 횟수를 다 썼으면({@link PricingErrorCode#COUPON_USES_EXHAUSTED}), 1인 1회인데 이미 썼으면
     *                                ({@link PricingErrorCode#COUPON_ALREADY_USED})
     */
    public void checkAvailableTo(boolean alreadyUsedByAccount) {
        if (usedCount >= terms.usageLimit()) {
            throw new RuleViolationException(PricingErrorCode.COUPON_USES_EXHAUSTED, "쿠폰 " + id + " — " + usedCount + " / " + terms.usageLimit());
        }
        if (terms.onePerPerson() && alreadyUsedByAccount) {
            throw new RuleViolationException(PricingErrorCode.COUPON_ALREADY_USED, "쿠폰 " + id);
        }
    }

    /**
     * 쿠폰을 쓴다 — 사용 수 + 1(위 "쓰는 순서" 4). 가격 계산에서 한 장이라도 쿠폰을 골랐을 때만 부른다. 쓰기 직전에 코드 · 횟수 · 1인 1회를 다시
     * 본다.
     *
     * @throws RuleViolationException {@link #requireUsableIn} · {@link #checkAvailableTo}와 같다
     */
    public void use(long eventId, Instant now, boolean alreadyUsedByAccount) {
        requireUsableIn(eventId, Optional.of(this), now);
        checkAvailableTo(alreadyUsedByAccount);
        usedCount++;
    }

    /**
     * 이 쿠폰을 쓴 예매 건의 티켓을 취소한 뒤 — 전체 취소면 쓴 횟수 하나를 돌려준다. 유효 기간은 그대로다. 부분 취소 · 변경이면 그대로 둔다.
     *
     * @return 돌려줬는지
     */
    public boolean returnAfterCancellation(TicketCancellationKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (!kind.returnsCoupon()) {
            return false;
        }
        giveBackOneUse();
        return true;
    }

    /**
     * 결제가 끝나지 않은 예매(결제 실패 · 결제 전 만료)의 쿠폰 사용을 되돌린다 — 예매가 성립하지 않았으니 쓴 것이 아니다(#73 "결제 실패 · 만료로
     * 끝나면 되돌림").
     */
    public void returnUnpaidUse() {
        giveBackOneUse();
    }

    /**
     * 설정을 바꾼다. 판매 중에도 바꿀 수 있고 새로 쓰는 예매부터 적용된다 — 이미 쓴 티켓의 할인은 티켓이 직접 갖는다(docs/14 "받은 할인").
     * 전체 사용 가능 횟수는 이미 쓴 횟수 아래로 줄일 수 없다. 확인 필요 (#49).
     *
     * @return 바뀌었는지. 같은 설정이면 false
     * @throws RuleViolationException 전체 사용 가능 횟수가 사용 수보다 적으면({@link PricingErrorCode#COUPON_USAGE_LIMIT_BELOW_USED})
     */
    public boolean reconfigure(CouponTerms newTerms) {
        Objects.requireNonNull(newTerms, "newTerms");
        if (newTerms.equals(terms)) {
            return false;
        }
        if (newTerms.usageLimit() < usedCount) {
            throw new RuleViolationException(PricingErrorCode.COUPON_USAGE_LIMIT_BELOW_USED,
                    "쿠폰 " + id + " — 전체 횟수 " + newTerms.usageLimit() + " < 사용 수 " + usedCount);
        }
        terms = newTerms;
        return true;
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public long eventId() {
        return eventId;
    }

    public CouponCode code() {
        return code;
    }

    public CouponTerms terms() {
        return terms;
    }

    /** 가격 계산에 넘길 할인({@link BookingPrice#calculate}). */
    public Discount discount() {
        return terms.discount();
    }

    /** 사용 수 — 쿠폰을 쓴 예매 건 수. 저장과 주최자의 쿠폰 사용 현황(docs/11 "예매 현황")에 쓴다. */
    public int usedCount() {
        return usedCount;
    }

    private void giveBackOneUse() {
        if (usedCount == 0) {
            throw new IllegalStateException("돌려줄 쿠폰 사용이 없습니다: 쿠폰 " + id);
        }
        usedCount--;
    }
}
