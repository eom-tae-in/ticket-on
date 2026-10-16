package com.ticketon.application.event.service;

import static com.ticketon.application.event.service.EventWizardScene.DOHYUN;
import static com.ticketon.application.event.service.EventWizardScene.JUNGHOON;
import static com.ticketon.application.event.service.EventWizardScene.PLANNER;
import static com.ticketon.application.event.service.EventWizardScene.YEOREUM;
import static com.ticketon.application.event.service.EventWizardScene.acting;
import static com.ticketon.application.event.service.EventWizardScene.encoreItems;
import static com.ticketon.application.event.service.EventWizardScene.springPlayItems;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.EventCategoriesResult;
import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.application.event.port.in.GetEventWizardQuery;
import com.ticketon.application.event.port.in.SaveEventBasicInformationCommand;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.event.EventCategory;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 나갔다 들어오면 이어서 한다 — 저장된 단계의 값과 진행 단계를 다시 읽는다(docs/03 "임시저장은 자동으로", {@link EventWizardScene}). 1단계의 카테고리
 * 목록도 읽는다.
 */
@DisplayName("행사 위저드 값 조회 · 정식 카테고리 목록 조회 유스케이스")
class GetEventWizardServiceTest {

    private final EventWizardScene scene = new EventWizardScene();
    private final GetEventWizardService service = scene.getEventWizardService();
    private final long springPlay = scene.createEventService()
            .create(new CreateEventCommand(acting(JUNGHOON, scene.twelveMonths), springPlayItems())).eventId();

    @Test
    @DisplayName("1단계를 고쳐 저장한 뒤 다른 운영진이 다시 읽으면 고친 값과 진행 단계로 이어서 한다 — 잠그지도 기록하지도 않는다")
    void resumesWithSavedValues() {
        scene.saveEventBasicInformationService().save(
                new SaveEventBasicInformationCommand(acting(JUNGHOON, scene.twelveMonths), springPlay, encoreItems()));
        int locksBefore = scene.rowLockLog.locks().size();

        EventWizardResult result = service.get(new GetEventWizardQuery(acting(PLANNER, scene.twelveMonths), springPlay));

        assertThat(result.eventId()).isEqualTo(springPlay);
        assertThat(result.basicInformation().title()).isEqualTo("2026 봄 앵콜공연 〈열두 번째 달〉");
        assertThat(result.wizardProgress()).isEqualTo(EventWizardStep.BASIC_INFORMATION);
        assertThat(result.bookingStep().maxSeatsPerSelection()).isEqualTo(4);
        assertThat(scene.rowLockLog.locks()).hasSize(locksBefore);
        assertThat(scene.recordRepository.records()).hasSize(2);
    }

    @Test
    @DisplayName("다른 주최자의 운영진은 403, 자기 주최자 경로로 열두달 행사에 닿거나 없는 행사면 404다")
    void outsidersAndMissingEvents() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> service.get(new GetEventWizardQuery(acting(DOHYUN, scene.twelveMonths), springPlay)))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.get(new GetEventWizardQuery(acting(YEOREUM, scene.lateSummer), springPlay)))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.get(new GetEventWizardQuery(acting(JUNGHOON, scene.twelveMonths), springPlay + 1)));
    }

    @Test
    @DisplayName("정식 카테고리 목록을 유형 차례로 돌려주고 \"기타\"는 따로 이름만 준다 — 승격한 카테고리는 그 유형의 끝에")
    void categories() {
        scene.categoryRepository.promote(EventCategory.of(EventType.PERFORMANCE, "낭독극"));

        EventCategoriesResult result = scene.getEventCategoriesService().get();

        assertThat(result.otherCategoryName()).isEqualTo("기타");
        assertThat(result.types()).extracting(EventCategoriesResult.TypeCategories::type)
                .containsExactly(EventType.PERFORMANCE, EventType.EXHIBITION, EventType.FESTIVAL);
        assertThat(result.types().get(0).categoryNames())
                .containsExactly("연극", "뮤지컬", "콘서트", "클래식", "국악", "무용", "코미디", "낭독극");
        assertThat(result.types().get(1).categoryNames()).containsExactly("회화", "사진", "미디어아트", "설치", "공예");
        assertThat(result.types().get(2).categoryNames()).containsExactly("음악", "푸드", "문화", "지역축제");
    }
}
