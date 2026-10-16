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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.application.event.port.in.SaveEventBookingSettingsCommand;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.AgeRestriction;
import com.ticketon.domain.event.BookingSettings;
import com.ticketon.domain.event.CancellationPolicy;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventPassword;
import com.ticketon.domain.event.EventVisibility;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.event.LeadTime;
import com.ticketon.domain.event.ReentrySetting;
import com.ticketon.domain.event.SeatsPerSelectionLimit;
import com.ticketon.domain.event.SessionChoiceRestriction;
import com.ticketon.domain.event.TicketsPerPersonBasis;
import com.ticketon.domain.event.TicketsPerPersonLimit;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.policy.CancellationFeeTier;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달의 운영진이 위저드 5단계(공개 수준 · 예매 설정 · 취소 규정 조정)를 넘긴다 — 전부 처음 값이 있어 건너뛸 수 있다(docs/03 "행사 만들기
 * 위저드", {@link EventWizardScene}).
 */
@DisplayName("위저드 5단계(공개 수준 · 예매 설정 · 취소 규정) 저장 유스케이스")
class SaveEventBookingSettingsServiceTest {

    private static final Instant LATER = NOW.plus(Duration.ofHours(2));

    /** "공연 3일 전까지 전액 환불"(docs/08 조정 예시). */
    private static final CancellationPolicy FULL_REFUND_UNTIL_THREE_DAYS_BEFORE = CancellationPolicy.of(List.of(
            CancellationFeeTier.of(3, 0), CancellationFeeTier.of(1, 30), CancellationFeeTier.of(0, 90)));

    private final EventWizardScene scene = new EventWizardScene();
    private final SaveEventBookingSettingsService service = scene.saveEventBookingSettingsService();
    private final long springPlay = scene.createEventService()
            .create(new CreateEventCommand(acting(JUNGHOON, scene.twelveMonths), springPlayItems())).eventId();

    /** 오픈 3/1 14:00, 예매 종료 2시간 전, 검표 90분 전, 1회 6석, 날짜별 4매, 하루에 한 회차만, 재입장 켬, 만 19세 이상. */
    private static BookingSettings adjustedBookingSettings() {
        return new BookingSettings(Instant.parse("2027-03-01T05:00:00Z"), LeadTime.of(Duration.ofHours(2)),
                LeadTime.of(Duration.ofMinutes(90)), SeatsPerSelectionLimit.of(6),
                TicketsPerPersonLimit.of(4, TicketsPerPersonBasis.PER_DATE), SessionChoiceRestriction.ONE_SESSION_PER_DATE,
                ReentrySetting.ALLOWED, AgeRestriction.atLeast(19));
    }

    private EventWizardResult save(long staffAccountId, long organizerId, EventVisibility visibility, EventPassword password,
            BookingSettings bookingSettings, CancellationPolicy cancellationPolicy) {
        return service.save(new SaveEventBookingSettingsCommand(acting(staffAccountId, organizerId), springPlay, visibility,
                password, bookingSettings, cancellationPolicy));
    }

    @Test
    @DisplayName("처음 값 그대로 넘기면 진행 단계가 5단계로 오르고 저장된다 — 값은 처음 값 그대로")
    void defaultsSaveProgresses() {
        scene.clock.moveTo(LATER);

        EventWizardResult result = save(PLANNER, scene.twelveMonths, EventVisibility.PUBLIC, null, BookingSettings.defaults(),
                CancellationPolicy.standard());

        assertThat(result.wizardProgress()).isEqualTo(EventWizardStep.BOOKING_SETTINGS);
        assertThat(result.lastModifiedAt()).isEqualTo(LATER);
        assertThat(result.bookingStep().maxSeatsPerSelection()).isEqualTo(4);
        assertThat(result.bookingStep().cancellationPolicyStandard()).isTrue();
        assertThat(scene.eventRepository.saves()).isEqualTo(1);
        assertThat(scene.recordRepository.records()).last().satisfies(record -> {
            assertThat(record.action()).isEqualTo(StaffAction.EDIT_EVENT);
            assertThat(record.summary()).isEqualTo("행사 예매 설정 저장 (5단계)");
            assertThat(record.operatedAt()).isEqualTo(LATER);
        });
    }

    @Test
    @DisplayName("조정한 공개 수준 · 예매 설정 · 취소 규정을 저장하고, 같은 값을 다시 저장하면 저장도 기록도 없다")
    void savesAdjustedValuesOnce() {
        EventWizardResult result = save(JUNGHOON, scene.twelveMonths, EventVisibility.LINK_ONLY, null,
                adjustedBookingSettings(), FULL_REFUND_UNTIL_THREE_DAYS_BEFORE);
        save(PLANNER, scene.twelveMonths, EventVisibility.LINK_ONLY, null, adjustedBookingSettings(),
                FULL_REFUND_UNTIL_THREE_DAYS_BEFORE);

        EventWizardResult.BookingStep bookingStep = result.bookingStep();
        assertThat(bookingStep.visibility()).isEqualTo(EventVisibility.LINK_ONLY);
        assertThat(bookingStep.bookingOpensAt()).isEqualTo(Instant.parse("2027-03-01T05:00:00Z"));
        assertThat(bookingStep.bookingCloseLeadTime()).isEqualTo(Duration.ofHours(2));
        assertThat(bookingStep.checkinOpenLeadTime()).isEqualTo(Duration.ofMinutes(90));
        assertThat(bookingStep.maxSeatsPerSelection()).isEqualTo(6);
        assertThat(bookingStep.maxTicketsPerPerson()).isEqualTo(4);
        assertThat(bookingStep.ticketsPerPersonBasis()).isEqualTo(TicketsPerPersonBasis.PER_DATE);
        assertThat(bookingStep.sessionChoice()).isEqualTo(SessionChoiceRestriction.ONE_SESSION_PER_DATE);
        assertThat(bookingStep.reentryAllowed()).isTrue();
        assertThat(bookingStep.minimumAge()).isEqualTo(19);
        assertThat(bookingStep.cancellationPolicyStandard()).isFalse();
        assertThat(bookingStep.cancellationFeeTiers()).containsExactly(new EventWizardResult.CancellationFeeTier(3, 0),
                new EventWizardResult.CancellationFeeTier(1, 30), new EventWizardResult.CancellationFeeTier(0, 90));
        assertThat(scene.eventRepository.saves()).isEqualTo(1);
        assertThat(scene.recordRepository.records()).hasSize(2);
    }

    @Test
    @DisplayName("암호 보호 — 새 암호를 적었을 때만 해시해 걸고, 다시 저장할 때 적지 않거나 걸려 있던 암호를 다시 적으면 바뀐 것이 없다")
    void passwordIsHashedOnlyWhenNew() {
        EventWizardResult protectedResult = save(JUNGHOON, scene.twelveMonths, EventVisibility.PASSWORD_PROTECTED,
                new EventPassword("0315"), BookingSettings.defaults(), CancellationPolicy.standard());
        save(JUNGHOON, scene.twelveMonths, EventVisibility.PASSWORD_PROTECTED, null, BookingSettings.defaults(),
                CancellationPolicy.standard());
        save(JUNGHOON, scene.twelveMonths, EventVisibility.PASSWORD_PROTECTED, new EventPassword("0315"),
                BookingSettings.defaults(), CancellationPolicy.standard());

        assertThat(protectedResult.bookingStep().passwordSet()).isTrue();
        assertThat(scene.passwordHashPort.hashes()).isEqualTo(1);
        assertThat(scene.eventRepository.saves()).isEqualTo(1);
        assertThat(scene.eventRepository.findById(springPlay).orElseThrow().visibility().passwordHash().value())
                .isEqualTo("{fake}1:0315");

        save(JUNGHOON, scene.twelveMonths, EventVisibility.PASSWORD_PROTECTED, new EventPassword("0412"),
                BookingSettings.defaults(), CancellationPolicy.standard());

        assertThat(scene.passwordHashPort.hashes()).isEqualTo(2);
        assertThat(scene.eventRepository.findById(springPlay).orElseThrow().visibility().passwordHash().value())
                .isEqualTo("{fake}2:0412");
    }

    @Test
    @DisplayName("암호가 걸린 적 없이 암호 보호로 두면서 암호를 적지 않으면 막는다 (EVENT_PASSWORD_REQUIRED) — 저장도 기록도 없다")
    void passwordRequired() {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> save(JUNGHOON, scene.twelveMonths, EventVisibility.PASSWORD_PROTECTED, null,
                        BookingSettings.defaults(), CancellationPolicy.standard()))
                .satisfies(rejected -> assertThat(rejected.errorCode()).isEqualTo(EventErrorCode.EVENT_PASSWORD_REQUIRED));
        assertThat(scene.eventRepository.saves()).isZero();
        assertThat(scene.recordRepository.records()).hasSize(1);
    }

    @Test
    @DisplayName("암호 보호가 아닌데 새 암호를 담은 입력은 만들 수 없다")
    void passwordOnlyForPasswordProtection() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SaveEventBookingSettingsCommand(
                acting(JUNGHOON, scene.twelveMonths), springPlay, EventVisibility.LINK_ONLY, new EventPassword("0315"),
                BookingSettings.defaults(), CancellationPolicy.standard()));
    }

    @Test
    @DisplayName("다른 주최자의 운영진은 403, 자기 주최자 경로로 열두달 행사에 닿으면 404다")
    void outsidersCannotReachEvent() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> save(DOHYUN, scene.twelveMonths, EventVisibility.PUBLIC, null, BookingSettings.defaults(),
                        CancellationPolicy.standard()))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> save(YEOREUM, scene.lateSummer, EventVisibility.PUBLIC, null, BookingSettings.defaults(),
                        CancellationPolicy.standard()))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
        assertThat(scene.eventRepository.saves()).isZero();
    }

    @Test
    @DisplayName("잠금 순서 — 계정 줄 → 주최자 줄(외래 키 확인 잠금) → 행사 줄(FOR UPDATE) (#195)")
    void locksAccountOrganizerThenEvent() {
        int locksBeforeSave = scene.rowLockLog.locks().size();

        save(PLANNER, scene.twelveMonths, EventVisibility.PUBLIC, null, BookingSettings.defaults(), CancellationPolicy.standard());

        assertThat(scene.rowLockLog.locks().subList(locksBeforeSave, scene.rowLockLog.locks().size())).containsExactly(
                "계정 " + PLANNER + " FOR KEY SHARE", "주최자 " + scene.twelveMonths + " FOR KEY SHARE",
                "행사 " + springPlay + " FOR UPDATE");
    }
}
