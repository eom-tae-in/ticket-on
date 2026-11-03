package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.DuplicateEventCommand;
import com.ticketon.application.event.port.in.DuplicateEventUseCase;
import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.application.event.port.out.EventEntranceRepository;
import com.ticketon.application.event.port.out.EventPosterStoragePort;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.event.port.out.FloorPlanRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.common.TokenGenerator;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventEntrances;
import com.ticketon.domain.event.EventPublicAddress;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 복제 — 무엇을 가져오고 무엇을 가져오지 않는지는 {@link Event#duplicate}가, 도면 · 입구 목록을 새 것으로 통째로 복사하는 일은
 * {@link FloorPlan#copyZonesFor} · {@link FloorPlan#copyBlocksFrom} · {@link EventEntrances#copyFor}가 갖는다(docs/04 "행사 복제"). 여기서는
 * 권한을 확인하고 원본을 잠가 읽은 뒤, 공개 주소를 발급해 복제본을 저장하고 도면 · 입구 목록을 복사해 저장하고 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 복제하는 사람의 계정 줄(KEY SHARE) → 주최자 줄(KEY SHARE) → 권한 다시 확인 → 원본 행사 줄(KEY SHARE) →
 * 원본 도면 · 입구 목록 읽기 → 복제본 행사 저장 → 구역 · 무대 위치 복사해 저장 → 블록 · 좌석 복사해 저장 → 입구 목록 복사해 저장 → 조작 기록
 * ({@link EventEditingLocks}). 복제본 줄은 이 트랜잭션이 넣는 새 줄이라 따로 잠그지 않는다.
 * <ul>
 *   <li><b>원본은 FOR KEY SHARE로 잠근다</b> — 원본은 고치지 않으므로 FOR UPDATE까지 걸지 않고, 원본을 고치는 흐름(행사 줄 FOR UPDATE)만 막는다.
 *       도면 · 입구 목록은 여러 문장으로 읽는데, 잡은 동안 원본의 편집이 커밋되지 않아 한 시점의 도면을 읽는다 — 문장 사이에 커밋된 편집을 반쯤
 *       읽으면 도면을 되살리지 못한다(좌석 없는 블록, 도면에 없는 구역을 가리키는 좌석). 앞서 원본을 고치는 중이면 그쪽이 끝날 때까지 기다렸다가
 *       고친 원본을 복제한다. 원본에 예매 · 초대권 줄을 넣는 흐름(외래 키 확인 잠금)은 막지 않는다.</li>
 *   <li><b>도면은 두 번 저장한다</b> — 좌석은 구역을 id로 가리키므로 구역을 먼저 저장해 id를 받은 뒤 블록 · 좌석을 얹는다.</li>
 *   <li>구역 · 블록이 생겼다는 알림(ZoneAdded · SeatBlockAdded)은 발행하지 않는다 — 재고는 회차에만 붙는데(docs/02 "날짜와 회차") 복제본에는
 *       회차가 없다. 회차를 더할 때(SessionAdded) 그 회차의 재고가 도면 전체로 만들어진다.</li>
 * </ul>
 * 공개 주소는 행사 만들기처럼 TokenGenerator로 URL 안전 문자 {@value EventPublicAddress#LENGTH}자를 만든다.
 */
@Service
public class DuplicateEventService implements DuplicateEventUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final EventEntranceRepository entranceRepository;
    private final EventPosterStoragePort posterStorage;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;

    public DuplicateEventService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, StaffAccountRepository staffAccountRepository,
            OrganizerRepository organizerRepository, EventRepository eventRepository, FloorPlanRepository floorPlanRepository,
            EventEntranceRepository entranceRepository, EventPosterStoragePort posterStorage, TokenGenerator tokenGenerator,
            Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.staffAccountRepository = staffAccountRepository;
        this.organizerRepository = organizerRepository;
        this.eventRepository = eventRepository;
        this.floorPlanRepository = floorPlanRepository;
        this.entranceRepository = entranceRepository;
        this.posterStorage = posterStorage;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
    }

    /** 조작 기록의 요약 — "행사 복제 · 2026 봄 정기공연 〈열두 번째 달〉". 기록은 복제본을 가리키고, 요약은 원본의 제목을 말한다. */
    static String operationSummary(EventTitle originalTitle) {
        return "행사 복제 · " + originalTitle.value();
    }

    @Override
    @Transactional
    public EventWizardResult duplicate(DuplicateEventCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase, staffAccountRepository,
                organizerRepository, new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.CREATE_EVENT));
        Event original = EventEditingLocks.keyShareEvent(eventRepository, permission, command.eventId());
        FloorPlan originalFloorPlan = floorPlanRepository.findByEventId(command.eventId());
        EventEntrances originalEntrances = entranceRepository.findByEventId(command.eventId());

        EventPublicAddress publicAddress = EventPublicAddress.issue(tokenGenerator.urlSafe(EventPublicAddress.LENGTH));
        Event copy = eventRepository.add(original.duplicate(publicAddress, now));
        long copyId = copy.id().orElseThrow();
        copyFloorPlan(originalFloorPlan, copyId, now);
        entranceRepository.save(originalEntrances.copyFor(copyId), now);
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT, copyId,
                operationSummary(original.basicInformation().title()), now));
        return EventWizardResults.of(copy, posterStorage);
    }

    /** 구역 · 무대 위치를 복사해 저장하고(구역 id를 받는다), 그 도면에 블록 · 좌석을 복사해 다시 저장한다. */
    private void copyFloorPlan(FloorPlan originalFloorPlan, long copyId, Instant now) {
        FloorPlan copiedFloorPlan = floorPlanRepository.save(originalFloorPlan.copyZonesFor(copyId), now);
        copiedFloorPlan.copyBlocksFrom(originalFloorPlan);
        floorPlanRepository.save(copiedFloorPlan, now);
    }
}
