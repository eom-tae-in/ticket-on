package com.ticketon.application.event.service;

import static com.ticketon.application.event.service.EventWizardScene.DOHYUN;
import static com.ticketon.application.event.service.EventWizardScene.JUNGHOON;
import static com.ticketon.application.event.service.EventWizardScene.NOW;
import static com.ticketon.application.event.service.EventWizardScene.PLANNER;
import static com.ticketon.application.event.service.EventWizardScene.YEOREUM;
import static com.ticketon.application.event.service.EventWizardScene.acting;
import static com.ticketon.application.event.service.EventWizardScene.springPlayItems;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.event.port.in.AddEntranceCommand;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.EntranceResult;
import com.ticketon.application.event.port.in.GetEventEntrancesQuery;
import com.ticketon.application.event.port.in.RemoveEntranceCommand;
import com.ticketon.application.event.port.in.RenameEntranceCommand;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EntranceName;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달의 운영진이 위저드 6단계에서 입구를 더하고 이름을 바꾸고 지우고 본다({@link EventWizardScene}). 문서의 "정문", "2층 전용", "VIP 입구"
 * (docs/02 "입구 (게이트)")와 판매가 끝난 18:10의 "옆문"(docs/04 "판매 종료는 공연이 끝난 때가 아닙니다").
 */
@DisplayName("입구 추가 · 이름 변경 · 삭제 · 조회 유스케이스 (위저드 6단계)")
class EntranceServicesTest {

    private static final Instant LATER = NOW.plus(Duration.ofHours(2));

    private final EventWizardScene scene = new EventWizardScene();
    private final AddEntranceService addEntranceService = scene.addEntranceService();
    private final RenameEntranceService renameEntranceService = scene.renameEntranceService();
    private final RemoveEntranceService removeEntranceService = scene.removeEntranceService();
    private final GetEventEntrancesService getEventEntrancesService = scene.getEventEntrancesService();
    private final long springPlay = scene.createEventService()
            .create(new CreateEventCommand(acting(JUNGHOON, scene.twelveMonths), springPlayItems())).eventId();

    private EntranceResult add(String name) {
        return addEntranceService.add(new AddEntranceCommand(acting(PLANNER, scene.twelveMonths), springPlay, EntranceName.of(name)));
    }

    private EntranceResult rename(long entranceId, String name) {
        return renameEntranceService.rename(new RenameEntranceCommand(acting(PLANNER, scene.twelveMonths), springPlay, entranceId,
                EntranceName.of(name)));
    }

    private void remove(long entranceId) {
        removeEntranceService.remove(new RemoveEntranceCommand(acting(PLANNER, scene.twelveMonths), springPlay, entranceId));
    }

    private List<String> entranceNames() {
        return getEventEntrancesService.get(new GetEventEntrancesQuery(acting(JUNGHOON, scene.twelveMonths), springPlay))
                .entrances().stream().map(EntranceResult::name).toList();
    }

    private static void assertRejected(ThrowingCallable action, EventErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .satisfies(rejected -> assertThat(rejected.errorCode()).isEqualTo(expectedCode));
    }

