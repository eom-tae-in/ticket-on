package com.ticketon.application.event.service;

import static com.ticketon.application.event.service.EventWizardScene.DOHYUN;
import static com.ticketon.application.event.service.EventWizardScene.JUNGHOON;
import static com.ticketon.application.event.service.EventWizardScene.NOW;
import static com.ticketon.application.event.service.EventWizardScene.PLANNER;
import static com.ticketon.application.event.service.EventWizardScene.YEOREUM;
import static com.ticketon.application.event.service.EventWizardScene.acting;
import static com.ticketon.application.event.service.EventWizardScene.encoreItems;
import static com.ticketon.application.event.service.EventWizardScene.springPlayItems;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.EventBasicInformationItems;
import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.application.event.port.in.SaveEventBasicInformationCommand;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달의 운영진이 위저드 1단계를 다시 넘긴다 — 단계를 넘길 때마다 저장되는 자동 임시저장이다(docs/03 "임시저장은 자동으로",
 * {@link EventWizardScene}).
 */
@DisplayName("위저드 1단계(기본 정보) 저장 유스케이스")
class SaveEventBasicInformationServiceTest {

    private static final Instant LATER = NOW.plus(Duration.ofHours(2));

    private final EventWizardScene scene = new EventWizardScene();
    private final SaveEventBasicInformationService service = scene.saveEventBasicInformationService();
    private final long springPlay = scene.createEventService()
            .create(new CreateEventCommand(acting(JUNGHOON, scene.twelveMonths), springPlayItems())).eventId();

    private EventWizardResult save(long staffAccountId, long organizerId, long eventId, EventBasicInformationItems items) {
        return service.save(new SaveEventBasicInformationCommand(acting(staffAccountId, organizerId), eventId, items));
    }

    @Test
    @DisplayName("제목을 고치면 저장되고 마지막 수정 시각이 바뀌며, 고친 운영진의 조작 기록이 남는다")
    void savesChangedTitle() {
        scene.clock.moveTo(LATER);

        EventWizardResult result = save(PLANNER, scene.twelveMonths, springPlay, encoreItems());

        assertThat(result.basicInformation().title()).isEqualTo("2026 봄 앵콜공연 〈열두 번째 달〉");
        assertThat(result.lastModifiedAt()).isEqualTo(LATER);
        assertThat(result.wizardProgress()).isEqualTo(EventWizardStep.BASIC_INFORMATION);
        assertThat(scene.eventRepository.saves()).isEqualTo(1);
        assertThat(scene.eventRepository.findById(springPlay).orElseThrow().basicInformation().title().value())
                .isEqualTo("2026 봄 앵콜공연 〈열두 번째 달〉");
        assertThat(scene.recordRepository.records()).last().satisfies(record -> {
            assertThat(record.staffAccountId()).isEqualTo(PLANNER);
            assertThat(record.action()).isEqualTo(StaffAction.EDIT_EVENT);
            assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.EVENT);
            assertThat(record.targetId()).isEqualTo(springPlay);
            assertThat(record.summary()).isEqualTo("행사 기본 정보 저장 (1단계)");
            assertThat(record.operatedAt()).isEqualTo(LATER);
        });
    }

    @Test
    @DisplayName("같은 값을 여러 번 저장해도 저장도 조작 기록도 없고 마지막 수정 시각도 그대로다 — 자동 임시저장")
    void sameValuesLeaveNoTrace() {
        scene.clock.moveTo(LATER);

        save(JUNGHOON, scene.twelveMonths, springPlay, springPlayItems());
        EventWizardResult result = save(PLANNER, scene.twelveMonths, springPlay, springPlayItems());

        assertThat(result.lastModifiedAt()).isEqualTo(NOW);
        assertThat(scene.eventRepository.saves()).isZero();
        assertThat(scene.recordRepository.records()).hasSize(1);
    }

    @Test
    @DisplayName("잠금 순서 — 계정 줄 → 주최자 줄(외래 키 확인 잠금) → 행사 줄(FOR UPDATE) (#195)")
    void locksAccountOrganizerThenEvent() {
        int locksBeforeSave = scene.rowLockLog.locks().size();

        save(PLANNER, scene.twelveMonths, springPlay, encoreItems());

        assertThat(scene.rowLockLog.locks().subList(locksBeforeSave, scene.rowLockLog.locks().size())).containsExactly(
                "계정 " + PLANNER + " FOR KEY SHARE", "주최자 " + scene.twelveMonths + " FOR KEY SHARE",
                "행사 " + springPlay + " FOR UPDATE");
    }

    @Test
    @DisplayName("계정 줄을 기다리는 사이 내보내졌으면 잠근 뒤 다시 확인해 막는다 — 행사 줄은 잠그지 않고 저장도 기록도 없다")
    void removedWhileWaitingForLock() {
        scene.staffAccountRepository.runOnNextLock(() -> scene.staffRepository.saveEnded(
                scene.staffRepository.findByOrganizerId(scene.twelveMonths).removeOperator(JUNGHOON, PLANNER, NOW)));

        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> save(PLANNER, scene.twelveMonths, springPlay, encoreItems()))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
        assertThat(scene.rowLockLog.locks()).doesNotContain("행사 " + springPlay + " FOR UPDATE");
        assertThat(scene.eventRepository.saves()).isZero();
        assertThat(scene.recordRepository.records()).hasSize(1);
    }

    @Test
    @DisplayName("다른 주최자의 운영진은 열두달 경로로 403, 자기 주최자 경로로 열두달 행사에 닿으면 없는 행사(404)다")
    void outsidersCannotReachEvent() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> save(DOHYUN, scene.twelveMonths, springPlay, encoreItems()))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> save(YEOREUM, scene.lateSummer, springPlay, encoreItems()))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
        assertThat(scene.eventRepository.saves()).isZero();
        assertThat(scene.recordRepository.records()).hasSize(1);
    }

    @Test
    @DisplayName("없는 행사는 404다")
    void missingEvent() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> save(JUNGHOON, scene.twelveMonths, springPlay + 100, encoreItems()))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
    }

    @Test
    @DisplayName("예매가 열린 뒤 장소를 바꾸면 수정 범위로 막고 아무것도 저장하지 않는다 — 관리자 승인 안내")
    void venueLockedAfterBookingOpened() {
        Event stored = scene.eventRepository.findById(springPlay).orElseThrow();
        scene.eventRepository.store(Event.restore(springPlay, stored.organizerId(), stored.publicAddress(),
                stored.basicInformation(), null, stored.visibility(), stored.bookingSettings(), stored.cancellationPolicy(),
                EventStatus.ON_SALE, NOW, stored.salesPauses(), EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS, NOW));
        EventBasicInformationItems items = springPlayItems();
        EventBasicInformationItems moved = new EventBasicInformationItems(items.title(), items.type(), items.categoryName(),
                null, items.introduction(), new Venue("△△소극장", "서울특별시 강남구 테헤란로 7"), items.venueDetails(),
                items.mapLink(), items.publicContact());

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> save(JUNGHOON, scene.twelveMonths, springPlay, moved))
                .satisfies(rejected -> assertThat(rejected.errorCode())
                        .isEqualTo(EventErrorCode.VENUE_CHANGE_NEEDS_ADMIN_APPROVAL));
        assertThat(scene.eventRepository.saves()).isZero();
        assertThat(scene.recordRepository.records()).hasSize(1);
    }
}
