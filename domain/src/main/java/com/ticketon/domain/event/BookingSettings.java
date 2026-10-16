package com.ticketon.domain.event;

import com.ticketon.domain.policy.TimePolicy;
import java.time.Instant;
import java.util.Objects;

/**
 * 예매 설정 — 위저드 5단계에서 정하는 것 가운데 공개 수준({@link EventVisibilitySetting})과 취소 규정({@link CancellationPolicy})을 뺀
 * 나머지(docs/03 "행사 만들기 위저드"). <b>전부 처음 값이 있어 건너뛸 수 있다</b>({@link #defaults}).
 * <table>
 *   <caption>예매 설정과 처음 값</caption>
 *   <tr><th>항목</th><th>처음 값</th><th>근거</th></tr>
 *   <tr><td>예매 오픈 시각 — 행사 단위</td><td>없음(판매 시작 즉시 연다, 확인 필요 #35)</td><td>docs/06 "오픈 시각"</td></tr>
 *   <tr><td>예매 종료 — 회차 시작 / 종일형 마지막 입장의 N 전</td><td>1시간</td><td>docs/06 "예매 종료"</td></tr>
 *   <tr><td>검표 시작 — 회차 시작 / 종일형 운영 시작의 N 전</td><td>1시간</td><td>docs/03 "검표 시작 시각"</td></tr>
 *   <tr><td>1회 선택 매수</td><td>4석</td><td>docs/06 "1회 선택 매수"</td></tr>
 *   <tr><td>1인 총 매수</td><td>제한 없음(확인 필요 #35)</td><td>docs/06 "1인 총 매수"</td></tr>
 *   <tr><td>회차 선택 제약</td><td>제한 없음</td><td>docs/06 "회차 선택 제약"</td></tr>
 *   <tr><td>재입장</td><td>끔</td><td>docs/03 "재입장 설정"</td></tr>
 *   <tr><td>연령 제한</td><td>없음</td><td>docs/03 "연령 제한"</td></tr>
 * </table>
 * <p>
 * <b>예매 오픈 시각은 행사 단위</b>다 — 회차마다 다르게 열면 관객이 헷갈린다. 오픈 전에도 행사 페이지는 공개되고, 관객 화면은 서버 기준 남은
 * 시간을 카운트다운으로 띄운다(docs/06 "오픈 전에도 행사 페이지는 공개"). 문서에 오픈 시각의 처음 값이 없어, 비워 두면 판매를 시작하는 순간 연다고
 * 보았다(docs/04 "판매 대기 → 판매 중 — 자동(오픈 시각 도달)"). 오픈 시각이 회차보다 앞서는지 같은 회차와 맞춰 보는 검증은 판매 시작 직전 검증이
 * 한다(docs/03 "판매 시작 직전에 검증").
 * <p>
 * 예매 종료 · 검표 시작은 기준 시각에서 앞서는 시간만 갖는다({@link LeadTime}). 기준 시각(회차형은 회차 시작, 종일형은 마지막 입장 · 운영 시작)은
 * 회차의 시각이 고른다({@link SessionTimes}). 회차를 나중에 추가해도 이 설정이 그대로 적용된다(docs/04 "회차 추가는 허용").
 * <p>
 * 언제 무엇을 바꿀 수 있는지(판매 중에도 예매 종료 시각은 변경 가능 등)는 상태별 수정 범위가 정한다(docs/04 "예매가 열린 뒤 수정 범위",
 * {@link EventEditTarget#BOOKING_CLOSE_TIME}, {@link EventEditTarget#OTHER_BOOKING_SETTINGS}).
 *
 * @param bookingOpensAt       예매 오픈 시각. 비워 두면 null — 판매를 시작하는 순간 연다
 * @param bookingCloseLeadTime 예매 종료 — 회차 시작(종일형은 마지막 입장)보다 이만큼 앞에서 닫는다
 * @param checkinOpenLeadTime  검표 시작 — 회차 시작(종일형은 운영 시작)보다 이만큼 앞에서 연다
 * @param seatsPerSelection    1회 선택 매수
 * @param ticketsPerPerson     1인 총 매수
 * @param sessionChoice        회차 선택 제약
 * @param reentry              재입장
 * @param ageRestriction       연령 제한
 */
public record BookingSettings(Instant bookingOpensAt, LeadTime bookingCloseLeadTime, LeadTime checkinOpenLeadTime,
        SeatsPerSelectionLimit seatsPerSelection, TicketsPerPersonLimit ticketsPerPerson, SessionChoiceRestriction sessionChoice,
        ReentrySetting reentry, AgeRestriction ageRestriction) {

    private static final BookingSettings DEFAULTS = new BookingSettings(
            null,
            LeadTime.of(TimePolicy.DEFAULT_BOOKING_CLOSE_BEFORE_START),
            LeadTime.of(TimePolicy.DEFAULT_CHECKIN_OPEN_BEFORE_START),
            SeatsPerSelectionLimit.defaultLimit(),
            TicketsPerPersonLimit.defaultLimit(),
            SessionChoiceRestriction.defaultRestriction(),
            ReentrySetting.defaultSetting(),
            AgeRestriction.none());

    public BookingSettings {
        Objects.requireNonNull(bookingCloseLeadTime, "bookingCloseLeadTime");
        Objects.requireNonNull(checkinOpenLeadTime, "checkinOpenLeadTime");
        Objects.requireNonNull(seatsPerSelection, "seatsPerSelection");
        Objects.requireNonNull(ticketsPerPerson, "ticketsPerPerson");
        Objects.requireNonNull(sessionChoice, "sessionChoice");
        Objects.requireNonNull(reentry, "reentry");
        Objects.requireNonNull(ageRestriction, "ageRestriction");
    }

    /** 처음 값 — 5단계를 건너뛰면 이대로 판다(위 표). */
    public static BookingSettings defaults() {
        return DEFAULTS;
    }
}
