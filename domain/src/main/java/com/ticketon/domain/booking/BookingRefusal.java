package com.ticketon.domain.booking;

import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.error.ErrorCode;
import java.util.Objects;

/**
 * 예매 자격 판정이 막은 까닭 — 화면이 분기에 쓰는 코드와 그대로 보여 줄 문구(docs/06 "전체 흐름").
 * <p>
 * <b>코드는 오류 코드 enum의 상수 이름 그대로다.</b> 예매의 까닭은 {@link BookingErrorCode}이고, 이용 정지 · 활동 중이 아닌 계정은 계정의 오류
 * 코드({@link AccountErrorCode#AUDIENCE_SUSPENDED} · {@link AccountErrorCode#AUDIENCE_NOT_ACTIVE})를 그대로 쓴다 — 신고 · 전화번호 변경을 막을
 * 때와 같은 코드라 화면이 이용 정지를 한 가지로 알린다(docs/01 "관객 이용 정지").
 * <p>
 * <b>문구는 대개 그 코드의 문구</b>이고({@link #of}), 기준 나이 · 한도처럼 그 행사의 값이 들어가는 문구만 판정이 채운다 — "이 공연은 만 19세
 * 이상만 예매할 수 있습니다."(docs/03 "연령 제한"). 오류 코드이기도 해서 규칙 위반으로 던지면 채운 문구가 그대로 화면에 나간다
 * ({@link BookingEligibility#requireEligible}).
 *
 * @param reason  막은 까닭의 오류 코드 — 화면이 분기에 쓰는 코드가 이 상수 이름이다
 * @param message 그대로 보여 줄 문구
 * @param detail  로그에만 남기는 설명(어느 회차 · 몇 매인지). 화면에 내보내지 않는다. 없으면 null
 */
public record BookingRefusal(ErrorCode reason, String message, String detail) implements ErrorCode {

    public BookingRefusal {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(message, "message");
        if (reason instanceof BookingRefusal) {
            throw new IllegalArgumentException("막은 까닭은 오류 코드 enum의 상수입니다: " + reason.name());
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("막은 까닭의 문구가 비어 있습니다: " + reason.name());
        }
    }

    /** 그 코드의 문구 그대로 막는다. */
    static BookingRefusal of(ErrorCode reason, String detail) {
        return new BookingRefusal(reason, reason.message(), detail);
    }

    /** 막은 까닭의 상수 이름 — 화면이 분기에 쓰는 코드({@link #code}). */
    @Override
    public String name() {
        return reason.name();
    }
}
