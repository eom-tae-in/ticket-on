package com.ticketon.domain.event;

/**
 * 1인 총 매수를 세는 범위 — 날짜별 / 행사 전체(docs/06 "1인 총 매수 — 기준을 고릅니다").
 * <table>
 *   <caption>8회차(4일 × 2회) 공연에서 4매 제한이면</caption>
 *   <tr><th>기준</th><th>한 사람이 가질 수 있는 매수</th></tr>
 *   <tr><td>날짜별</td><td>최대 16매</td></tr>
 *   <tr><td>행사 전체</td><td>4매</td></tr>
 * </table>
 * <b>시간 회차별 기준은 두지 않는다</b> — 같은 날 15시와 19시를 다 사면 우회되기 때문이다. 날짜가 하나뿐인 행사는 둘이 같은 값이라 고르는
 * 칸을 보여 주지 않는다({@link #isChoiceShown}). 이 상수 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum TicketsPerPersonBasis {

    /** 날짜별 — 날짜마다 한도만큼. 묶음권 1장은 포함한 날짜마다 1매로 센다(docs/05 "매수 제한 계산"). */
    PER_DATE,

    /** 행사 전체 — 행사 전체에서 한도만큼. */
    EVENT_TOTAL;

    /**
     * 기준을 고르는 칸을 보여 주는지 — 날짜가 둘 이상일 때만. 날짜가 하나면(회차가 여럿이어도) 날짜별과 행사 전체가 같은 값이라 보여 주지 않는다
     * (docs/06 "단일 회차 행사에서는 이 선택 자체를 보여주지 않습니다"). 아직 날짜를 만들지 않았으면(0) 보여 주지 않는다.
     *
     * @param dateCount 행사의 날짜 수 (0 이상)
     */
    public static boolean isChoiceShown(int dateCount) {
        if (dateCount < 0) {
            throw new IllegalArgumentException("날짜 수는 0 이상이어야 합니다: " + dateCount);
        }
        return dateCount > 1;
    }
}
