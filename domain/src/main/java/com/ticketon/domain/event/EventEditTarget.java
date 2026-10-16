package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventEditAvailability.ALLOWED;
import static com.ticketon.domain.event.EventEditAvailability.ALLOWED_WITH_RULES;
import static com.ticketon.domain.event.EventEditAvailability.LOCKED;
import static com.ticketon.domain.event.EventEditAvailability.NEEDS_ADMIN_APPROVAL;

/**
 * 행사에서 고치는 항목 — 상태별 수정 범위 표({@link EventEditScope})가 판정하는 단위.
 * <p>
 * docs/04 "예매가 열린 뒤(판매 중) 수정 범위" 표의 줄을 <b>한 항목씩 그대로</b> 옮겼다(입구 목록 · 검표 계정은 고치는 곳이 달라 둘로 나눴다). 항목마다
 * 세 가지가 정해져 있다.
 * <ol>
 *   <li><b>예매가 열린 뒤</b>(판매 중 · 판매 중단) 고칠 수 있는지 — 표의 오른쪽 칸.</li>
 *   <li><b>판매 대기에 초대권이 걸리면</b> 오픈 전부터 규칙이 붙는지 — 그 좌석의 이동 · 삭제와 판매 제외 해제, 그 회차 · 구역 삭제, 회차 시간 변경
 *       시 알림, 관계자 확보 수 하한, 장소 변경 승인(docs/04 "판매 대기에 초대권을 발권했으면", docs/09). 정원 · 가격 · 취소 규정을 포함한 나머지는
 *       계속 자유다.</li>
 *   <li><b>판매가 끝난 뒤</b>(판매 종료 ~ 행사 종료)에도 고칠 수 있는지 — 입구 목록 · 검표 계정만(docs/04 "판매 종료는 공연이 끝난 때가
 *       아닙니다"). 판매가 끝나면 현장이 이미 돌아가고 있어 현장 운영에 필요한 것만 남긴다.</li>
 * </ol>
 * 표 밖의 항목은 맨 아래에 따로 모았다. 문서가 다른 곳에서 정한 것(회차 출연)은 그 근거를 따르고, 문서에 없는 것은 "확인 필요 (#38)"로 예매가 열린
 * 뒤 막아 둔다 — 고칠 수 있게 여는 것은 나중에 해도 관객이 이미 산 표에 손해가 없지만, 열어 둔 것을 닫기 전에 산 표의 내용이 바뀌면 되돌릴 수 없다.
 * <p>
 * 수정이 아닌 조치(회차 취소 · 행사 취소, 초대권 발권 · 회수, 출연자 변경 알림, 공연 지연 · 중단 처리, 정산 · 현황 보기)는 이 표와 별개로 각자의
 * 규칙을 따른다(docs/04 "수정이 아닌 조치는 이 표와 별개입니다").
 */
public enum EventEditTarget {

    // ── docs/04 "예매가 열린 뒤(판매 중) 수정 범위" 표

    /** 장소 — 예매가 열린 뒤에는 관리자 승인 필수 + 전원 통지 + 공제 면제(#107). 판매 대기에 초대권이 있으면 오픈 전부터 승인. */
    VENUE(NEEDS_ADMIN_APPROVAL, true, false),

    /**
     * 회차 일시(종일형은 그날의 운영 시간) — 주최자 단독은 그 회차 시작 1주일 전까지({@link Event#requireSessionTimeEditable}). 티켓이 있으면 그
     * 회차의 예매자 · 초대권 받은 사람에게 알림 + 예매 표에 공제 면제(#105). 이후는 연기 절차(#106). 판매 대기에 초대권이 있으면 바꿀 수는 있고
     * 알림이 간다.
     */
    SESSION_DATE_TIME(ALLOWED_WITH_RULES, true, false),

    /** 제목 · 포스터 · 소개 — 예매가 열린 뒤에도 자유 변경. 변경 이력을 남긴다(docs/03 "변경 이력", #46). */
    TITLE_POSTER_DESCRIPTION(ALLOWED, false, false),

    /** 입구 목록 — 자유 변경. 판매가 끝난 뒤에도 행사가 끝날 때까지 고친다(18:10 정문 줄이 길어 "옆문" 추가). */
    ENTRANCES(ALLOWED, false, true),

    /** 검표 계정 — 자유 변경. 판매가 끝난 뒤에도 행사가 끝날 때까지 고친다(18:20 검표 계정 비밀번호가 단톡방에 올라가 바꾼다). */
    CHECKIN_ACCOUNTS(ALLOWED, false, true),

    /** 공개 수준 — 변경 가능. 판매가 끝나면 고정된다(docs/03 "전환 — 판매 종료 전까지 가능"). */
    VISIBILITY(ALLOWED, false, false),

    /** 예매 종료 시각 — 변경 가능({@link BookingSettings#bookingCloseLeadTime}). */
    BOOKING_CLOSE_TIME(ALLOWED, false, false),

    /** 정원(수량형) — 늘리기만. 판매 수량 이하로 줄일 수 없다(#64). */
    CAPACITY(ALLOWED_WITH_RULES, false, false),

