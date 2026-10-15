package com.ticketon.domain.organizer;

import com.ticketon.common.BusinessDayCalculator;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.policy.ProposedPolicy;
import com.ticketon.domain.policy.TimePolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 주최자 심사 목표일 — 신청서를 낸 날(한국 날짜)부터 영업일 3일 안에 검토한다(docs/01 "주최자 신청과 승인", docs/99 C "주최자 심사 목표").
 * <p>
 * 제출한 날은 세지 않고 다음 영업일부터 센다({@link BusinessDayCalculator#plusBusinessDays}). 2026-10-15(목) 10:00 제출 → 16일(금) ·
 * 19일(월) · 20일(화)로 목표일은 2026-10-20(화)이다. 토·일과 공휴일은 세지 않는다 — 공휴일은 주입한 달력이 정한다.
 * <p>
 * 관리자 처리 목록은 목표일이 다가온 신청을 강조한다({@link #isApproaching}). 처리 목록은 공연이 임박한 건과 기한(영업일)이 다가오는 건을
 * 강조하는데(docs/12 "관리자 대상", docs/04 "처리 목록 — 정산 기한이 다가오는 건은 강조"), 주최자 신청에는 공연이 없어 심사 목표일을 기한으로 본다.
 *
 * @param dueDate 심사 목표일 (한국 날짜)
 */
public record OrganizerReviewDeadline(LocalDate dueDate) {

    public OrganizerReviewDeadline {
        Objects.requireNonNull(dueDate, "dueDate");
    }

    /**
     * 제출 일시로 심사 목표일을 정한다.
     *
     * @param submittedAt           신청서 제출 일시
     * @param businessDayCalculator 영업일 계산 — 공휴일 달력을 담는다
     */
    public static OrganizerReviewDeadline of(Instant submittedAt, BusinessDayCalculator businessDayCalculator) {
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(businessDayCalculator, "businessDayCalculator");
        LocalDate submittedOn = KoreanTime.toDate(submittedAt);
        return new OrganizerReviewDeadline(
                businessDayCalculator.plusBusinessDays(submittedOn, TimePolicy.ORGANIZER_REVIEW_TARGET.count()));
    }

    /**
     * 목표일이 다가왔는지 — 오늘부터 목표일까지 남은 영업일이
     * {@link ProposedPolicy#ORGANIZER_REVIEW_DEADLINE_APPROACHING_WITHIN 정해 둔 수} 이하다. 목표일 당일과 지난 뒤도 다가온 것이다.
     * 목표일이 2026-10-20(화)이면 19일(월)부터 다가왔고, 16일(금)·17일(토)은 아직이다(남은 영업일 2일).
     *
     * @param today                 오늘 (한국 날짜)
     * @param businessDayCalculator 목표일을 정할 때와 같은 영업일 계산
     */
    public boolean isApproaching(LocalDate today, BusinessDayCalculator businessDayCalculator) {
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(businessDayCalculator, "businessDayCalculator");
        if (!today.isBefore(dueDate)) {
            return true;
        }
        return businessDayCalculator.businessDaysBetween(today, dueDate)
                <= ProposedPolicy.ORGANIZER_REVIEW_DEADLINE_APPROACHING_WITHIN.count();
    }
}
