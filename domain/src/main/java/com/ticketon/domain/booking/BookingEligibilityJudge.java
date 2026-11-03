package com.ticketon.domain.booking;

import com.ticketon.common.AgeCalculator;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.error.ErrorCode;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.AgeRestriction;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventSession;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.ScreenSide;
import com.ticketon.domain.event.SessionStatus;
import com.ticketon.domain.pricing.BundleProduct;
import com.ticketon.domain.pricing.PricingErrorCode;
import com.ticketon.domain.ticket.Ticket;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 예매 자격 판정 — 이 관객이 이 행사를 지금 예매할 수 있는지(docs/06 "전체 흐름"). 막으면 까닭과 문구를 돌려준다({@link BookingEligibility}).
 * <p>
 * <b>예매 흐름의 단계마다 그 단계에서 알 수 있는 것을 본다.</b> 좌석을 잡은 뒤에 막으면 타이머와 선점이 헛돌기 때문이다. 뒤 단계는 앞 단계의
 * 판정을 다시 한다 — 회차를 고르는 사이 판매가 멈췄을 수 있다.
 * <table>
 *   <caption>단계별 판정</caption>
 *   <tr><th>단계</th><th>메서드</th><th>보는 것</th></tr>
 *   <tr><td>[예매하기]</td><td>{@link #atStart}</td><td>미리보기 아님 · 판매 중 · 이용 정지 아님</td></tr>
 *   <tr><td>회차 선택 (또는 날짜 · 묶음권)</td><td>{@link #atSessionChoice}</td><td>+ 회차 취소 안 됨 · 그 회차 예매 종료 전 · 연령 제한 ·
 *       회차 선택 제약</td></tr>
 * </table>
 * <p>
 * <b>판매 중</b>은 저장된 행사 상태가 아니라 지금 시각을 반영한 상태로 본다({@link Event#statusAt}) — 오픈 시각이 지났는데 상태 전환 작업이 아직
 * 돌지 않았어도 예매가 열려 있고, 마지막 회차의 예매 종료 시각이 지났으면 판매 중단 중이어도 판매 종료다(docs/04 "시간이 운영 판단을 이깁니다").
 * <p>
 * <b>예매 설정(연령 제한 · 회차 선택 제약 · 매수 제한)은 지금 값</b>으로 본다 — 판매 중에 바꾸면 신규 예매부터 적용되고 이미 산 표는 그대로다
 * (docs/04 "예매가 열린 뒤(판매 중) 수정 범위"). 이미 가진 티켓을 세는 법은 {@link HeldBookingTickets}가 정한다.
 * <p>
 * 현재 시각은 인자로 받는다.
 */
public final class BookingEligibilityJudge {

    private static final char HANGUL_SYLLABLE_FIRST = '가';
    private static final char HANGUL_SYLLABLE_LAST = '힣';

    /** 한글 음절 하나가 갖는 받침의 가짓수(받침 없음 포함) — 음절 번호를 이것으로 나눈 나머지가 0이면 받침이 없다. */
    private static final int HANGUL_FINAL_CONSONANT_COUNT = 28;

    private final Event event;
    private final EventSchedule schedule;
    private final AudienceAccount audience;
    private final HeldBookingTickets heldTickets;
    private final BookingAccess access;
    private final BookingPurpose purpose;

    private BookingEligibilityJudge(Event event, EventSchedule schedule, AudienceAccount audience, List<Ticket> heldTickets,
            BookingAccess access, BookingPurpose purpose) {
        this.event = Objects.requireNonNull(event, "event");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.access = Objects.requireNonNull(access, "access");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        long eventId = event.id().orElseThrow(() -> new IllegalArgumentException("저장 전 행사는 예매할 수 없습니다"));
        if (schedule.eventId() != eventId) {
            throw new IllegalArgumentException("행사 " + eventId + "에 다른 행사(" + schedule.eventId() + ")의 일정을 넘겼습니다");
        }
        this.heldTickets = HeldBookingTickets.of(heldTickets, schedule);
    }

    /**
     * 판정을 준비한다.
     *
     * @param event       예매하려는 행사
     * @param schedule    그 행사의 일정
     * @param audience    예매하려는 관객의 계정
     * @param heldTickets 그 관객이 이 행사에서 이미 가진 예매 티켓 — 그 계정의 예매 건에 딸린 티켓. 취소된 티켓 · 초대권 티켓이 섞여 있어도 판정이
     *                    걸러 낸다. 변경이면 바꿀 티켓은 빼고 넘긴다({@link BookingPurpose#CHANGE})
     * @param access      관객 화면을 어떻게 열었는지 — 미리보기 링크면 막는다
     * @param purpose     새 예매인지 변경인지
     * @throws IllegalArgumentException 저장 전 행사이거나, 다른 행사의 일정 · 티켓이면
     */
    public static BookingEligibilityJudge of(Event event, EventSchedule schedule, AudienceAccount audience, List<Ticket> heldTickets,
            BookingAccess access, BookingPurpose purpose) {
        return new BookingEligibilityJudge(event, schedule, audience, heldTickets, access, purpose);
    }

    /**
     * [예매하기]를 누른 순간의 판정 — 아래 차례로 보고 처음 걸린 까닭으로 막는다.
     * <ol>
     *   <li><b>미리보기 아님</b> — 미리보기 링크로는 예매가 되지 않는다(docs/03).</li>
     *   <li><b>판매 중</b> — 지금 시각의 행사 상태가 판매 중이어야 한다. 작성 중 · 판매 대기는 아직 열리지 않았고, 판매 중단은 신규 판매만 멈췄고(변경이면
     *       docs/04의 변경 문구), 판매 종료 · 행사 종료 · 정산 완료는 마감됐고, 취소됨은 취소됐다(docs/04 "행사 상태").</li>
     *   <li><b>이용 정지 아님</b> — 이용 정지된 계정은 [예매하기]에서 막힌다(docs/06 "전체 흐름", docs/01 "관객 이용 정지"). 변경도 막힌다. 산 표는
     *       뺏지 않으므로 기존 티켓 보기 · QR · 취소는 따로 된다. 인증 대기 · 탈퇴 계정은 활동 중이 아니라 막는다.</li>
     * </ol>
     */
    public BookingEligibility atStart(Instant now) {
        Objects.requireNonNull(now, "now");
        return decide(refusalAtStart(now));
    }

    /**
     * 회차(또는 묶음권과 날짜마다의 회차)를 고른 순간의 판정 — [예매하기]의 판정 다음에 아래 차례로 본다. 이 판정을 지나야 좌석(매수) 선택으로
     * 넘어가고 타이머가 돈다.
     * <ol>
     *   <li><b>회차 취소 안 됨</b> — 취소된 회차는 팔지 않는다. 묶음권은 날짜 하나라도 취소됐으면 막는다.</li>
     *   <li><b>그 회차 예매 종료 전</b> — 회차마다 예매 종료 시각이 따로다(docs/06 "예매 종료"). 행사는 판매 중이어도 오늘 회차는 마감됐을 수 있다.</li>
     *   <li><b>연령 제한</b> — 생년월일상 <b>그 회차 날짜의 만 나이</b>가 기준 나이에 모자라면 막는다. 묶음권은 첫 날로 본다(docs/03 "연령 제한").
     *       오늘 나이가 아니다 — 3/14 공연은 3/14에 만 19세가 되면 된다. 생년월일은 자기 신고라 검증되지 않고, 통과하면 예매 단계에서 필수
     *       체크박스를 받는다.</li>
     *   <li><b>회차 선택 제약</b> — 이미 가진 유효 예매 티켓과 견준다(docs/06 "회차 선택 제약"). '하루에 한 회차만'이면 고른 회차의 날짜에 다른
     *       회차 티켓이 있으면 막고, '행사 전체에서 한 회차만'이면 고른 회차와 가진 티켓의 회차를 합쳐 둘 이상이면 막는다. 같은 회차를 더 사는 것은
     *       된다(그건 1인 총 매수가 본다). 초대권 티켓은 세지 않는다.</li>
     * </ol>
     * 확인 필요 (#223) — '행사 전체에서 한 회차만'인 행사에서는 묶음권(회차 여럿)을 살 수 없다. 문서가 둘이 함께 걸린 경우를 말하지 않아 글자
     * 그대로 따랐다.
     *
     * @throws NotFoundException      고른 회차가 일정에 없으면({@link EventErrorCode#SESSION_NOT_FOUND})
     * @throws RuleViolationException 묶음권의 회차가 묶은 날짜마다 하나씩이 아니면({@link PricingErrorCode#BUNDLE_SESSIONS_MISMATCH})
     * @throws IllegalArgumentException 다른 행사의 묶음권이면
     */
    public BookingEligibility atSessionChoice(BookingSelection selection, Instant now) {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(now, "now");
        requireSelectable(selection);
        return decide(refusalAtSessionChoice(selection, now));
    }

    private Optional<BookingRefusal> refusalAtStart(Instant now) {
        return previewRefusal()
                .or(() -> salesRefusal(now))
                .or(this::accountRefusal);
    }

    private Optional<BookingRefusal> refusalAtSessionChoice(BookingSelection selection, Instant now) {
        return refusalAtStart(now)
                .or(() -> sessionRefusal(selection, now))
                .or(() -> ageRefusal(selection))
                .or(() -> sessionChoiceRefusal(selection));
    }

    private Optional<BookingRefusal> previewRefusal() {
        if (access == BookingAccess.PREVIEW_LINK) {
            return refuse(BookingErrorCode.PREVIEW_NOT_BOOKABLE, "미리보기 링크");
        }
        return Optional.empty();
    }

    private Optional<BookingRefusal> salesRefusal(Instant now) {
        EventStatus status = event.statusAt(schedule, now);
        String detail = "행사 " + schedule.eventId() + " " + status;
        return switch (status) {
            case ON_SALE -> Optional.empty();
            case DRAFT, AWAITING_OPEN -> refuse(BookingErrorCode.BOOKING_NOT_OPEN, detail);
            case SALES_PAUSED -> refuse(purpose.salesPausedCode(), detail);
            case SALES_CLOSED, ENDED, SETTLED -> refuse(BookingErrorCode.BOOKING_CLOSED, detail);
            case CANCELLED -> refuse(BookingErrorCode.EVENT_CANCELLED, detail);
        };
    }

    private Optional<BookingRefusal> accountRefusal() {
        if (audience.can(purpose.audienceAction())) {
            return Optional.empty();
        }
        ErrorCode reason = audience.isSuspended() ? AccountErrorCode.AUDIENCE_SUSPENDED : AccountErrorCode.AUDIENCE_NOT_ACTIVE;
        String accountId = audience.id().isPresent() ? String.valueOf(audience.id().getAsLong()) : "(저장 전)";
        return refuse(reason, "계정 " + accountId + " " + audience.status() + ", " + purpose);
    }

    private Optional<BookingRefusal> sessionRefusal(BookingSelection selection, Instant now) {
        for (long sessionId : selection.sessionIds()) {
            EventSession session = schedule.session(sessionId);
            if (session.isCancelled()) {
                return refuse(BookingErrorCode.SESSION_CANCELLED, "회차 " + sessionId);
            }
            SessionStatus status = schedule.timesOf(session).status(event.bookingSettings(), now);
            if (status != SessionStatus.ON_SALE) {
                return refuse(BookingErrorCode.SESSION_BOOKING_CLOSED, "회차 " + sessionId + " " + status);
            }
        }
        return Optional.empty();
    }

    private Optional<BookingRefusal> ageRefusal(BookingSelection selection) {
        AgeRestriction ageRestriction = event.bookingSettings().ageRestriction();
        if (!ageRestriction.isRestricted()) {
            return Optional.empty();
        }
        LocalDate referenceDate = firstDateOf(selection);
        int age = AgeCalculator.ageOn(audience.personalInformation().birthDate(), referenceDate);
        if (age >= ageRestriction.minimumAge()) {
            return Optional.empty();
        }
        String message = audienceSubject() + " 만 " + ageRestriction.minimumAge() + "세 이상만 예매할 수 있습니다.";
        return Optional.of(new BookingRefusal(BookingErrorCode.AGE_RESTRICTED, message,
                "기준일 " + referenceDate + ", 기준 나이 만 " + ageRestriction.minimumAge() + "세"));
    }

    private Optional<BookingRefusal> sessionChoiceRefusal(BookingSelection selection) {
        return switch (event.bookingSettings().sessionChoice()) {
            case NONE -> Optional.empty();
            case ONE_SESSION_PER_DATE -> otherSessionOnSameDateRefusal(selection);
            case ONE_SESSION_PER_EVENT -> otherSessionInEventRefusal(selection);
        };
    }

    private Optional<BookingRefusal> otherSessionOnSameDateRefusal(BookingSelection selection) {
        for (long sessionId : selection.sessionIds()) {
            long dateId = dateIdOf(sessionId);
            Set<Long> otherSessionIds = new HashSet<>(heldTickets.sessionIdsOn(dateId));
            otherSessionIds.remove(sessionId);
            if (!otherSessionIds.isEmpty()) {
                String message = audienceSubject() + " " + BookingErrorCode.ONE_SESSION_PER_DATE.message()
                        + " 이 날짜의 다른 회차를 이미 예매했습니다.";
                return Optional.of(new BookingRefusal(BookingErrorCode.ONE_SESSION_PER_DATE, message,
                        "고른 회차 " + sessionId + ", 같은 날짜에 가진 회차 " + otherSessionIds));
            }
        }
        return Optional.empty();
    }

    private Optional<BookingRefusal> otherSessionInEventRefusal(BookingSelection selection) {
        Set<Long> sessionIds = new HashSet<>(heldTickets.sessionIds());
        sessionIds.addAll(selection.sessionIds());
        if (sessionIds.size() <= 1) {
            return Optional.empty();
        }
        String message = audienceSubject() + " " + BookingErrorCode.ONE_SESSION_PER_EVENT.message();
        return Optional.of(new BookingRefusal(BookingErrorCode.ONE_SESSION_PER_EVENT, message,
                "고른 회차 " + selection.sessionIds() + ", 가진 회차 " + heldTickets.sessionIds()));
    }

    /**
     * 고른 회차가 이 행사의 일정에 있고, 묶음권이면 이 행사의 묶음권이며 고른 회차가 묶은 날짜마다 하나씩이다(docs/05 "그 상품으로 산 티켓은
     * 날짜마다 회차 하나씩"). 이것은 막을 까닭이 아니라 잘못된 요청이라 던진다.
     */
    private void requireSelectable(BookingSelection selection) {
        switch (selection) {
            case BookingSelection.SingleSession(long sessionId) -> schedule.session(sessionId);
            case BookingSelection.Bundle(BundleProduct product, List<Long> sessionIds) -> requireBundleSelectable(product, sessionIds);
        }
    }

    private void requireBundleSelectable(BundleProduct product, List<Long> sessionIds) {
        if (product.eventId() != schedule.eventId()) {
            throw new IllegalArgumentException("다른 행사의 묶음권입니다: 행사 " + schedule.eventId() + ", 묶음권의 행사 " + product.eventId());
        }
        Set<Long> chosenDateIds = new HashSet<>();
        for (long sessionId : sessionIds) {
            chosenDateIds.add(dateIdOf(sessionId));
        }
        boolean onePerBundledDate = chosenDateIds.size() == sessionIds.size() && chosenDateIds.equals(product.terms().dateIds());
        if (!onePerBundledDate) {
            throw new RuleViolationException(PricingErrorCode.BUNDLE_SESSIONS_MISMATCH,
                    "묶음권의 날짜 " + product.terms().dateIds() + ", 고른 회차 " + sessionIds);
        }
    }

    /** 고른 회차 중 가장 이른 날짜 — 회차 하나면 그 날짜, 묶음권이면 첫 날(docs/03 "기준 나이 — 묶음권은 첫 날"). */
    private LocalDate firstDateOf(BookingSelection selection) {
        return selection.sessionIds().stream()
                .map(sessionId -> schedule.timesOf(schedule.session(sessionId)).date())
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalArgumentException("고른 회차가 없습니다"));
    }

    private long dateIdOf(long sessionId) {
        return schedule.dateOf(sessionId).id().orElseThrow(() -> new IllegalArgumentException("저장 전 날짜의 회차입니다: " + sessionId));
    }

    /**
     * 관객 화면에서 이 행사를 가리키는 말 — "이 공연은" · "이 전시는" · "이 페스티벌은". 관객 화면은 유형 이름으로 부른다(docs/00 "용어", docs/03
     * "이 공연은 만 19세 이상만 예매할 수 있습니다").
     */
    private String audienceSubject() {
        String name = event.nameOn(ScreenSide.AUDIENCE);
        return "이 " + name + topicParticleAfter(name);
    }

    /** 앞 글자에 받침이 있으면 "은", 없으면 "는". 한글 음절이 아니면 둘 다 적는다. */
    private static String topicParticleAfter(String word) {
        char last = word.charAt(word.length() - 1);
        if (last < HANGUL_SYLLABLE_FIRST || last > HANGUL_SYLLABLE_LAST) {
            return "은(는)";
        }
        boolean hasFinalConsonant = (last - HANGUL_SYLLABLE_FIRST) % HANGUL_FINAL_CONSONANT_COUNT != 0;
        return hasFinalConsonant ? "은" : "는";
    }

    private static Optional<BookingRefusal> refuse(ErrorCode reason, String detail) {
        return Optional.of(BookingRefusal.of(reason, detail));
    }

    private static BookingEligibility decide(Optional<BookingRefusal> refusal) {
        return refusal.map(BookingEligibility::refused).orElseGet(BookingEligibility::eligible);
    }
}
