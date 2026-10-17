package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.GetOrganizerEventListQuery;
import com.ticketon.application.event.port.in.GetOrganizerEventListUseCase;
import com.ticketon.application.event.port.in.OrganizerEventListResult;
import com.ticketon.application.event.port.in.OrganizerEventListResult.DraftEvent;
import com.ticketon.application.event.port.in.OrganizerEventListResult.ListedEvent;
import com.ticketon.application.event.port.out.EventPosterStoragePort;
import com.ticketon.application.event.port.out.OrganizerEventListQueryPort;
import com.ticketon.application.event.query.OrganizerEventSummary;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.domain.event.DraftEventNudges;
import com.ticketon.domain.event.EventListSection;
import com.ticketon.domain.organizer.StaffAction;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 행사 목록 조회 — 그 주최자의 운영진인지({@link StaffAction#VIEW_EVENTS}, 운영진 누구나) 확인한 뒤, 읽기 전용이라 도메인 객체를 되살리지 않고
 * 조회 포트가 돌려준 줄을 구역({@link EventListSection})으로 나눠 옮긴다. 작성 중 행사의 이어서 할 단계와 접어둠은 도메인 규칙
 * ({@link com.ticketon.domain.event.EventWizardStep#next}, {@link DraftEventNudges#isFolded})으로 정한다. 보기만 하므로 잠그지 않고 조작 기록을
 * 남기지 않는다.
 * <p>
 * 행사와 일정 날짜는 조회 포트가 한 문장으로 읽는다 — 행사 줄을 잠그지 않지만 문장 하나는 스냅숏 하나로 읽으므로, 그 사이에 커밋된 일정 편집이 반쯤
 * 섞이지 않는다.
 */
@Service
public class GetOrganizerEventListService implements GetOrganizerEventListUseCase {

    /** 작성 중 — 마지막 수정이 최근인 것부터, 같으면 나중에 만든 것부터. */
    private static final Comparator<OrganizerEventSummary> DRAFT_ORDER =
            Comparator.comparing(OrganizerEventSummary::lastModifiedAt, Comparator.reverseOrder())
                    .thenComparing(OrganizerEventSummary::eventId, Comparator.reverseOrder());

    /** 진행 중 — 첫 날짜가 이른 것부터(날짜가 없으면 끝), 같으면 먼저 만든 것부터. */
    private static final Comparator<OrganizerEventSummary> IN_PROGRESS_ORDER =
            Comparator.comparing(OrganizerEventSummary::firstDate, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
                    .thenComparing(OrganizerEventSummary::eventId);

    /** 지난 행사 — 마지막 날짜가 늦은 것부터(날짜가 없으면 끝), 같으면 나중에 만든 것부터. */
    private static final Comparator<OrganizerEventSummary> PAST_ORDER =
            Comparator.comparing(OrganizerEventSummary::lastDate, Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()))
                    .thenComparing(OrganizerEventSummary::eventId, Comparator.reverseOrder());

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final OrganizerEventListQueryPort eventListQueryPort;
    private final EventPosterStoragePort posterStorage;
    private final Clock clock;

    public GetOrganizerEventListService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            OrganizerEventListQueryPort eventListQueryPort, EventPosterStoragePort posterStorage, Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.eventListQueryPort = eventListQueryPort;
        this.posterStorage = posterStorage;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizerEventListResult get(GetOrganizerEventListQuery query) {
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(query.actingStaff(), StaffAction.VIEW_EVENTS));
        Instant now = clock.instant();
        Map<EventListSection, List<OrganizerEventSummary>> sections = eventListQueryPort.eventsOf(permission.organizerId()).stream()
                .collect(Collectors.groupingBy(event -> EventListSection.of(event.status())));
        return new OrganizerEventListResult(
                sorted(sections, EventListSection.DRAFT, DRAFT_ORDER, event -> draftOf(event, now)),
                sorted(sections, EventListSection.IN_PROGRESS, IN_PROGRESS_ORDER, this::listedOf),
                sorted(sections, EventListSection.PAST, PAST_ORDER, this::listedOf));
    }

    private static <T> List<T> sorted(Map<EventListSection, List<OrganizerEventSummary>> sections, EventListSection section,
            Comparator<OrganizerEventSummary> order, Function<OrganizerEventSummary, T> mapper) {
        return sections.getOrDefault(section, List.of()).stream().sorted(order).map(mapper).toList();
    }

    private DraftEvent draftOf(OrganizerEventSummary event, Instant now) {
        return new DraftEvent(event.eventId(), event.title(), posterUrlOf(event), event.wizardProgress(), event.wizardProgress().next(),
                event.lastModifiedAt(), event.firstDate(), event.lastDate(), DraftEventNudges.isFolded(event.lastDate(), now));
    }

    private ListedEvent listedOf(OrganizerEventSummary event) {
        return new ListedEvent(event.eventId(), event.title(), posterUrlOf(event), event.status(), event.firstDate(), event.lastDate());
    }

    private String posterUrlOf(OrganizerEventSummary event) {
        return event.poster() == null ? null : posterStorage.publicUrlOf(event.poster());
    }
}
