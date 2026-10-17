package com.ticketon.application.event.service;

import static com.ticketon.application.event.service.EventWizardScene.DOHYUN;
import static com.ticketon.application.event.service.EventWizardScene.JUNGHOON;
import static com.ticketon.application.event.service.EventWizardScene.PLANNER;
import static com.ticketon.application.event.service.EventWizardScene.SPRING_FORMER_OWNER;
import static com.ticketon.application.event.service.EventWizardScene.acting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.groups.Tuple.tuple;

import com.ticketon.application.event.port.in.GetOrganizerEventListQuery;
import com.ticketon.application.event.port.in.OrganizerEventListResult;
import com.ticketon.application.event.port.in.OrganizerEventListResult.DraftEvent;
import com.ticketon.application.event.port.in.OrganizerEventListResult.ListedEvent;
import com.ticketon.application.event.query.OrganizerEventSummary;
import com.ticketon.common.ImageFormat;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.event.EventPoster;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 내 행사 목록 — 세 구역으로 나누고(docs/13 "3. 주최 관리 — 내 행사 목록"), 작성 중 행사에 진행률 · 마지막 수정 · 이어서 할 단계 · 접어둠을 붙인다
 * (docs/04 "작성 중 행사 관리"). 시계는 2026-10-15 10:00(한국 시각)에 멈춰 있다({@link EventWizardScene}).
 */
@DisplayName("내 행사 목록 조회 유스케이스")
class GetOrganizerEventListServiceTest {

    private static final EventPoster POSTER = EventPoster.issue("Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL", ImageFormat.JPEG);

