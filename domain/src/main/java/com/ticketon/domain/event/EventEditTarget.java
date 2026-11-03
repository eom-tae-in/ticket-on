package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventEditAvailability.ALLOWED;
import static com.ticketon.domain.event.EventEditAvailability.ALLOWED_WITH_RULES;
import static com.ticketon.domain.event.EventEditAvailability.LOCKED;
import static com.ticketon.domain.event.EventEditAvailability.NEEDS_ADMIN_APPROVAL;

/**
 * 행사에서 고치는 항목 — 상태별 수정 범위 표({@link EventEditScope})가 판정하는 단위.
 * <p>
 * docs/04 "예매가 열린 뒤(판매 중) 수정 범위" 표의 줄을 <b>한 항목씩 그대로</b> 옮겼다(입구 목록 · 검표 계정은 고치는 곳이 달라 둘로 나눴다). 표 아래
 * 이유 문단("산 사람의 조건은 관객에게 유리한 쪽으로만 바꿉니다")이 왜 그렇게 정했는지를 말한다. 항목마다 세 가지가 정해져 있다.
 * <ol>
 *   <li><b>예매가 열린 뒤</b>(판매 중, 예매가 열린 뒤의 판매 중단) 고칠 수 있는지 — 표의 오른쪽 칸.</li>
 *   <li><b>판매 대기에 초대권이 걸리면</b> 오픈 전부터 규칙이 붙는지 — 그 좌석의 이동 · 삭제와 판매 제외 해제, 그 회차 · 구역 삭제, 회차 시간 변경
 *       시 알림, 관계자 확보 수 하한, 장소 변경 승인(docs/04 "판매 대기에 초대권을 발권했으면", docs/09). 정원 · 가격 · 취소 규정을 포함한 나머지는
 *       계속 자유다.</li>
 *   <li><b>판매가 끝난 뒤</b>(판매 종료 ~ 행사 종료)에도 고칠 수 있는지 — 입구 목록 · 검표 계정만(docs/04 "판매 종료는 공연이 끝난 때가
 *       아닙니다"). 판매가 끝나면 현장이 이미 돌아가고 있어 현장 운영에 필요한 것만 남긴다.</li>
 * </ol>
 * 표 밖의 항목(회차 출연, 비어 있던 회차 종료 시각, 예매 오픈 시각)은 맨 아래에 따로 모았다. 문서가 표 밖의 다른 곳에서 정한 것이라 항목마다 그
 * 근거를 적었다.
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

    /** 제목 · 포스터 · 소개 — 예매가 열린 뒤에도 자유 변경. 변경 이력을 남긴다(docs/03 "변경 이력", {@link EventChangeHistory}). */
    TITLE_POSTER_DESCRIPTION(ALLOWED, false, false),

    /**
     * 상세 안내 · 지도 링크 · 공개용 연락처 — 예매가 열린 뒤에도 자유 변경. 조건이 아니라 안내라서, 막으면 정훈이 "주차 불가" 한 줄을 못 넣는다
     * (docs/04 "산 사람의 조건은 관객에게 유리한 쪽으로만 바꿉니다"). 변경 이력을 남긴다(docs/03 "상세 안내 · 지도 링크 · 공개용 연락처도 이력을
     * 남깁니다", 이력 저장은 #231). 장소(공연장 이름 · 주소)는 {@link #VENUE}의 승인 절차 그대로다.
     */
    VENUE_GUIDE_AND_CONTACT(ALLOWED, false, false),

    /** 유형 — 불가. 목록 필터와 화면 이름이 통째로 바뀌는 분류라 예매가 열린 뒤에는 바꾸지 않는다. 판매 대기에는 예매가 없어 바꿀 수 있다. */
    TYPE(LOCKED, false, false),

    /** 카테고리 — 변경 가능. 조건이 아니라 안내라서 막을 이유가 없다. 바꿔도 지금 유형의 목록에서 고른다(docs/03 "카테고리"). */
    CATEGORY(ALLOWED, false, false),

    /** 입구 목록 — 자유 변경. 판매가 끝난 뒤에도 행사가 끝날 때까지 고친다(18:10 정문 줄이 길어 "옆문" 추가). */
    ENTRANCES(ALLOWED, false, true),

    /** 검표 계정 — 자유 변경. 판매가 끝난 뒤에도 행사가 끝날 때까지 고친다(18:20 검표 계정 비밀번호가 단톡방에 올라가 바꾼다). */
    CHECKIN_ACCOUNTS(ALLOWED, false, true),

    /** 공개 수준 — 변경 가능. 판매가 끝나면 고정된다(docs/03 "전환 — 판매 종료 전까지 가능"). */
    VISIBILITY(ALLOWED, false, false),

    /** 예매 종료 시각 — 변경 가능({@link BookingSettings#bookingCloseLeadTime}). */
    BOOKING_CLOSE_TIME(ALLOWED, false, false),

    /** 검표 시작 시각 — 변경 가능({@link BookingSettings#checkinOpenLeadTime}). */
    CHECKIN_OPEN_TIME(ALLOWED, false, false),

    /**
     * 1회 선택 매수 · 1인 총 매수(기준 포함) · 회차 선택 제약 — 변경 가능하되 신규 예매부터 적용한다. 이미 산 표는 그대로이고, 1인 총 매수에는 이미
     * 산 표도 센다(#69). 가격처럼 앞으로 살 사람에게만 걸리므로 암표 조짐이 보이면 바로 줄일 수 있어야 한다(docs/04 "산 사람의 조건은 관객에게
     * 유리한 쪽으로만 바꿉니다").
     */
    PURCHASE_LIMITS_AND_SESSION_CHOICE(ALLOWED, false, false),

    /**
     * 재입장 — 켜기만 한다. 끄면 이미 산 서윤이 현장에서 다시 들어오지 못한다({@link Event#saveBookingStep}). 판매 대기에는 예매가 없어 자유고, 초대권이
     * 있어도 오픈 전부터 붙는 규칙이 아니다(docs/04 "판매 대기에 초대권을 발권했으면"의 목록에 없다).
     */
    REENTRY(ALLOWED_WITH_RULES, false, false),

    /**
     * 연령 제한 — 낮추거나 없애기만 한다. 새로 걸거나 올리면 이미 산 서윤이 현장에서 막히고, 연령 미달은 예매 동의대로 환불도 되지 않는다
     * ({@link Event#saveBookingStep}). 판매 대기에는 예매가 없어 자유고, 초대권이 있어도 오픈 전부터 붙는 규칙이 아니다.
     */
    AGE_RESTRICTION(ALLOWED_WITH_RULES, false, false),

    /** 정원(수량형) — 늘리기만. 판매 수량 이하로 줄일 수 없다(#64). */
    CAPACITY(ALLOWED_WITH_RULES, false, false),

    /** 좌석 배치도(도면) — 티켓이 걸린 좌석은 옮기거나 지울 수 없다. 빈 좌석 · 빈 블록 추가, 티켓 없는 좌석 수정은 가능(#64). */
    FLOOR_PLAN(ALLOWED_WITH_RULES, true, false),

    /** 블록 이름 — 변경 가능. 티켓이 걸린 좌석의 표기도 함께 바뀐다. 이력은 남기지 않는다(docs/02). 팔린 좌석이 있으면 알림 창(#110). */
    BLOCK_NAME(ALLOWED, false, false),

    /** 판매 제외 좌석 — 회차별로 자유 변경. 팔린 좌석은 제외 불가, 초대권이 걸린 좌석은 제외를 풀 수 없음, 관계자 확보 수 하한(#53, #64). */
    EXCLUDED_SEATS(ALLOWED_WITH_RULES, true, false),

    /**
     * 가격(구역 기본가 · 회차 가격 조정) — 변경 가능하되 신규 예매부터 적용(가격 계산 #48). 판매가 끝나면 고정이다(docs/04 "판매 종료는 공연이
     * 끝난 때가 아닙니다").
     * <p>
     * 조기예매 · 쿠폰은 예매가 열린 뒤에도 고치되 하한이 붙는다 — 끝난 조기예매는 바꾸지 않고, 조기예매 수량 · 쿠폰 전체 사용 횟수는 이미 쓴 수
     * 아래로 줄이지 않는다(docs/99 E "가격 · 할인"). 그 규칙은 조기예매 · 쿠폰을 저장하는 쪽(#51)이 본다. 확인 필요 (#223) — 묶음권 할인율을 예매가
     * 열린 뒤 바꿀 수 있는지, 판매 중에 조기예매 · 쿠폰 · 묶음권을 새로 만들 수 있는지는 문서에 없다.
     */
    PRICE(ALLOWED, false, false),

    /** 회차 추가 — 가능. 추가된 회차에도 기존 예매 설정이 그대로 적용된다(docs/04 "회차 추가는 허용합니다"). */
    SESSION_ADDITION(ALLOWED, false, false),

    /** 회차 · 구역 삭제 — 티켓이 있으면 불가(#64). 취소 · 회수된 티켓만 남았으면 지울 수 있고 기록으로 남는다. */
    SESSION_OR_ZONE_DELETION(ALLOWED_WITH_RULES, true, false),

    /** 취소 · 환불 규정 — 불가. 예매 시점 규정이 관객마다 따라다닌다(docs/08 "규정은 기준 고정"). 판매 대기에는 예매가 없어 바꿀 수 있다. */
    CANCELLATION_POLICY(LOCKED, false, false),

    // ── 표 밖

    /** 회차 출연 — 제목 · 소개 · 포스터와 같다(docs/03 "회차별 출연 칸도 같습니다", docs/02 "출연 (선택)"). 변경 이력을 남긴다({@link EventChangeHistory}). */
    PERFORMERS(ALLOWED, false, false),

    /**
     * 비어 있던 회차 종료 시각의 입력({@link EventSchedule#enterSessionEndTime}). 회차 시간이 잠긴 뒤에도 받고 — 잠기는 시점(회차 시작 1주일 전)에
     * 입력을 요구한다(docs/02 "종료 시각은 행사 종료 판정에 쓰입니다") —, 판매가 끝난 뒤에도 행사가 끝날 때까지 받는다(docs/04 "상태별로 할 수 있는
     * 것", "판매 종료는 공연이 끝난 때가 아닙니다", docs/99 E "행사 · 배치도"). 마지막 회차의 종료 시각이 없으면 행사 종료를 판정할 수 없어
     * (docs/04 "전환 트리거") 판매 종료에서 막으면 행사가 끝나지 않고 정산도 할 수 없다. 이미 있는 종료 시각을 바꾸는 것은 회차 일시다.
     */
    MISSING_SESSION_END_TIME(ALLOWED, false, true),

    /**
     * 예매 오픈 시각({@link BookingSettings#bookingOpensAt}) — 예매가 열린 뒤에는 이미 지나 해당이 없다(docs/04 "예매가 열린 뒤(판매 중) 수정
     * 범위"의 이유 문단). 판매 대기에는 아직 열리지 않아 바꿀 수 있다.
     */
    BOOKING_OPEN_TIME(LOCKED, false, false);

    private final EventEditAvailability afterBookingOpened;
    private final boolean lockedByInvitationsBeforeOpening;
    private final boolean editableAfterSalesClosed;

    EventEditTarget(EventEditAvailability afterBookingOpened, boolean lockedByInvitationsBeforeOpening,
            boolean editableAfterSalesClosed) {
        this.afterBookingOpened = afterBookingOpened;
        this.lockedByInvitationsBeforeOpening = lockedByInvitationsBeforeOpening;
        this.editableAfterSalesClosed = editableAfterSalesClosed;
    }

    /** 예매가 열린 뒤(판매 중, 예매가 열린 뒤의 판매 중단) 고칠 수 있는지 — docs/04 표의 오른쪽 칸. */
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