    /** 좌석 배치도(도면) — 티켓이 걸린 좌석은 옮기거나 지울 수 없다. 빈 좌석 · 빈 블록 추가, 티켓 없는 좌석 수정은 가능(#64). */
    FLOOR_PLAN(ALLOWED_WITH_RULES, true, false),

    /** 블록 이름 — 변경 가능. 티켓이 걸린 좌석의 표기도 함께 바뀐다. 이력은 남기지 않는다(docs/02). 팔린 좌석이 있으면 알림 창(#110). */
    BLOCK_NAME(ALLOWED, false, false),

    /** 판매 제외 좌석 — 회차별로 자유 변경. 팔린 좌석은 제외 불가, 초대권이 걸린 좌석은 제외를 풀 수 없음, 관계자 확보 수 하한(#53, #64). */
    EXCLUDED_SEATS(ALLOWED_WITH_RULES, true, false),

    /**
     * 가격(구역 기본가 · 회차 가격 조정) — 변경 가능하되 신규 예매부터 적용(가격 계산 #48). 조기예매 · 쿠폰 · 묶음권은 표에 없어 여기 넣지 않았다 —
     * 확인 필요 (#38).
     */
    PRICE(ALLOWED, false, false),

    /** 회차 추가 — 가능. 추가된 회차에도 기존 예매 설정이 그대로 적용된다(docs/04 "회차 추가는 허용합니다"). */
    SESSION_ADDITION(ALLOWED, false, false),

    /** 회차 · 구역 삭제 — 티켓이 있으면 불가(#64). 취소 · 회수된 티켓만 남았으면 지울 수 있고 기록으로 남는다. */
    SESSION_OR_ZONE_DELETION(ALLOWED_WITH_RULES, true, false),

    /** 취소 · 환불 규정 — 불가. 예매 시점 규정이 관객마다 따라다닌다(docs/08 "규정은 기준 고정"). 판매 대기에는 예매가 없어 바꿀 수 있다. */
    CANCELLATION_POLICY(LOCKED, false, false),

    // ── 표 밖

    /** 회차 출연 — 제목 · 소개 · 포스터와 같다(docs/03 "회차별 출연 칸도 같습니다", docs/02 "출연 (선택)"). 변경 이력을 남긴다(#46). */
    PERFORMERS(ALLOWED, false, false),

    /**
     * 확인 필요 (#38) — 비어 있던 회차 종료 시각의 입력({@link EventSchedule#enterSessionEndTime}). 회차 시간이 잠긴 뒤에도 받고, 판매가 끝난
     * 뒤에도 행사가 끝날 때까지 받는다 — 마지막 회차의 종료 시각이 없으면 행사 종료를 판정할 수 없어(docs/04 "전환 트리거") 판매 종료에서 막으면
     * 행사가 끝나지 않고 정산도 할 수 없다. 이미 있는 종료 시각을 바꾸는 것은 회차 일시다.
     */
    MISSING_SESSION_END_TIME(ALLOWED, false, true),

    /** 확인 필요 (#38) — 유형 · 카테고리. 예매가 열린 뒤 수정 범위 표에 없어 막아 둔다. */
    TYPE_AND_CATEGORY(LOCKED, false, false),

    /** 확인 필요 (#38) — 상세 안내 · 지도 링크 · 공개용 연락처. 예매가 열린 뒤 수정 범위 표에 없어 막아 둔다. */
    VENUE_GUIDE_AND_CONTACT(LOCKED, false, false),

    /**
     * 확인 필요 (#38) — 예매 종료 시각 밖의 예매 설정(오픈 시각 · 검표 시작 · 1회 선택 매수 · 1인 총 매수 · 회차 선택 제약 · 재입장 · 연령 제한).
     * 예매가 열린 뒤 수정 범위 표에는 예매 종료 시각만 있어 나머지는 막아 둔다 — 산 뒤에 연령 제한이 생기는 일 같은 것을 막는다.
     */
    OTHER_BOOKING_SETTINGS(LOCKED, false, false);

    private final EventEditAvailability afterBookingOpened;
    private final boolean lockedByInvitationsBeforeOpening;
    private final boolean editableAfterSalesClosed;

    EventEditTarget(EventEditAvailability afterBookingOpened, boolean lockedByInvitationsBeforeOpening,
            boolean editableAfterSalesClosed) {
        this.afterBookingOpened = afterBookingOpened;
        this.lockedByInvitationsBeforeOpening = lockedByInvitationsBeforeOpening;
        this.editableAfterSalesClosed = editableAfterSalesClosed;
    }

    /** 예매가 열린 뒤(판매 중 · 판매 중단) 고칠 수 있는지 — docs/04 표의 오른쪽 칸. */
    public EventEditAvailability afterBookingOpened() {
        return afterBookingOpened;
    }

    /** 판매 대기에 초대권이 걸리면 오픈 전부터 예매가 열린 뒤의 규칙이 붙는 항목인지(docs/04, docs/09). */
    public boolean isLockedByInvitationsBeforeOpening() {
        return lockedByInvitationsBeforeOpening;
    }

    /** 판매가 끝난 뒤(판매 종료 ~ 행사 종료)에도 고칠 수 있는 항목인지 — 입구 목록 · 검표 계정. */
    public boolean isEditableAfterSalesClosed() {
        return editableAfterSalesClosed;
    }
}