    @Test
    @DisplayName("'정문' · '2층 전용' · 'VIP 입구'를 더하면 더한 순서로 저장되고, 진행 단계가 6단계가 되며, 하나마다 조작 기록이 남는다")
    void addsThreeEntrances() {
        scene.clock.moveTo(LATER);

        EntranceResult mainGate = add(" 정문 ");
        add("2층 전용");
        add("VIP 입구");

        assertThat(mainGate.name()).isEqualTo("정문");
        assertThat(entranceNames()).containsExactly("정문", "2층 전용", "VIP 입구");
        assertThat(scene.eventRepository.findById(springPlay).orElseThrow().wizardProgress())
                .isEqualTo(EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS);
        assertThat(scene.eventRepository.findById(springPlay).orElseThrow().lastModifiedAt()).isEqualTo(LATER);
        assertThat(scene.recordRepository.records()).extracting(record -> record.action() + " " + record.summary()).endsWith(
                StaffAction.EDIT_EVENT + " 입구 추가 · 정문 (6단계)", StaffAction.EDIT_EVENT + " 입구 추가 · 2층 전용 (6단계)",
                StaffAction.EDIT_EVENT + " 입구 추가 · VIP 입구 (6단계)");
        assertThat(scene.eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("판매가 끝난 뒤에도 행사가 끝날 때까지 입구를 더하고 바꾸고 지운다 — 18:10 정문 줄이 길어 '옆문'을 더한다")
    void editableAfterSalesClosed() {
        long mainGate = add("정문").entranceId();
        scene.moveEventTo(springPlay, EventStatus.SALES_CLOSED);

        long sideGate = add("옆문").entranceId();
        rename(mainGate, "정문(매표소 쪽)");
        remove(sideGate);

        assertThat(entranceNames()).containsExactly("정문(매표소 쪽)");
    }

    @Test
    @DisplayName("지난 행사는 입구도 고정이다 (EDIT_LOCKED_FOR_PAST_EVENT)")
    void lockedForPastEvent() {
        long mainGate = add("정문").entranceId();
        scene.moveEventTo(springPlay, EventStatus.ENDED);

        assertRejected(() -> add("옆문"), EventErrorCode.EDIT_LOCKED_FOR_PAST_EVENT);
        assertRejected(() -> rename(mainGate, "옆문"), EventErrorCode.EDIT_LOCKED_FOR_PAST_EVENT);
        assertRejected(() -> remove(mainGate), EventErrorCode.EDIT_LOCKED_FOR_PAST_EVENT);
        assertThat(entranceNames()).containsExactly("정문");
    }

    @Test
    @DisplayName("이름을 바꾸면 바꾸기 전 · 뒤 이름으로 조작 기록이 남고, 같은 이름이면 저장도 기록도 없다")
    void renamesEntrance() {
        long mainGate = add("정문").entranceId();
        int savesBefore = scene.entranceRepository.saves();
        int recordsBefore = scene.recordRepository.records().size();

        EntranceResult same = rename(mainGate, " 정문 ");

        assertThat(same).isEqualTo(new EntranceResult(mainGate, "정문"));
        assertThat(scene.entranceRepository.saves()).isEqualTo(savesBefore);
        assertThat(scene.recordRepository.records()).hasSize(recordsBefore);

        EntranceResult renamed = rename(mainGate, "옆문");

        assertThat(renamed).isEqualTo(new EntranceResult(mainGate, "옆문"));
        assertThat(scene.recordRepository.records()).last()
                .satisfies(record -> assertThat(record.summary()).isEqualTo("입구 이름 변경 · 정문 → 옆문 (6단계)"));
    }

    @Test
    @DisplayName("같은 이름의 입구는 둘일 수 없다 — 더하기 · 이름 바꾸기 모두 막고 아무것도 남기지 않는다")
    void duplicateNamesAreRejected() {
        add("정문");
        long vipGate = add("VIP 입구").entranceId();
        int recordsBefore = scene.recordRepository.records().size();

        assertRejected(() -> add("정문"), EventErrorCode.ENTRANCE_NAME_ALREADY_TAKEN);
        assertRejected(() -> rename(vipGate, "정문"), EventErrorCode.ENTRANCE_NAME_ALREADY_TAKEN);

        assertThat(entranceNames()).containsExactly("정문", "VIP 입구");
        assertThat(scene.recordRepository.records()).hasSize(recordsBefore);
    }

    @Test
    @DisplayName("입구를 지우면 지운 시각과 함께 남고 목록에서 빠지며 조작 기록이 남는다 — 그 이름으로 다시 더할 수 있다")
    void removesEntrance() {
        add("정문");
        long vipGate = add("VIP 입구").entranceId();
        scene.clock.moveTo(LATER);

        remove(vipGate);

        assertThat(scene.entranceRepository.removedEntrances()).containsEntry(vipGate, LATER);
        assertThat(entranceNames()).containsExactly("정문");
        assertThat(scene.recordRepository.records()).last()
                .satisfies(record -> assertThat(record.summary()).isEqualTo("입구 삭제 · VIP 입구 (6단계)"));

        assertThat(add("VIP 입구").entranceId()).isNotEqualTo(vipGate);
    }

    @Test
    @DisplayName("목록에 없는(이미 지운) 입구는 바꾸거나 지울 수 없다 (ENTRANCE_NOT_FOUND)")
    void unknownEntrance() {
        long mainGate = add("정문").entranceId();
        remove(mainGate);

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> remove(mainGate))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.ENTRANCE_NOT_FOUND));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> rename(mainGate, "옆문"))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.ENTRANCE_NOT_FOUND));
    }

    @Test
    @DisplayName("다른 주최자의 운영진은 403, 자기 주최자 경로로 열두달 행사에 닿으면 404다 — 조회도 같다")
    void outsidersCannotReachEntrances() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> addEntranceService.add(new AddEntranceCommand(acting(DOHYUN, scene.twelveMonths), springPlay,
                        EntranceName.of("정문"))))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> addEntranceService.add(new AddEntranceCommand(acting(YEOREUM, scene.lateSummer), springPlay,
                        EntranceName.of("정문"))))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> getEventEntrancesService.get(new GetEventEntrancesQuery(acting(YEOREUM, scene.lateSummer),
                        springPlay)))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
        assertThat(scene.entranceRepository.saves()).isZero();
    }

    @Test
    @DisplayName("잠금 순서 — 계정 줄 → 주최자 줄(외래 키 확인 잠금) → 행사 줄(FOR UPDATE), 입구 목록은 행사 줄을 잡은 뒤 읽는다 (#195)")
    void locksAccountOrganizerThenEvent() {
        int locksBefore = scene.rowLockLog.locks().size();

        add("정문");

        assertThat(scene.rowLockLog.locks().subList(locksBefore, scene.rowLockLog.locks().size())).containsExactly(
                "계정 " + PLANNER + " FOR KEY SHARE", "주최자 " + scene.twelveMonths + " FOR KEY SHARE",
                "행사 " + springPlay + " FOR UPDATE");
    }
}
