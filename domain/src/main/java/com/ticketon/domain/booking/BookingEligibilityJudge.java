package com.ticketon.domain.booking;

import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.error.ErrorCode;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 예매 자격 판정 — 이 관객이 이 행사를 지금 예매할 수 있는지(docs/06 "전체 흐름"). 막으면 까닭과 문구를 돌려준다({@link BookingEligibility}).
 * <p>
 * <b>예매 흐름의 단계마다 그 단계에서 알 수 있는 것을 본다.</b> 좌석을 잡은 뒤에 막으면 타이머와 선점이 헛돌기 때문이다.
 * <table>
 *   <caption>단계별 판정</caption>
 *   <tr><th>단계</th><th>메서드</th><th>보는 것</th></tr>
 *   <tr><td>[예매하기]</td><td>{@link #atStart}</td><td>미리보기 아님 · 판매 중 · 이용 정지 아님</td></tr>
 * </table>
 * <p>
 * <b>판매 중</b>은 저장된 행사 상태가 아니라 지금 시각을 반영한 상태로 본다({@link Event#statusAt}) — 오픈 시각이 지났는데 상태 전환 작업이 아직
 * 돌지 않았어도 예매가 열려 있고, 마지막 회차의 예매 종료 시각이 지났으면 판매 중단 중이어도 판매 종료다(docs/04 "시간이 운영 판단을 이깁니다").
 * <p>
 * 현재 시각은 인자로 받는다.
 */
public final class BookingEligibilityJudge {

    private final Event event;
    private final EventSchedule schedule;
    private final AudienceAccount audience;
    private final BookingAccess access;
    private final BookingPurpose purpose;

    private BookingEligibilityJudge(Event event, EventSchedule schedule, AudienceAccount audience, BookingAccess access,
            BookingPurpose purpose) {
        this.event = Objects.requireNonNull(event, "event");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.access = Objects.requireNonNull(access, "access");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        long eventId = event.id().orElseThrow(() -> new IllegalArgumentException("저장 전 행사는 예매할 수 없습니다"));
        if (schedule.eventId() != eventId) {
            throw new IllegalArgumentException("행사 " + eventId + "에 다른 행사(" + schedule.eventId() + ")의 일정을 넘겼습니다");
        }
    }

    /**
     * 판정을 준비한다.
     *
     * @param event    예매하려는 행사
     * @param schedule 그 행사의 일정
     * @param audience 예매하려는 관객의 계정
     * @param access   관객 화면을 어떻게 열었는지 — 미리보기 링크면 막는다
     * @param purpose  새 예매인지 변경인지
     * @throws IllegalArgumentException 저장 전 행사이거나 다른 행사의 일정이면
     */
    public static BookingEligibilityJudge of(Event event, EventSchedule schedule, AudienceAccount audience, BookingAccess access,
            BookingPurpose purpose) {
        return new BookingEligibilityJudge(event, schedule, audience, access, purpose);
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

    private Optional<BookingRefusal> refusalAtStart(Instant now) {
        return previewRefusal()
                .or(() -> salesRefusal(now))
                .or(this::accountRefusal);
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

    private static Optional<BookingRefusal> refuse(ErrorCode reason, String detail) {
        return Optional.of(BookingRefusal.of(reason, detail));
    }

    private static BookingEligibility decide(Optional<BookingRefusal> refusal) {
        return refusal.map(BookingEligibility::refused).orElseGet(BookingEligibility::eligible);
    }
}