    private final EventWizardScene scene = new EventWizardScene();
    private final Map<Long, List<OrganizerEventSummary>> eventsByOrganizer = Map.of(
            scene.twelveMonths, twelveMonthsEvents(),
            scene.lateSummer, List.of(summary(90, "늦여름 페스티벌 2026", EventStatus.DRAFT, EventWizardStep.SCHEDULE,
                    at(2026, 10, 1), null, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22))));
    private final List<Long> askedOrganizers = new ArrayList<>();
    private final GetOrganizerEventListService service = new GetOrganizerEventListService(scene.checkStaffPermissionService,
            organizerId -> {
                askedOrganizers.add(organizerId);
                return eventsByOrganizer.getOrDefault(organizerId, List.of());
            }, scene.posterStorage, scene.clock);

    /** 극단 열두달의 행사 — 상태가 고루 섞였다. 차례는 저장소가 주는 id 차례. */
    private static List<OrganizerEventSummary> twelveMonthsEvents() {
        return List.of(
                summary(1, "2025 겨울 정기공연", EventStatus.ENDED, EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS, at(2025, 11, 1), POSTER,
                        LocalDate.of(2025, 12, 20), LocalDate.of(2025, 12, 21)),
                summary(2, "○○밴드 단독공연", EventStatus.DRAFT, EventWizardStep.PRICING, at(2026, 10, 12), POSTER,
                        LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 24)),
                summary(3, "2026 봄 정기공연 〈열두 번째 달〉", EventStatus.SETTLED, EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS,
                        at(2026, 2, 1), null, LocalDate.of(2026, 3, 14), LocalDate.of(2026, 3, 15)),
                summary(4, "가을 낭독회", EventStatus.ON_SALE, EventWizardStep.BOOKING_SETTINGS, at(2026, 9, 1), null,
                        LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8)),
                summary(5, "여름 워크숍 공연", EventStatus.DRAFT, EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS, at(2026, 6, 1), null,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 14)),
                summary(6, "연말 콘서트", EventStatus.AWAITING_OPEN, EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS, at(2026, 10, 1),
                        null, LocalDate.of(2026, 12, 30), LocalDate.of(2026, 12, 31)),
                summary(7, "새 행사", EventStatus.DRAFT, EventWizardStep.BASIC_INFORMATION, at(2026, 10, 15), null, null, null),
                summary(8, "취소된 앵콜공연", EventStatus.CANCELLED, EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS, at(2026, 4, 1), null,
                        LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 2)),
                summary(9, "가을 정기공연", EventStatus.SALES_PAUSED, EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS, at(2026, 9, 20),
                        null, LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25)),
                summary(10, "오늘 공연", EventStatus.SALES_CLOSED, EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS, at(2026, 9, 1), null,
                        LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15)));
    }

    @Test
    @DisplayName("작성 중 / 진행 중(판매 대기 · 판매 중 · 판매 중단 · 판매 종료) / 지난 행사(행사 종료 · 정산 완료 · 취소됨)로 나눈다")
    void splitsIntoThreeSections() {
        OrganizerEventListResult result = service.get(new GetOrganizerEventListQuery(acting(JUNGHOON, scene.twelveMonths)));

        assertThat(result.drafts()).extracting(DraftEvent::eventId).containsExactlyInAnyOrder(2L, 5L, 7L);
        assertThat(result.inProgress()).extracting(ListedEvent::eventId).containsExactlyInAnyOrder(4L, 6L, 9L, 10L);
        assertThat(result.past()).extracting(ListedEvent::eventId).containsExactlyInAnyOrder(1L, 3L, 8L);
        assertThat(askedOrganizers).containsExactly(scene.twelveMonths);
    }

    @Test
    @DisplayName("작성 중은 마지막 수정이 최근인 것부터, 진행 중은 첫 날짜가 이른 것부터, 지난 행사는 마지막 날짜가 늦은 것부터")
    void ordersEachSection() {
        OrganizerEventListResult result = service.get(new GetOrganizerEventListQuery(acting(JUNGHOON, scene.twelveMonths)));

        assertThat(result.drafts()).extracting(DraftEvent::eventId).containsExactly(7L, 2L, 5L);
        assertThat(result.inProgress()).extracting(ListedEvent::eventId).containsExactly(10L, 9L, 4L, 6L);
        assertThat(result.past()).extracting(ListedEvent::eventId).containsExactly(8L, 3L, 1L);
    }

    @Test
    @DisplayName("'4/7 단계 · 가격 설정까지 완료'인 ○○밴드 단독공연은 이어서 5단계를 열고, 마지막 수정 시각 · 날짜 · 포스터 주소가 붙는다")
    void draftProgress() {
        OrganizerEventListResult result = service.get(new GetOrganizerEventListQuery(acting(JUNGHOON, scene.twelveMonths)));

        assertThat(result.drafts()).filteredOn(draft -> draft.eventId() == 2L).singleElement()
                .isEqualTo(new DraftEvent(2L, "○○밴드 단독공연", scene.posterStorage.publicUrlOf(POSTER), EventWizardStep.PRICING,
                        EventWizardStep.BOOKING_SETTINGS, at(2026, 10, 12), LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 24),
                        false));
    }

    @Test
    @DisplayName("날짜가 모두 지난 작성 중 행사(마지막 날짜 10/14)는 접어 두고, 날짜가 남았거나 없으면 접지 않는다")
    void foldsDraftsWhoseDatesPassed() {
        OrganizerEventListResult result = service.get(new GetOrganizerEventListQuery(acting(JUNGHOON, scene.twelveMonths)));

        assertThat(result.drafts()).extracting(DraftEvent::eventId, DraftEvent::folded)
                .containsExactly(
                        tuple(7L, false),
                        tuple(2L, false),
                        tuple(5L, true));
    }

    @Test
    @DisplayName("진행 중 · 지난 행사는 상태 · 날짜 · 포스터 주소와 함께 나온다")
    void listedEvents() {
        OrganizerEventListResult result = service.get(new GetOrganizerEventListQuery(acting(JUNGHOON, scene.twelveMonths)));

        assertThat(result.inProgress()).filteredOn(event -> event.eventId() == 9L).singleElement()
                .isEqualTo(new ListedEvent(9L, "가을 정기공연", null, EventStatus.SALES_PAUSED, LocalDate.of(2026, 10, 24),
                        LocalDate.of(2026, 10, 25)));
        assertThat(result.past()).filteredOn(event -> event.eventId() == 1L).singleElement()
                .isEqualTo(new ListedEvent(1L, "2025 겨울 정기공연", scene.posterStorage.publicUrlOf(POSTER), EventStatus.ENDED,
                        LocalDate.of(2025, 12, 20), LocalDate.of(2025, 12, 21)));
    }

    @Test
    @DisplayName("운영자도 본다 — 잠그지도 기록하지도 않는다")
    void operatorCanView() {
        int locksBefore = scene.rowLockLog.locks().size();

        OrganizerEventListResult result = service.get(new GetOrganizerEventListQuery(acting(PLANNER, scene.twelveMonths)));

        assertThat(result.drafts()).hasSize(3);
        assertThat(scene.rowLockLog.locks()).hasSize(locksBefore);
        assertThat(scene.recordRepository.records()).isEmpty();
    }

    @Test
    @DisplayName("다른 주최자의 운영진이 열두달 경로로 오면 403이고 목록을 읽지 않는다 — 닫힌 주최자도 403")
    void outsidersAreDenied() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> service.get(new GetOrganizerEventListQuery(acting(DOHYUN, scene.twelveMonths))))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> service.get(new GetOrganizerEventListQuery(acting(SPRING_FORMER_OWNER, scene.closedSpring))))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.ORGANIZER_NOT_ACTIVE));
        assertThat(askedOrganizers).isEmpty();
    }

    @Test
    @DisplayName("자기 주최자 경로로 보면 자기 주최자의 행사만 나온다")
    void ownOrganizerOnly() {
        OrganizerEventListResult result = service.get(new GetOrganizerEventListQuery(acting(DOHYUN, scene.lateSummer)));

        assertThat(result.drafts()).extracting(DraftEvent::eventId).containsExactly(90L);
        assertThat(result.drafts().getFirst().folded()).isTrue();
        assertThat(result.inProgress()).isEmpty();
        assertThat(result.past()).isEmpty();
        assertThat(askedOrganizers).containsExactly(scene.lateSummer);
    }

    private static OrganizerEventSummary summary(long eventId, String title, EventStatus status, EventWizardStep wizardProgress,
            Instant lastModifiedAt, EventPoster poster, LocalDate firstDate, LocalDate lastDate) {
        return new OrganizerEventSummary(eventId, title, status, wizardProgress, lastModifiedAt, poster, firstDate, lastDate);
    }

    private static Instant at(int year, int month, int day) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, 9, 0));
    }
}
