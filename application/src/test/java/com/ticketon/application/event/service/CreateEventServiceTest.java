package com.ticketon.application.event.service;

import static com.ticketon.application.event.service.EventWizardScene.DOHYUN;
import static com.ticketon.application.event.service.EventWizardScene.FIRST_ADDRESS;
import static com.ticketon.application.event.service.EventWizardScene.JUNGHOON;
import static com.ticketon.application.event.service.EventWizardScene.NEW_SPRING_APPLICANT;
import static com.ticketon.application.event.service.EventWizardScene.NOW;
import static com.ticketon.application.event.service.EventWizardScene.PLANNER;
import static com.ticketon.application.event.service.EventWizardScene.SPRING_FORMER_OWNER;
import static com.ticketon.application.event.service.EventWizardScene.acting;
import static com.ticketon.application.event.service.EventWizardScene.springPlayItems;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.EventBasicInformationItems;
import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventCategory;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.EventVisibility;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.event.SessionChoiceRestriction;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달의 운영진이 위저드 1단계를 넘겨 행사를 만든다(docs/03 "임시저장은 자동으로", {@link EventWizardScene}). 빈 초안은 없고, 활동 중인
 * 주최자의 운영진 누구나 만든다(docs/01 "권한 두 가지").
 */
@DisplayName("행사 만들기 유스케이스")
class CreateEventServiceTest {

    private final EventWizardScene scene = new EventWizardScene();
    private final CreateEventService service = scene.createEventService();

    private EventWizardResult create(long staffAccountId, long organizerId, EventBasicInformationItems items) {
        return service.create(new CreateEventCommand(acting(staffAccountId, organizerId), items));
    }

