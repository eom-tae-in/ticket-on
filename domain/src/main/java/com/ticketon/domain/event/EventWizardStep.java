package com.ticketon.domain.event;

/**
 * 행사 만들기 위저드의 단계(docs/03 "행사 만들기 위저드", docs/13 "행사 만들기 (위저드 7단계 · 자동 임시저장)").
 * <table>
 *   <caption>위저드 7단계</caption>
 *   <tr><th>단계</th><th>내용</th><th></th></tr>
 *   <tr><td>1</td><td>유형 · 제목 · 소개 · 포스터 · 장소 · 카테고리 (+ 상세 안내 · 지도 링크 · 공개용 연락처)</td><td>필수</td></tr>
 *   <tr><td>2</td><td>회차 방식 · 회차 (날짜 · 시간 · 운영 시간 · 출연)</td><td>필수</td></tr>
 *   <tr><td>3</td><td>블록 · 구역 · 좌석 배치도 또는 정원</td><td>필수</td></tr>
 *   <tr><td>4</td><td>가격 · 조기예매 · 쿠폰 · 묶음권</td><td>필수</td></tr>
 *   <tr><td>5</td><td>공개 수준 · 오픈/종료 · 매수 제한 · 재입장 · 검표 시작 · 연령 제한 · 취소 규정 조정</td><td>처음 값 있음</td></tr>
 *   <tr><td>6</td><td>입구 목록 · 검표 계정</td><td>판매 시작 뒤에도 입력 가능</td></tr>
 *   <tr><td>7</td><td>검토 · 관객 화면 미리보기 → 판매 시작</td><td></td></tr>
 * </table>
 * <b>임시저장은 자동이다</b> — 단계를 넘길 때마다 그 단계가 저장되고, 나갔다 들어오면 이어서 한다. "저장" 버튼을 누르게 하면 안 누르고 나가서 다
 * 날린다. 그래서 행사는 작성 중에 어느 단계까지 저장했는지를 갖는다({@link Event#wizardProgress}) — 작성 중 행사 목록의 진행률("4/7 단계 · 가격
 * 설정까지 완료")과 [이어서 작성]이 멈춘 단계를 이것으로 안다(docs/04 "작성 중 행사 관리").
 * <p>
 * 7단계는 저장하는 단계가 아니라 판매를 시작하는 단계다 — 단계를 저장해 진행 단계가 7단계가 되는 일은 없다. 화면이 진행률의 분모(7)로 쓴다.
 * <p>
 * 이 상수 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum EventWizardStep {

    /** 1단계 — 기본 정보. 행사를 만들 때 함께 저장한다({@link Event#create}). */
    BASIC_INFORMATION(1),

    /** 2단계 — 회차 방식 · 날짜 · 회차. */
    SCHEDULE(2),

    /** 3단계 — 블록 · 구역 · 좌석 배치도 또는 정원. */
    SEATING(3),

    /** 4단계 — 가격 · 조기예매 · 쿠폰 · 묶음권. */
    PRICING(4),

    /** 5단계 — 공개 수준 · 예매 설정 · 취소 규정 조정. 전부 처음 값이 있어 건너뛸 수 있다. */
    BOOKING_SETTINGS(5),

    /** 6단계 — 입구 목록 · 검표 계정. 처음 값이 있어 건너뛸 수 있고, 판매 시작 뒤에도 입력한다. */
    ENTRANCES_AND_CHECKIN_ACCOUNTS(6),

    /** 7단계 — 검토 · 관객 화면 미리보기 → 판매 시작. 저장하는 단계가 아니다. */
    REVIEW(7);

    private final int number;

    EventWizardStep(int number) {
        this.number = number;
    }

    /** 위저드의 단계 수 — 진행률의 분모("4/7 단계"). */
    public static int stepCount() {
        return values().length;
    }

    /** 단계 번호 — 1부터. */
    public int number() {
        return number;
    }

    /** 이 단계가 그 단계보다 뒤인지. */
    public boolean isAfter(EventWizardStep other) {
        return number > other.number;
    }

    /**
     * 이 단계까지 저장한 행사가 이어서 할 단계 — 바로 다음 단계다. 작성 중 행사 목록의 [이어서 작성]이 이 단계를 연다(docs/04 "작성 중 행사
     * 관리" — "멈춘 단계로 바로 가는 버튼"). 4단계까지 저장했으면 5단계를 연다 — 5 · 6단계는 처음 값이 있어 건너뛸 수 있지만(docs/03) 건너뛸지는
     * 그 단계를 연 주최자가 고른다. 7단계(검토)는 마지막이라 그대로 7단계다.
     */
    public EventWizardStep next() {
        EventWizardStep[] steps = values();
        return ordinal() + 1 < steps.length ? steps[ordinal() + 1] : this;
    }
}
