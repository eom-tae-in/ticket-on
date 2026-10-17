package com.ticketon.domain.ticket;

import java.util.Optional;

/**
 * 공유 링크가 무효가 된 이유 — 링크는 티켓에 붙어 있어 티켓이 사라지면 링크도 사라진다(docs/07 "공유 링크의 수명").
 * <p>
 * <b>무효가 된 링크를 열면 이유를 알려 준다.</b> 빈 화면이나 오류를 띄우면 받은 사람이 링크가 잘못된 줄 안다. 링크 화면의 문구는 문서 그대로다
 * ({@link #title}, {@link #guidance}).
 * <table>
 *   <caption>공유 링크의 수명 (docs/07)</caption>
 *   <tr><th>무효가 된 이유</th><th>링크 화면</th></tr>
 *   <tr><td>티켓 취소 · 변경</td><td>"취소된 티켓입니다" · 예매하신 분께 확인해 주세요</td></tr>
 *   <tr><td>QR 재발행</td><td>"재발행으로 바뀐 티켓입니다" · 예매하신 분께 새 링크를 받아 주세요</td></tr>
 *   <tr><td>행사 종료</td><td>"종료된 행사입니다"</td></tr>
 * </table>
 * 어느 이유인지는 티켓이 판정한다({@link Ticket#shareLinkInvalidReason}).
 * <p>
 * 이 이름은 화면이 분기에 쓰므로 바꾸지 않는다.
 */
public enum ShareLinkInvalidReason {

    /** 티켓 취소 · 변경 — 변경은 기존 티켓을 취소하므로 무효다. 새 티켓에는 새 링크가 생긴다(docs/08). */
    CANCELLED("취소된 티켓입니다", "예매하신 분께 확인해 주세요."),

    /** QR 재발행 — 재발행하면 열쇠가 바뀌어 옛 열쇠로 만든 링크가 즉시 무효다. */
    REISSUED("재발행으로 바뀐 티켓입니다", "예매하신 분께 새 링크를 받아 주세요."),

    /** 행사 종료 — 행사가 끝나면 자동 만료된다. */
    EVENT_ENDED("종료된 행사입니다", null);

    private final String title;
    private final String guidance;

    ShareLinkInvalidReason(String title, String guidance) {
        this.title = title;
        this.guidance = guidance;
    }

    /** 링크 화면의 제목 — "취소된 티켓입니다". */
    public String title() {
        return title;
    }

    /** 제목 아래 안내 — "예매하신 분께 확인해 주세요." 행사 종료는 안내가 없다. */
    public Optional<String> guidance() {
        return Optional.ofNullable(guidance);
    }
}
