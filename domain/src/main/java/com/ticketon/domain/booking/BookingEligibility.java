package com.ticketon.domain.booking;

import com.ticketon.domain.error.RuleViolationException;
import java.util.Objects;
import java.util.Optional;

/**
 * 예매 자격 판정 결과 — 예매할 수 있거나, 막은 까닭({@link BookingRefusal})이 하나 있다. 판정은 {@link BookingEligibilityJudge}가 한다.
 * <p>
 * 화면이 미리 물어 버튼을 막거나 까닭을 보일 때는 결과를 그대로 쓰고({@link #refusal}), 예매 흐름이 다음 단계로 넘어가기 전에는 막혔으면 규칙
 * 위반으로 던진다({@link #requireEligible}). 어느 쪽이든 화면에 나가는 코드와 문구는 같다.
 */
public final class BookingEligibility {

    private static final BookingEligibility ELIGIBLE = new BookingEligibility(null);

    private final BookingRefusal refusal;

    private BookingEligibility(BookingRefusal refusal) {
        this.refusal = refusal;
    }

    /** 예매할 수 있다. */
    public static BookingEligibility eligible() {
        return ELIGIBLE;
    }

    /** 그 까닭으로 막혔다. */
    public static BookingEligibility refused(BookingRefusal refusal) {
        return new BookingEligibility(Objects.requireNonNull(refusal, "refusal"));
    }

    /** 예매할 수 있는지. */
    public boolean isEligible() {
        return refusal == null;
    }

    /** 막은 까닭. 예매할 수 있으면 비어 있다. */
    public Optional<BookingRefusal> refusal() {
        return Optional.ofNullable(refusal);
    }

    /**
     * 예매할 수 있어야 한다.
     *
     * @throws RuleViolationException 막혔으면 — 오류 코드는 막은 까닭({@link BookingRefusal})이라 화면에 그 코드와 문구가 나간다
     */
    public void requireEligible() {
        if (refusal != null) {
            throw new RuleViolationException(refusal, refusal.detail());
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof BookingEligibility that && Objects.equals(refusal, that.refusal);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(refusal);
    }

    @Override
    public String toString() {
        return refusal == null ? "BookingEligibility[eligible]" : "BookingEligibility[refused " + refusal.code() + "]";
    }
}