    @Test
    @DisplayName("운영자도 만든다 — 1단계 기본 정보와 함께 작성 중으로 저장되고, 추측할 수 없는 공개 주소를 받으며, 진행 단계는 1단계다")
    void operatorCreates() {
        EventWizardResult result = create(PLANNER, scene.twelveMonths, springPlayItems());

        assertThat(result.eventId()).isPositive();
        assertThat(result.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(result.publicAddress()).isEqualTo(FIRST_ADDRESS);
        assertThat(result.wizardProgress()).isEqualTo(EventWizardStep.BASIC_INFORMATION);
        assertThat(result.lastModifiedAt()).isEqualTo(NOW);
        assertThat(result.basicInformation().title()).isEqualTo("2026 봄 정기공연 〈열두 번째 달〉");
        assertThat(result.basicInformation().type()).isEqualTo(EventType.PERFORMANCE);
        assertThat(result.basicInformation().categoryName()).isEqualTo("연극");
        assertThat(result.basicInformation().genreName()).isNull();
        assertThat(result.basicInformation().venueName()).isEqualTo("○○아트홀");
        assertThat(result.basicInformation().venueDetails()).isEqualTo("지하 1층, 주차 불가");
        assertThat(result.basicInformation().mapLink()).isEqualTo("https://map.example/place/12");
        assertThat(result.basicInformation().publicContact()).isEqualTo("twelve-months@example.com");
        assertThat(scene.eventRepository.findById(result.eventId()).orElseThrow().organizerId()).isEqualTo(scene.twelveMonths);
    }

    @Test
    @DisplayName("5단계는 처음 값으로 시작한다 — 공개, 예매 종료 · 검표 시작 1시간 전, 1회 4석, 1인 총 매수 · 연령 제한 없음, 재입장 끔, 기준표 취소 규정")
    void bookingStepStartsWithDefaults() {
        EventWizardResult.BookingStep bookingStep = create(JUNGHOON, scene.twelveMonths, springPlayItems()).bookingStep();

        assertThat(bookingStep.visibility()).isEqualTo(EventVisibility.PUBLIC);
        assertThat(bookingStep.passwordSet()).isFalse();
        assertThat(bookingStep.bookingOpensAt()).isNull();
        assertThat(bookingStep.bookingCloseLeadTime()).isEqualTo(Duration.ofHours(1));
        assertThat(bookingStep.checkinOpenLeadTime()).isEqualTo(Duration.ofHours(1));
        assertThat(bookingStep.maxSeatsPerSelection()).isEqualTo(4);
        assertThat(bookingStep.maxTicketsPerPerson()).isNull();
        assertThat(bookingStep.ticketsPerPersonBasis()).isNull();
        assertThat(bookingStep.sessionChoice()).isEqualTo(SessionChoiceRestriction.NONE);
        assertThat(bookingStep.reentryAllowed()).isFalse();
        assertThat(bookingStep.minimumAge()).isNull();
        assertThat(bookingStep.cancellationPolicyStandard()).isTrue();
        assertThat(bookingStep.cancellationFeeTiers()).containsExactly(
                new EventWizardResult.CancellationFeeTier(10, 0), new EventWizardResult.CancellationFeeTier(7, 10),
                new EventWizardResult.CancellationFeeTier(3, 20), new EventWizardResult.CancellationFeeTier(1, 30),
                new EventWizardResult.CancellationFeeTier(0, 90));
    }

    @Test
    @DisplayName("만든 운영진의 조작 기록이 행사를 대상으로 남는다")
    void recordsOperation() {
        EventWizardResult result = create(PLANNER, scene.twelveMonths, springPlayItems());

        assertThat(scene.recordRepository.records()).singleElement().satisfies(record -> {
            assertThat(record.organizerId()).isEqualTo(scene.twelveMonths);
            assertThat(record.staffAccountId()).isEqualTo(PLANNER);
            assertThat(record.staffRole()).isEqualTo(StaffRole.OPERATOR);
            assertThat(record.action()).isEqualTo(StaffAction.CREATE_EVENT);
            assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.EVENT);
            assertThat(record.targetId()).isEqualTo(result.eventId());
            assertThat(record.summary()).isEqualTo("행사 만들기");
            assertThat(record.operatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    @DisplayName("잠금 순서 — 만드는 운영진의 계정 줄 → 주최자 줄에 외래 키 확인 잠금을 건다 (#195)")
    void locksAccountThenOrganizer() {
        create(PLANNER, scene.twelveMonths, springPlayItems());

        assertThat(scene.rowLockLog.locks()).containsExactly("계정 " + PLANNER + " FOR KEY SHARE",
                "주최자 " + scene.twelveMonths + " FOR KEY SHARE");
        assertThat(scene.staffAccountRepository.lockedAccountIds()).isEmpty();
    }

    @Test
    @DisplayName("계정 줄을 기다리는 사이 내보내졌으면 잠근 뒤 다시 확인해 막는다 (NOT_ORGANIZER_STAFF) — 행사도 조작 기록도 없다")
    void removedWhileWaitingForLock() {
        scene.staffAccountRepository.runOnNextLock(() -> scene.staffRepository.saveEnded(
                scene.staffRepository.findByOrganizerId(scene.twelveMonths).removeOperator(JUNGHOON, PLANNER, NOW)));

        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> create(PLANNER, scene.twelveMonths, springPlayItems()))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
        assertThat(scene.eventRepository.findById(1)).isEmpty();
        assertThat(scene.recordRepository.records()).isEmpty();
    }

    @Test
    @DisplayName("다른 주최자의 운영진은 만들지 못하고, 아무 줄도 잠그지 않는다 (NOT_ORGANIZER_STAFF)")
    void outsiderIsRejected() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> create(DOHYUN, scene.twelveMonths, springPlayItems()))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
        assertThat(scene.rowLockLog.locks()).isEmpty();
        assertThat(scene.recordRepository.records()).isEmpty();
    }

    @Test
    @DisplayName("승인 전인 주최자에는 만들지 못한다 — 신청한 사람도 승인 전에는 소속되지 않았다 (NOT_ORGANIZER_STAFF)")
    void organizerUnderReviewIsRejected() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> create(NEW_SPRING_APPLICANT, scene.newSpring, springPlayItems()))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
    }

    @Test
    @DisplayName("활동 중이 아닌 주최자에는 소속 줄이 남아 있어도 만들지 못한다 (ORGANIZER_NOT_ACTIVE)")
    void inactiveOrganizerIsRejected() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> create(SPRING_FORMER_OWNER, scene.closedSpring, springPlayItems()))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.ORGANIZER_NOT_ACTIVE));
    }

    @Test
    @DisplayName("고른 유형의 목록에 없는 카테고리는 막고 아무것도 저장하지 않는다 — 공연에 \"회화\"")
    void categoryMustBelongToType() {
        EventBasicInformationItems items = springPlayItems();
        EventBasicInformationItems painting = new EventBasicInformationItems(items.title(), EventType.PERFORMANCE, "회화", null,
                items.introduction(), items.venue(), null, null, null);

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> create(JUNGHOON, scene.twelveMonths, painting))
                .satisfies(rejected -> assertThat(rejected.errorCode()).isEqualTo(EventErrorCode.EVENT_CATEGORY_NOT_IN_TYPE));
        assertThat(scene.eventRepository.findById(1)).isEmpty();
        assertThat(scene.recordRepository.records()).isEmpty();
    }

    @Test
    @DisplayName("'기타'를 고르면 장르명을 받고, 관리자가 승격한 카테고리는 정식 카테고리로 고른다 — 목록은 저장된 데이터다")
    void otherAndPromotedCategories() {
        EventBasicInformationItems items = springPlayItems();
        EventBasicInformationItems reading = new EventBasicInformationItems(items.title(), EventType.PERFORMANCE, "기타", "낭독극",
                items.introduction(), items.venue(), null, null, null);
        scene.categoryRepository.promote(EventCategory.of(EventType.PERFORMANCE, "마임"));
        EventBasicInformationItems mime = new EventBasicInformationItems(items.title(), EventType.PERFORMANCE, "마임", null,
                items.introduction(), items.venue(), null, null, null);

        EventWizardResult other = create(JUNGHOON, scene.twelveMonths, reading);
        EventWizardResult promoted = create(JUNGHOON, scene.twelveMonths, mime);

        assertThat(other.basicInformation().categoryName()).isEqualTo("기타");
        assertThat(other.basicInformation().genreName()).isEqualTo("낭독극");
        assertThat(promoted.basicInformation().categoryName()).isEqualTo("마임");
        assertThat(promoted.basicInformation().genreName()).isNull();
    }
}
