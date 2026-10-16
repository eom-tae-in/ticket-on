package com.ticketon.domain.policy;

import java.time.Duration;
import java.time.LocalTime;
import java.time.Period;
import java.util.List;

/**
 * docs/99 C "시간" 표의 확정 숫자.
 * <p>
 * 날짜·시각은 한국 시각 기준이다. 공연 지연 환급률처럼 99 C에서 시간 표에 함께 적힌 비율도 여기 둔다.
 */
public final class TimePolicy {

    /** 좌석 선택 타이머 — 좌석(또는 매수) 선택 화면 진입부터 쿠폰·정보 확인·동의까지. docs/06 "타이머 두 개" */
    public static final Duration SEAT_SELECTION_TIMER = Duration.ofMinutes(10);

    /** 결제 타이머 — 결제창을 여는 순간 다시 시작. docs/06 "타이머 두 개", "타이머는 결제창을 열면 재설정" */
    public static final Duration PAYMENT_TIMER = Duration.ofMinutes(7);

    /** 예매 종료 기본값 — 회차형은 회차 시작, 종일형은 마지막 입장의 1시간 전. 주최자가 조정한다. docs/06 "예매 종료" */
    public static final Duration DEFAULT_BOOKING_CLOSE_BEFORE_START = Duration.ofHours(1);

    /** 검표 시작 기본값 — 회차형은 회차 시작, 종일형은 운영 시작의 1시간 전부터. 주최자가 조정한다. docs/03·docs/10 "검표 시작 시각" */
    public static final Duration DEFAULT_CHECKIN_OPEN_BEFORE_START = Duration.ofHours(1);

    /**
     * 관객 취소 공제율 기준표 — 소비자분쟁해결기준(공연업). 관람일 10일 전까지 0%(전액 환급) · 9~7일 전 10% · 6~3일 전 20% · 2~1일 전 30% ·
     * 당일(시작 전) 90%. 문서 표와 같이 먼 날부터 적었다.
     * 행사마다 이 표가 처음 값이고, 주최자는 모든 구간에서 공제율이 이 표 이하가 되게만 조정한다.
     * 99 C 표에는 없지만 고시가 정한 값이라 확정이다(docs/99 A-1). docs/08 "법적 기준", "규정은 기준 고정, 관대한 방향만 조정 가능"
     */
    public static final List<CancellationFeeTier> STANDARD_CANCELLATION_FEE_TIERS = List.of(
            CancellationFeeTier.of(10, 0),
            CancellationFeeTier.of(7, 10),
            CancellationFeeTier.of(3, 20),
            CancellationFeeTier.of(1, 30),
            CancellationFeeTier.of(0, 90));

    /**
     * 예매 직후 전액 환급 기간 — 예매 후 24시간 이내에 취소하면, 관람일 3일 전까지라는 조건 아래({@link #FULL_REFUND_AFTER_BOOKING_MINIMUM_DAYS_BEFORE})
     * 공제 없이 전액 환급. 기준표에 늘 붙는 예외라 주최자가 조정한 규정에도 그대로 붙는다.
     * 99 C 표에는 없지만 고시가 정한 값이라 확정이다(docs/99 A-1). docs/08 "법적 기준"
     */
    public static final Duration FULL_REFUND_PERIOD_AFTER_BOOKING = Duration.ofHours(24);

    /**
     * 예매 직후 전액 환급의 조건 — 관람일 3일 전까지(관람일까지 남은 달력 일수가 3 이상). 2일 전부터는 예매 직후라도 기준표대로 공제한다.
     * 99 C 표에는 없지만 고시가 정한 값이라 확정이다(docs/99 A-1). docs/08 "법적 기준"
     */
    public static final int FULL_REFUND_AFTER_BOOKING_MINIMUM_DAYS_BEFORE = 3;

    /** 회차 시간 수정 잠금 — 주최자 단독 수정은 그 회차 시작 1주일 전까지. 연기는 예외. docs/04 "예매가 열린 뒤(판매 중) 수정 범위", "회차 연기" */
    public static final Period SESSION_TIME_EDIT_LOCK_BEFORE_START = Period.ofWeeks(1);

    /** 공연 지연 환급 기준 — 주최자 사정으로 예정보다 30분 이상 늦게 시작하면 환급. 30분 미만은 처리 없음. docs/08 "공연 지연" */
    public static final Duration DELAY_REFUND_MINIMUM_DELAY = Duration.ofMinutes(30);

    /** 공연 지연 환급률 — 그 회차에 입장한 티켓의 결제 금액(묶음권은 그날 몫)의 10%. docs/08 "공연 지연" */
    public static final Percent DELAY_REFUND_RATE = Percent.of(10);

    /** 공연 중단 판정 — 예정 공연 시간의 절반 이하만 하고 멈추면 회차 취소와 같게 처리. docs/08 "공연 중단", docs/02 "회차의 시간 정보" */
    public static final Percent INTERRUPTION_MAXIMUM_PLAYED_SHARE = Percent.of(50);

    /**
     * 공연 지연 처리 기한 — 그 행사 정산 완료 전, 그리고 행사 종료 30일 이내. docs/08 "공연 지연", docs/04 "판매 종료는 공연이 끝난 때가 아닙니다"
     * <p>
     * 환급 대상을 정하는 입장 기록이 행사 종료 30일 뒤 삭제되므로 입장 기록 보관 기간과 같은 값이다.
     */
    public static final Period DELAY_HANDLING_DEADLINE_AFTER_EVENT_END =
            RecordRetentionPolicy.ENTRY_RECORD_RETENTION_AFTER_EVENT_END;

    /** 신고 수정 요청 기한 기본값 — 관리자가 요청마다 정한다. 넘기면 관리자가 페이지를 내린다. docs/01 "수정 요청" */
    public static final Period DEFAULT_CORRECTION_REQUEST_DEADLINE = Period.ofDays(3);

    /** 전날 리마인더 발송 시각 — 관람일 전날 한국 시각 낮 12시. 묶음권은 날짜마다. docs/12 "전날 리마인더" */
    public static final LocalTime DAY_BEFORE_REMINDER_TIME = LocalTime.NOON;

    /** 주최자 심사 목표 — 신청서 제출부터 영업일 3일 이내. docs/01 "주최자 신청과 승인" */
    public static final BusinessDays ORGANIZER_REVIEW_TARGET = BusinessDays.of(3);

    /** 정산 목표 — 행사 종료(취소된 행사는 취소 시점) 후 영업일 7일 이내. docs/11 "정산 시점은 행사 종료 후 영업일 7일 이내" */
    public static final BusinessDays SETTLEMENT_TARGET = BusinessDays.of(7);

    /** 작성 중 행사 알림 — 30일 동안 수정이 없으면 알림. 자동 삭제는 하지 않는다. docs/04 "작성 중 행사 관리" */
    public static final Period DRAFT_EVENT_IDLE_NOTICE = Period.ofDays(30);

    private TimePolicy() {
    }
}
