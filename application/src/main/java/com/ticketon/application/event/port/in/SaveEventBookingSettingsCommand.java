package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.BookingSettings;
import com.ticketon.domain.event.CancellationPolicy;
import com.ticketon.domain.event.EventPassword;
import com.ticketon.domain.event.EventVisibility;
import java.util.Objects;

/**
 * 위저드 5단계(공개 수준 · 예매 설정 · 취소 규정 조정) 저장 입력 — 단계의 칸을 모두 보낸다(docs/03 "행사 만들기 위저드").
 * <p>
 * 새 암호는 암호 보호로 두면서 암호를 새로 정할 때만 담는다. 암호 보호로 두면서 적지 않으면 걸려 있던 암호를 그대로 쓴다 — 저장된 암호를 다시
 * 보여 줄 수 없으므로, 다른 칸을 고치느라 같은 단계를 다시 저장할 때마다 암호를 새로 적게 하지 않는다. 암호는 값을 가리는 값 객체라 문자열로
 * 바꿔도 새지 않는다.
 *
 * @param actingStaff        저장하는 운영진과 주최자
 * @param eventId            행사 id
 * @param visibility         공개 수준
 * @param newPassword        새로 정한 암호 — 암호 보호일 때만. 새로 정하지 않았으면 null
 * @param bookingSettings    예매 설정
 * @param cancellationPolicy 취소 규정 — 기준표 그대로면 {@link CancellationPolicy#standard}
 */
public record SaveEventBookingSettingsCommand(ActingStaff actingStaff, long eventId, EventVisibility visibility,
        EventPassword newPassword, BookingSettings bookingSettings, CancellationPolicy cancellationPolicy) {

    /**
     * @throws IllegalArgumentException 암호 보호가 아닌데 새 암호를 담으면
     */
    public SaveEventBookingSettingsCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(bookingSettings, "bookingSettings");
        Objects.requireNonNull(cancellationPolicy, "cancellationPolicy");
        if (newPassword != null && !visibility.requiresPassword()) {
            throw new IllegalArgumentException("암호는 암호 보호일 때만 정합니다: " + visibility);
        }
    }
}
