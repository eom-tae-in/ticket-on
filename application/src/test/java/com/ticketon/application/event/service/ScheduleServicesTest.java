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

import com.ticketon.application.event.port.in.AddAllDayDateCommand;
import com.ticketon.application.event.port.in.AddTimedSessionCommand;
import com.ticketon.application.event.port.in.ChangeDateOperatingHoursCommand;
import com.ticketon.application.event.port.in.ChangeSessionDateTimeCommand;
import com.ticketon.application.event.port.in.ChangeSessionPerformersCommand;
import com.ticketon.application.event.port.in.ChangeSessionPriceAdjustmentCommand;
import com.ticketon.application.event.port.in.ChooseSessionModeCommand;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.EventScheduleResult;
import com.ticketon.application.event.port.in.GetEventScheduleQuery;
import com.ticketon.application.event.port.in.OperatingHoursResult;
import com.ticketon.application.event.port.in.RemoveScheduleDateCommand;
import com.ticketon.application.event.port.in.RemoveSessionCommand;
import com.ticketon.application.event.port.in.ScheduleDateResult;
import com.ticketon.application.event.port.in.ScheduleSessionResult;
import com.ticketon.application.event.port.in.SessionPriceAdjustmentResult;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.event.OperatingHours;
import com.ticketon.domain.event.SessionAdded;
import com.ticketon.domain.event.SessionMode;
import com.ticketon.domain.event.SessionPerformers;
import com.ticketon.domain.event.SessionPriceAdjustment;
import com.ticketon.domain.event.SessionPriceAdjustmentKind;
import com.ticketon.domain.event.SessionRemoved;
import com.ticketon.domain.event.TimedSessionTime;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달의 운영진이 위저드 2단계에서 회차 방식을 고르고 날짜 · 회차를 만들고 고치고 지운다({@link EventWizardScene}). 문서의 예시 — 공연
 * "3/14 15:00 · 19:00 회차"를 시계(2026-10-15)에 맞춰 11/14로 옮겼다(docs/02 "날짜와 회차"), 페스티벌 "5/3 · 5/4 · 5/5"는 11/20 · 11/21 ·
 * 11/22로, 전시 "10:00~18:00"(docs/02 "회차의 시간 정보"), 출연 "김배우 · 박배우"(docs/02), "+10,000원 · -20%"(docs/05). 회차가 생기고 지워지면
 * 재고에 알린다(#54).
 */
@DisplayName("일정 저장 · 조회 유스케이스 (위저드 2단계)")
class ScheduleServicesTest {

    private static final Instant LATER = NOW.plus(Duration.ofHours(2));
    private static final LocalDate NOVEMBER_14 = LocalDate.of(2026, 11, 14);
    private static final LocalDate NOVEMBER_15 = LocalDate.of(2026, 11, 15);
    private static final LocalDate NOVEMBER_20 = LocalDate.of(2026, 11, 20);
    private static final LocalDate NOVEMBER_21 = LocalDate.of(2026, 11, 21);
    private static final LocalDate NOVEMBER_22 = LocalDate.of(2026, 11, 22);
    private static final OperatingHours TEN_TO_SIX = OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(18, 0));
    private static final OperatingHours TEN_TO_EIGHT = OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(20, 0));

    private final EventWizardScene scene = new EventWizardScene();
    private final long springPlay = scene.createEventService()
            .create(new CreateEventCommand(acting(JUNGHOON, scene.twelveMonths), springPlayItems())).eventId();

    private EventScheduleResult chooseTimed() {
        return scene.chooseSessionModeService().choose(
                new ChooseSessionModeCommand(acting(PLANNER, scene.twelveMonths), springPlay, SessionMode.TIMED, null));
    }

    private EventScheduleResult chooseAllDay(OperatingHours defaultHours) {
        return scene.chooseSessionModeService().choose(
                new ChooseSessionModeCommand(acting(PLANNER, scene.twelveMonths), springPlay, SessionMode.ALL_DAY, defaultHours));
    }

    private EventScheduleResult addSession(LocalDate date, int startHour, Integer endHour) {
        return scene.addTimedSessionService().add(new AddTimedSessionCommand(acting(PLANNER, scene.twelveMonths), springPlay, date,
                TimedSessionTime.of(LocalTime.of(startHour, 0), endHour == null ? null : LocalTime.of(endHour, 0))));
    }

    private EventScheduleResult addDate(LocalDate date) {
        return scene.addAllDayDateService().add(new AddAllDayDateCommand(acting(PLANNER, scene.twelveMonths), springPlay, date));
    }

    private EventScheduleResult schedule() {
        return scene.getEventScheduleService().get(new GetEventScheduleQuery(acting(JUNGHOON, scene.twelveMonths), springPlay));
    }

    private static long sessionIdAt(EventScheduleResult schedule, LocalDate date, int startHour) {
        return dateOf(schedule, date).sessions().stream()
                .filter(session -> session.startTime() == null || session.startTime().equals(LocalTime.of(startHour, 0)))
                .findFirst()
                .orElseThrow()
                .sessionId();
    }

    private static ScheduleDateResult dateOf(EventScheduleResult schedule, LocalDate date) {
        return schedule.dates().stream().filter(candidate -> candidate.date().equals(date)).findFirst().orElseThrow();
    }

    private Event storedEvent() {
        return scene.eventRepository.findById(springPlay).orElseThrow();
    }

    private List<String> summaries() {
        return scene.recordRepository.records().stream().map(record -> record.summary()).toList();
    }

    private static void assertRejected(ThrowingCallable action, EventErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .satisfies(rejected -> assertThat(rejected.errorCode()).isEqualTo(expectedCode));
    }

    @Test
    @DisplayName("회차 방식을 고르기 전에는 빈 일정이고, 회차형을 고르면 일정이 생기며 진행 단계가 2단계가 되고 조작 기록이 남는다")
    void choosesTimed() {
        assertThat(schedule()).isEqualTo(EventScheduleResult.notChosen(springPlay));
        scene.clock.moveTo(LATER);

        EventScheduleResult chosen = chooseTimed();

        assertThat(chosen).isEqualTo(new EventScheduleResult(springPlay, SessionMode.TIMED, null, List.of()));
        assertThat(schedule()).isEqualTo(chosen);
        assertThat(storedEvent().wizardProgress()).isEqualTo(EventWizardStep.SCHEDULE);
        assertThat(storedEvent().lastModifiedAt()).isEqualTo(LATER);
        assertThat(scene.recordRepository.records()).last().satisfies(record -> {
            assertThat(record.action()).isEqualTo(StaffAction.EDIT_EVENT);
            assertThat(record.staffAccountId()).isEqualTo(PLANNER);
            assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.EVENT);
            assertThat(record.targetId()).isEqualTo(springPlay);
            assertThat(record.summary()).isEqualTo("회차 방식 · 회차형 (2단계)");
            assertThat(record.operatedAt()).isEqualTo(LATER);
        });
        assertThat(scene.eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("같은 회차 방식을 다시 보내면 저장도 기록도 없다 — 종일형의 기본 운영 시간이 바뀌면 저장하고 기록한다")
    void sameModeChangesNothing() {
        chooseAllDay(TEN_TO_SIX);
        int savesBefore = scene.scheduleRepository.saves();
        int eventSavesBefore = scene.eventRepository.saves();
        int recordsBefore = scene.recordRepository.records().size();

        chooseAllDay(TEN_TO_SIX);

        assertThat(scene.scheduleRepository.saves()).isEqualTo(savesBefore);
        assertThat(scene.eventRepository.saves()).isEqualTo(eventSavesBefore);
        assertThat(scene.recordRepository.records()).hasSize(recordsBefore);

        EventScheduleResult changed = chooseAllDay(OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(20, 0), LocalTime.of(19, 0)));

        assertThat(changed.defaultOperatingHours()).isEqualTo(new OperatingHoursResult(LocalTime.of(10, 0), LocalTime.of(20, 0),
                LocalTime.of(19, 0)));
        assertThat(summaries()).last().isEqualTo("회차 방식 · 종일형 · 기본 운영 시간 10:00~20:00 (마지막 입장 19:00) (2단계)");
    }

    @Test
    @DisplayName("회차형 11/14 15:00 · 19:00 회차를 더하면 회차마다 재고에 알리고 조작 기록이 남는다 (docs/02 예시)")
    void addsTimedSessions() {
        chooseTimed();

        addSession(NOVEMBER_14, 19, null);
        EventScheduleResult result = addSession(NOVEMBER_14, 15, 17);

        long matinee = sessionIdAt(result, NOVEMBER_14, 15);
        long evening = sessionIdAt(result, NOVEMBER_14, 19);
        assertThat(result.dates()).singleElement().satisfies(date -> {
            assertThat(date.date()).isEqualTo(NOVEMBER_14);
            assertThat(date.operatingHours()).isNull();
            assertThat(date.sessions()).containsExactly(
                    new ScheduleSessionResult(matinee, LocalTime.of(15, 0), LocalTime.of(17, 0), null,
                            new SessionPriceAdjustmentResult(SessionPriceAdjustmentKind.NONE, null, null), false),
                    new ScheduleSessionResult(evening, LocalTime.of(19, 0), null, null,
                            new SessionPriceAdjustmentResult(SessionPriceAdjustmentKind.NONE, null, null), false));
        });
        assertThat(scene.eventPublisher.published())
                .containsExactly(new SessionAdded(springPlay, evening), new SessionAdded(springPlay, matinee));
        assertThat(summaries()).endsWith("회차 추가 · 2026-11-14 19:00 (2단계)", "회차 추가 · 2026-11-14 15:00 (2단계)");
        assertThat(schedule()).isEqualTo(result);
    }

    @Test
    @DisplayName("종일형 11/20 · 11/21 · 11/22를 더하면 날짜마다 회차가 생기고, 11/21만 덮어쓴 운영 시간이 조회에 보인다")
    void addsAllDayDatesAndOverridesHours() {
        chooseAllDay(TEN_TO_SIX);
        List.of(NOVEMBER_20, NOVEMBER_21, NOVEMBER_22).forEach(this::addDate);

        EventScheduleResult result = scene.changeDateOperatingHoursService().change(
                new ChangeDateOperatingHoursCommand(acting(PLANNER, scene.twelveMonths), springPlay, NOVEMBER_21, TEN_TO_EIGHT));

        OperatingHoursResult tenToSix = new OperatingHoursResult(LocalTime.of(10, 0), LocalTime.of(18, 0), null);
        OperatingHoursResult tenToEight = new OperatingHoursResult(LocalTime.of(10, 0), LocalTime.of(20, 0), null);
        assertThat(result.dates()).extracting(ScheduleDateResult::date).containsExactly(NOVEMBER_20, NOVEMBER_21, NOVEMBER_22);
        assertThat(dateOf(result, NOVEMBER_20).operatingHours()).isEqualTo(tenToSix);
        assertThat(dateOf(result, NOVEMBER_20).operatingHoursOverride()).isNull();
        assertThat(dateOf(result, NOVEMBER_21).operatingHours()).isEqualTo(tenToEight);
        assertThat(dateOf(result, NOVEMBER_21).operatingHoursOverride()).isEqualTo(tenToEight);
        assertThat(result.dates()).allSatisfy(date -> assertThat(date.sessions()).singleElement()
                .satisfies(session -> assertThat(session.startTime()).isNull()));
        assertThat(scene.eventPublisher.published()).hasSize(3).allSatisfy(notice -> assertThat(notice).isInstanceOf(SessionAdded.class));
        assertThat(summaries()).last().isEqualTo("운영 시간 덮어쓰기 · 2026-11-21 10:00~20:00 (2단계)");

        scene.changeDateOperatingHoursService().change(
                new ChangeDateOperatingHoursCommand(acting(PLANNER, scene.twelveMonths), springPlay, NOVEMBER_21, null));

        assertThat(dateOf(schedule(), NOVEMBER_21).operatingHours()).isEqualTo(tenToSix);
        assertThat(summaries()).last().isEqualTo("운영 시간 기본값으로 · 2026-11-21 (2단계)");
    }

    @Test
    @DisplayName("종일형 날짜에 회차를 더하거나 회차형에 날짜만 더하면 막는다 — 저장 · 알림 · 기록이 없다")
    void modeMismatchRejected() {
        chooseAllDay(TEN_TO_SIX);
        addDate(NOVEMBER_20);
        int savesBefore = scene.scheduleRepository.saves();
        int recordsBefore = scene.recordRepository.records().size();

        assertRejected(() -> addSession(NOVEMBER_20, 10, 12), EventErrorCode.ALL_DAY_SINGLE_SESSION_PER_DATE);
        assertRejected(() -> scene.changeSessionDateTimeService().change(new ChangeSessionDateTimeCommand(
                acting(PLANNER, scene.twelveMonths), springPlay, sessionIdAt(schedule(), NOVEMBER_20, 0), NOVEMBER_20,
                LocalTime.of(10, 0), null)), EventErrorCode.ALL_DAY_SINGLE_SESSION_PER_DATE);

        assertThat(scene.scheduleRepository.saves()).isEqualTo(savesBefore);
        assertThat(scene.eventPublisher.published()).hasSize(1);
        assertThat(scene.recordRepository.records()).hasSize(recordsBefore);
    }

    @Test
    @DisplayName("회차 방식을 고르기 전에 회차 · 날짜 · 운영 시간을 넣으면 막는다 (SESSION_MODE_NOT_CHOSEN) — 일정이 생기지 않는다")
    void modeMustBeChosenFirst() {
        assertRejected(() -> addSession(NOVEMBER_14, 19, 21), EventErrorCode.SESSION_MODE_NOT_CHOSEN);
        assertRejected(() -> addDate(NOVEMBER_20), EventErrorCode.SESSION_MODE_NOT_CHOSEN);
        assertRejected(() -> scene.changeDateOperatingHoursService().change(new ChangeDateOperatingHoursCommand(
                acting(PLANNER, scene.twelveMonths), springPlay, NOVEMBER_20, TEN_TO_EIGHT)), EventErrorCode.SESSION_MODE_NOT_CHOSEN);

        assertThat(scene.scheduleRepository.findByEventId(springPlay)).isEmpty();
        assertThat(storedEvent().wizardProgress()).isEqualTo(EventWizardStep.BASIC_INFORMATION);
    }

    @Test
    @DisplayName("회차를 다른 날짜로 옮기면 같은 회차라 재고에 알리지 않고, 일시 변경 기록이 남는다 — 같은 일시를 다시 보내면 기록이 없다")
    void changesSessionDateTime() {
        chooseTimed();
        long evening = sessionIdAt(addSession(NOVEMBER_14, 19, 21), NOVEMBER_14, 19);
        int publishedBefore = scene.eventPublisher.published().size();
        ChangeSessionDateTimeCommand moveToNovember15 = new ChangeSessionDateTimeCommand(acting(PLANNER, scene.twelveMonths),
                springPlay, evening, NOVEMBER_15, LocalTime.of(20, 0), LocalTime.of(22, 0));

        EventScheduleResult moved = scene.changeSessionDateTimeService().change(moveToNovember15);

        assertThat(moved.dates()).singleElement().satisfies(date -> {
            assertThat(date.date()).isEqualTo(NOVEMBER_15);
            assertThat(date.sessions()).extracting(ScheduleSessionResult::sessionId).containsExactly(evening);
        });
        assertThat(scene.scheduleRepository.removedSessions()).isEmpty();
        assertThat(scene.scheduleRepository.removedDates()).hasSize(1);
        assertThat(scene.eventPublisher.published()).hasSize(publishedBefore);
        assertThat(summaries()).last().isEqualTo("회차 일시 변경 · 2026-11-14 19:00 → 2026-11-15 20:00 (2단계)");
        int recordsBefore = scene.recordRepository.records().size();

        scene.changeSessionDateTimeService().change(moveToNovember15);

        assertThat(scene.recordRepository.records()).hasSize(recordsBefore);
    }

    @Test
    @DisplayName("판매 중 — 시작 1주일 전이 지난 회차의 일시는 바꿀 수 없고, 열흘 넘게 남은 회차는 바꾼다")
    void sessionTimeLockedWithinAWeekAfterBookingOpened() {
        chooseTimed();
        long soon = sessionIdAt(addSession(LocalDate.of(2026, 10, 20), 19, 21), LocalDate.of(2026, 10, 20), 19);
        long later = sessionIdAt(addSession(NOVEMBER_14, 19, 21), NOVEMBER_14, 19);
        scene.moveEventTo(springPlay, EventStatus.ON_SALE);

        assertRejected(() -> scene.changeSessionDateTimeService().change(new ChangeSessionDateTimeCommand(
                acting(PLANNER, scene.twelveMonths), springPlay, soon, LocalDate.of(2026, 10, 20), LocalTime.of(18, 0), null)),
                EventErrorCode.SESSION_TIME_EDIT_LOCKED);
        EventScheduleResult changed = scene.changeSessionDateTimeService().change(new ChangeSessionDateTimeCommand(
                acting(PLANNER, scene.twelveMonths), springPlay, later, NOVEMBER_14, LocalTime.of(18, 0), LocalTime.of(20, 0)));

        assertThat(dateOf(changed, NOVEMBER_14).sessions()).extracting(ScheduleSessionResult::startTime)
                .containsExactly(LocalTime.of(18, 0));
    }

    @Test
    @DisplayName("출연 '김배우 · 박배우'와 가격 조정 +10,000원 · -20%를 저장하면 조회에 보이고 기록이 남는다 — 같은 값이면 기록이 없다")
    void changesPerformersAndPriceAdjustment() {
        chooseTimed();
        addSession(NOVEMBER_14, 15, 17);
        EventScheduleResult added = addSession(NOVEMBER_14, 19, 21);
        long matinee = sessionIdAt(added, NOVEMBER_14, 15);
        long evening = sessionIdAt(added, NOVEMBER_14, 19);

        scene.changeSessionPerformersService().change(new ChangeSessionPerformersCommand(acting(PLANNER, scene.twelveMonths),
                springPlay, evening, new SessionPerformers("김배우 · 박배우")));
        scene.changeSessionPriceAdjustmentService().change(new ChangeSessionPriceAdjustmentCommand(
                acting(PLANNER, scene.twelveMonths), springPlay, evening, SessionPriceAdjustment.amount(10_000)));
        EventScheduleResult result = scene.changeSessionPriceAdjustmentService().change(new ChangeSessionPriceAdjustmentCommand(
                acting(PLANNER, scene.twelveMonths), springPlay, matinee, SessionPriceAdjustment.percent(-20)));

        List<ScheduleSessionResult> sessions = dateOf(result, NOVEMBER_14).sessions();
        assertThat(sessions.get(0).priceAdjustment())
                .isEqualTo(new SessionPriceAdjustmentResult(SessionPriceAdjustmentKind.PERCENT, null, -20));
        assertThat(sessions.get(1).performers()).isEqualTo("김배우 · 박배우");
        assertThat(sessions.get(1).priceAdjustment())
                .isEqualTo(new SessionPriceAdjustmentResult(SessionPriceAdjustmentKind.AMOUNT, 10_000L, null));
        assertThat(summaries()).endsWith("출연 변경 · 2026-11-14 19:00 (2단계)", "가격 조정 변경 · 2026-11-14 19:00 · +10,000원 (2단계)",
                "가격 조정 변경 · 2026-11-14 15:00 · -20% (2단계)");
        int recordsBefore = scene.recordRepository.records().size();

        scene.changeSessionPerformersService().change(new ChangeSessionPerformersCommand(acting(PLANNER, scene.twelveMonths),
                springPlay, evening, new SessionPerformers("김배우 · 박배우")));
        scene.changeSessionPriceAdjustmentService().change(new ChangeSessionPriceAdjustmentCommand(
                acting(PLANNER, scene.twelveMonths), springPlay, matinee, SessionPriceAdjustment.percent(-20)));

        assertThat(scene.recordRepository.records()).hasSize(recordsBefore);
    }

    @Test
    @DisplayName("회차를 지우면 지운 시각과 함께 남고 재고에 알린다 — 날짜를 지우면 그 아래 회차마다 알린다")
    void removesSessionsAndDates() {
        chooseTimed();
        addSession(NOVEMBER_14, 15, 17);
        addSession(NOVEMBER_14, 19, 21);
        EventScheduleResult added = addSession(NOVEMBER_15, 19, 21);
        long matinee = sessionIdAt(added, NOVEMBER_14, 15);
        long evening = sessionIdAt(added, NOVEMBER_14, 19);
        long november15 = sessionIdAt(added, NOVEMBER_15, 19);
        scene.clock.moveTo(LATER);

        scene.removeSessionService().remove(new RemoveSessionCommand(acting(PLANNER, scene.twelveMonths), springPlay, november15));

        assertThat(scene.scheduleRepository.removedSessions()).containsEntry(november15, LATER);
        assertThat(scene.eventPublisher.published()).last().isEqualTo(new SessionRemoved(springPlay, november15));
        assertThat(summaries()).last().isEqualTo("회차 삭제 · 2026-11-15 19:00 (2단계)");

        scene.removeScheduleDateService().remove(new RemoveScheduleDateCommand(acting(PLANNER, scene.twelveMonths), springPlay,
                NOVEMBER_14));

        assertThat(scene.eventPublisher.published()).endsWith(new SessionRemoved(springPlay, Math.min(matinee, evening)),
                new SessionRemoved(springPlay, Math.max(matinee, evening)));
        assertThat(summaries()).last().isEqualTo("날짜 삭제 · 2026-11-14 (2단계)");
        assertThat(schedule().dates()).isEmpty();
        assertThat(scene.scheduleRepository.removedDates()).hasSize(2);
    }

    @Test
    @DisplayName("일정에 없는 회차 · 날짜는 찾을 수 없다")
    void unknownSessionOrDate() {
        chooseTimed();

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> scene.removeSessionService().remove(new RemoveSessionCommand(acting(PLANNER, scene.twelveMonths),
                        springPlay, 999)))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.SESSION_NOT_FOUND));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> scene.removeScheduleDateService().remove(new RemoveScheduleDateCommand(
                        acting(PLANNER, scene.twelveMonths), springPlay, NOVEMBER_14)))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.SCHEDULE_DATE_NOT_FOUND));
    }

    @Test
    @DisplayName("판매가 끝난 행사는 회차를 더하거나 지울 수 없다")
    void lockedAfterSalesClosed() {
        chooseTimed();
        long evening = sessionIdAt(addSession(NOVEMBER_14, 19, 21), NOVEMBER_14, 19);
        scene.moveEventTo(springPlay, EventStatus.SALES_CLOSED);

        assertRejected(() -> addSession(NOVEMBER_15, 19, 21), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
        assertRejected(() -> scene.removeSessionService().remove(new RemoveSessionCommand(acting(PLANNER, scene.twelveMonths),
                springPlay, evening)), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
        assertThat(schedule().dates()).hasSize(1);
    }

    @Test
    @DisplayName("다른 주최자의 운영진은 403, 자기 주최자 경로로 열두달 행사에 닿으면 404다 — 조회도 같다")
    void outsidersCannotReachSchedule() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> scene.chooseSessionModeService().choose(new ChooseSessionModeCommand(
                        acting(DOHYUN, scene.twelveMonths), springPlay, SessionMode.TIMED, null)))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> scene.chooseSessionModeService().choose(new ChooseSessionModeCommand(
                        acting(YEOREUM, scene.lateSummer), springPlay, SessionMode.TIMED, null)))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> scene.getEventScheduleService().get(new GetEventScheduleQuery(acting(YEOREUM, scene.lateSummer),
                        springPlay)))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
        assertThat(scene.scheduleRepository.saves()).isZero();
    }

    @Test
    @DisplayName("잠금 순서 — 계정 줄 → 주최자 줄(외래 키 확인 잠금) → 행사 줄(FOR UPDATE), 일정은 행사 줄을 잡은 뒤 읽는다 (#195)")
    void locksAccountOrganizerThenEvent() {
        chooseTimed();
        int locksBefore = scene.rowLockLog.locks().size();

        addSession(NOVEMBER_14, 19, 21);

        assertThat(scene.rowLockLog.locks().subList(locksBefore, scene.rowLockLog.locks().size())).containsExactly(
                "계정 " + PLANNER + " FOR KEY SHARE", "주최자 " + scene.twelveMonths + " FOR KEY SHARE",
                "행사 " + springPlay + " FOR UPDATE");
    }

    @Test
    @DisplayName("조작 기록 요약의 가격 조정 표기 — 조정 없음 · 빼는 금액")
    void priceAdjustmentLabels() {
        assertThat(ChangeSessionPriceAdjustmentService.operationSummary("2026-11-14 19:00", SessionPriceAdjustment.none()))
                .isEqualTo("가격 조정 변경 · 2026-11-14 19:00 · 조정 없음 (2단계)");
        assertThat(ChangeSessionPriceAdjustmentService.operationSummary("2026-11-14 19:00", SessionPriceAdjustment.amount(-5_000)))
                .isEqualTo("가격 조정 변경 · 2026-11-14 19:00 · -5,000원 (2단계)");
    }
}
