package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("행사의 입구 목록 — 도면에 속하고 회차마다 달라지지 않는다 (docs/02 \"입구 (게이트)\")")
class EventEntrancesTest {

    private static final long EVENT_ID = 7L;
    private static final long MAIN_GATE = 31L;
    private static final long SECOND_FLOOR_GATE = 32L;

    /** 정문 · 2층 전용 (docs/02 예시). */
    private static EventEntrances mainAndSecondFloor() {
        return EventEntrances.restore(EVENT_ID, List.of(
                Entrance.restore(MAIN_GATE, EntranceName.of("정문")),
                Entrance.restore(SECOND_FLOOR_GATE, EntranceName.of("2층 전용"))));
    }

    private static List<String> namesOf(EventEntrances entrances) {
        return entrances.entrances().stream().map(entrance -> entrance.name().value()).toList();
    }

    @Test
    @DisplayName("'정문', '2층 전용', 'VIP 입구'를 더한다 — 더한 순서대로 (docs/02 예시)")
    void addEntrances() {
        EventEntrances entrances = EventEntrances.empty(EVENT_ID);

        Entrance mainGate = entrances.add(EntranceName.of("정문"));
        entrances.add(EntranceName.of("2층 전용"));
        entrances.add(EntranceName.of("VIP 입구"));

        assertThat(mainGate.id()).isEmpty();
        assertThat(namesOf(entrances)).containsExactly("정문", "2층 전용", "VIP 입구");
    }

    @Test
    @DisplayName("판매 중 정문 줄이 길어 '옆문'을 더 연다 (docs/04 예시)")
    void addSideGateLater() {
        EventEntrances entrances = mainAndSecondFloor();

        entrances.add(EntranceName.of("옆문"));

        assertThat(namesOf(entrances)).containsExactly("정문", "2층 전용", "옆문");
    }

    @Test
    @DisplayName("같은 이름의 입구 둘은 받지 않는다 — 공백 · 자모 꼴이 달라도 같은 이름이다 (확인 필요)")
    void duplicateNameRejected() {
        EventEntrances entrances = mainAndSecondFloor();

        assertRejected(() -> entrances.add(EntranceName.of(" 정문")), EventErrorCode.ENTRANCE_NAME_ALREADY_TAKEN);
        assertRejected(() -> entrances.add(EntranceName.of(Normalizer.normalize("2층 전용", Normalizer.Form.NFD))),
                EventErrorCode.ENTRANCE_NAME_ALREADY_TAKEN);
        assertRejected(() -> entrances.rename(SECOND_FLOOR_GATE, EntranceName.of("정문")), EventErrorCode.ENTRANCE_NAME_ALREADY_TAKEN);
        assertThat(namesOf(entrances)).containsExactly("정문", "2층 전용");
    }

    @Test
    @DisplayName("입구 이름을 바꾼다 — 지금 이름 그대로면 바뀌지 않는다")
    void rename() {
        EventEntrances entrances = mainAndSecondFloor();

        assertThat(entrances.rename(MAIN_GATE, EntranceName.of("정문"))).isFalse();
        assertThat(entrances.rename(MAIN_GATE, EntranceName.of("1층 정문"))).isTrue();
        assertThat(entrances.entrance(MAIN_GATE).name()).isEqualTo(EntranceName.of("1층 정문"));
    }

    @Test
    @DisplayName("입구를 지운다 — 목록에 없는 입구는 찾을 수 없다")
    void remove() {
        EventEntrances entrances = mainAndSecondFloor();

        entrances.remove(SECOND_FLOOR_GATE);

        assertThat(namesOf(entrances)).containsExactly("정문");
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> entrances.remove(SECOND_FLOOR_GATE))
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(EventErrorCode.ENTRANCE_NOT_FOUND));
    }

    @Test
    @DisplayName("입구 이름 — 앞뒤 공백을 걷고, 비우면 받지 않고, 20자까지 받는다 (제안값, 확인 필요)")
    void entranceName() {
        assertThat(EntranceName.of("  VIP 입구 ").value()).isEqualTo("VIP 입구");
        assertThat(EntranceName.isValid("  ")).isFalse();
        assertThat(EntranceName.isValid(null)).isFalse();
        assertThat(ProposedPolicy.ENTRANCE_NAME_MAXIMUM_LENGTH).isEqualTo(20);
        assertThat(EntranceName.isValid("가".repeat(20))).isTrue();
        assertThat(EntranceName.isValid("가".repeat(21))).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> EntranceName.of(" "));
    }

    @Test
    @DisplayName("되살릴 때 이름이나 id가 겹치거나, 저장되지 않은 입구가 있으면 받지 않는다")
    void restoreValidation() {
        Entrance unsaved = EventEntrances.empty(EVENT_ID).add(EntranceName.of("정문"));

        assertThatIllegalArgumentException().isThrownBy(() -> EventEntrances.restore(EVENT_ID, List.of(
                Entrance.restore(MAIN_GATE, EntranceName.of("정문")), Entrance.restore(SECOND_FLOOR_GATE, EntranceName.of("정문")))));
        assertThatIllegalArgumentException().isThrownBy(() -> EventEntrances.restore(EVENT_ID, List.of(
                Entrance.restore(MAIN_GATE, EntranceName.of("정문")), Entrance.restore(MAIN_GATE, EntranceName.of("옆문")))));
        assertThatIllegalArgumentException().isThrownBy(() -> EventEntrances.restore(EVENT_ID, List.of(unsaved)));
    }
}
