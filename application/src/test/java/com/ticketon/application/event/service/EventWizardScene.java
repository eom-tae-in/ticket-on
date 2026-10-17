package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.EventBasicInformationItems;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.service.CheckStaffPermissionService;
import com.ticketon.application.organizer.service.RecordStaffOperationService;
import com.ticketon.common.FixedTokenGenerator;
import com.ticketon.common.TokenGenerator;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventPublicContact;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OrganizerStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 행사 만들기 · 위저드 단계 저장 · 조회 유스케이스 테스트가 함께 쓰는 장면 — docs/15의 두 주최자와 세 사람.
 * <ul>
 *   <li>극단 열두달 — 대표 박정훈이 소유자, 기획 단원이 운영자. 활동 중.</li>
 *   <li>늦여름 — 최여름이 소유자, 이도현이 운영자. 활동 중. 이도현은 열두달에는 바깥 사람이다.</li>
 *   <li>새봄 극단 — 신청만 내고 심사 중이다. 신청한 새봄 대표는 승인 전이라 소속되지 않았다.</li>
 *   <li>닫힌 주최자 극단 봄날 — 예전 소유자 소속이 남은 채 닫혔다(소속 줄이 남아 있어도 막는지 보려고).</li>
 * </ul>
 * 시계는 2026-10-15 10:00(한국 시각)에 멈춰 있다. 권한 확인과 조작 기록은 진짜 유스케이스를 메모리 저장소로 돌린다.
 */
final class EventWizardScene {

    static final long JUNGHOON = 1L;
    static final long PLANNER = 2L;
    static final long YEOREUM = 3L;
    static final long DOHYUN = 4L;
    static final long NEW_SPRING_APPLICANT = 5L;
    static final long SPRING_FORMER_OWNER = 6L;

    static final Instant APPROVED_AT = KoreanTime.toInstant(LocalDateTime.of(2019, 3, 4, 10, 0));
    static final Instant NOW = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    /** 첫 행사에 발급할 공개 주소. */
    static final String FIRST_ADDRESS = "Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0";

    /** 두 번째 행사에 발급할 공개 주소. */
    static final String SECOND_ADDRESS = "aB3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG9";

    final MutableClock clock = MutableClock.at(NOW);
    final RowLockLog rowLockLog = new RowLockLog();
    final FakeOrganizerRepository organizerRepository = new FakeOrganizerRepository(rowLockLog);
    final FakeOrganizerStaffRepository staffRepository = new FakeOrganizerStaffRepository();
    final FakeStaffAccountRepository staffAccountRepository = new FakeStaffAccountRepository(rowLockLog);
    final FakeEventRepository eventRepository = new FakeEventRepository(rowLockLog);
    final FakeEventCategoryRepository categoryRepository = new FakeEventCategoryRepository();
    final FakeEventPasswordHashPort passwordHashPort = new FakeEventPasswordHashPort();
    final FakeEventPosterStorage posterStorage = new FakeEventPosterStorage();
    final FakeFloorPlanRepository floorPlanRepository = new FakeFloorPlanRepository();
    final FakeEventEntranceRepository entranceRepository = new FakeEventEntranceRepository();
    final RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
    final RecordingStaffOperationRecordRepository recordRepository = new RecordingStaffOperationRecordRepository();
    final FixedTokenGenerator tokenGenerator = new FixedTokenGenerator(FIRST_ADDRESS, SECOND_ADDRESS);
    final CheckStaffPermissionService checkStaffPermissionService =
            new CheckStaffPermissionService(organizerRepository, staffRepository);
    final RecordStaffOperationService recordStaffOperationService = new RecordStaffOperationService(recordRepository);

    final long twelveMonths;
    final long lateSummer;
    final long newSpring;
    final long closedSpring;

    EventWizardScene() {
        staffAccountRepository.storeActive(JUNGHOON, "박정훈", "조용한고래", "junghoon@example.com");
        staffAccountRepository.storeActive(PLANNER, "김기획", "푸른바람", "planner@example.com");
        staffAccountRepository.storeActive(YEOREUM, "최여름", "늦은여름", "yeoreum@example.com");
        staffAccountRepository.storeActive(DOHYUN, "이도현", "밤의기타", "dohyun@example.com");
        staffAccountRepository.storeActive(NEW_SPRING_APPLICANT, "한새봄", "새봄바람", "new-spring@example.com");
        staffAccountRepository.storeActive(SPRING_FORMER_OWNER, "윤봄날", "봄날햇살", "spring@example.com");

        twelveMonths = organizerRepository.store("극단 열두달", OrganizerStatus.ACTIVE);
        OrganizerStaff twelveMonthsStaff = OrganizerStaff.startWithOwner(twelveMonths, JUNGHOON, APPROVED_AT);
        twelveMonthsStaff.addOperator(PLANNER, APPROVED_AT.plusSeconds(86_400));
        staffRepository.store(twelveMonthsStaff);

        lateSummer = organizerRepository.store("늦여름", OrganizerStatus.ACTIVE);
        OrganizerStaff lateSummerStaff = OrganizerStaff.startWithOwner(lateSummer, YEOREUM, APPROVED_AT);
        lateSummerStaff.addOperator(DOHYUN, APPROVED_AT);
        staffRepository.store(lateSummerStaff);

        newSpring = organizerRepository.store("새봄 극단", OrganizerStatus.UNDER_REVIEW);

        closedSpring = organizerRepository.store("극단 봄날", OrganizerStatus.CLOSED);
        staffRepository.store(OrganizerStaff.startWithOwner(closedSpring, SPRING_FORMER_OWNER, APPROVED_AT));
    }

    static ActingStaff acting(long staffAccountId, long organizerId) {
        return new ActingStaff(AccountRef.of(AccountKind.STAFF, staffAccountId), organizerId);
    }

    /** 극단 열두달의 봄 정기공연 1단계 — 공연 · 연극, 선택 항목까지 모두 적었다(docs/03 "기본 정보"의 "○○아트홀", "지하 1층", "주차 불가"). */
    static EventBasicInformationItems springPlayItems() {
        return new EventBasicInformationItems(EventTitle.of("2026 봄 정기공연 〈열두 번째 달〉"), EventType.PERFORMANCE, "연극", null,
                "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.", new Venue("○○아트홀", "서울특별시 종로구 대학로 12"),
                "지하 1층, 주차 불가", ExternalLink.of("https://map.example/place/12"),
                EventPublicContact.of("twelve-months@example.com"));
    }

    /** 제목만 "앵콜공연"으로 고친 봄 정기공연 1단계. */
    static EventBasicInformationItems encoreItems() {
        EventBasicInformationItems items = springPlayItems();
        return new EventBasicInformationItems(EventTitle.of("2026 봄 앵콜공연 〈열두 번째 달〉"), items.type(), items.categoryName(),
                items.genreName(), items.introduction(), items.venue(), items.venueDetails(), items.mapLink(),
                items.publicContact());
    }

    CreateEventService createEventService() {
        return new CreateEventService(checkStaffPermissionService, recordStaffOperationService, staffAccountRepository,
                organizerRepository, eventRepository, categoryRepository, posterStorage, tokenGenerator, clock);
    }

    SaveEventBasicInformationService saveEventBasicInformationService() {
        return new SaveEventBasicInformationService(checkStaffPermissionService, recordStaffOperationService,
                staffAccountRepository, organizerRepository, eventRepository, categoryRepository, posterStorage, clock);
    }

    SaveEventBookingSettingsService saveEventBookingSettingsService() {
        return new SaveEventBookingSettingsService(checkStaffPermissionService, recordStaffOperationService,
                staffAccountRepository, organizerRepository, eventRepository, passwordHashPort, posterStorage, clock);
    }

    GetEventWizardService getEventWizardService() {
        return new GetEventWizardService(checkStaffPermissionService, eventRepository, posterStorage);
    }

    /** 포스터 열쇠는 넘긴 생성기로 붙인다 — 행사 공개 주소의 생성기와 따로 둔다. */
    UploadEventPosterService uploadEventPosterService(TokenGenerator posterTokenGenerator) {
        return new UploadEventPosterService(checkStaffPermissionService, recordStaffOperationService, staffAccountRepository,
                organizerRepository, eventRepository, posterStorage, posterTokenGenerator, clock);
    }

    AddZoneService addZoneService() {
        return new AddZoneService(checkStaffPermissionService, recordStaffOperationService, staffAccountRepository,
                organizerRepository, eventRepository, floorPlanRepository, eventPublisher, clock);
    }

    ChangeZoneService changeZoneService() {
        return new ChangeZoneService(checkStaffPermissionService, recordStaffOperationService, staffAccountRepository,
                organizerRepository, eventRepository, floorPlanRepository, eventPublisher, clock);
    }

    RemoveZoneService removeZoneService() {
        return new RemoveZoneService(checkStaffPermissionService, recordStaffOperationService, staffAccountRepository,
                organizerRepository, eventRepository, floorPlanRepository, eventPublisher, clock);
    }

    GetEventZonesService getEventZonesService() {
        return new GetEventZonesService(checkStaffPermissionService, eventRepository, floorPlanRepository);
    }

    AddEntranceService addEntranceService() {
        return new AddEntranceService(checkStaffPermissionService, recordStaffOperationService, staffAccountRepository,
                organizerRepository, eventRepository, entranceRepository, clock);
    }

    RenameEntranceService renameEntranceService() {
        return new RenameEntranceService(checkStaffPermissionService, recordStaffOperationService, staffAccountRepository,
                organizerRepository, eventRepository, entranceRepository, clock);
    }

    RemoveEntranceService removeEntranceService() {
        return new RemoveEntranceService(checkStaffPermissionService, recordStaffOperationService, staffAccountRepository,
                organizerRepository, eventRepository, entranceRepository, clock);
    }

    GetEventEntrancesService getEventEntrancesService() {
        return new GetEventEntrancesService(checkStaffPermissionService, eventRepository, entranceRepository);
    }

    /** 저장된 그 행사를 그 상태로 바꿔 둔다 — 판매를 시작한 뒤의 수정 범위를 보려고. 작성 중 · 판매 대기가 아니면 예매가 열렸던 것으로 둔다. */
    void moveEventTo(long eventId, EventStatus status) {
        Event event = eventRepository.findById(eventId).orElseThrow();
        boolean beforeOpening = status == EventStatus.DRAFT || status == EventStatus.AWAITING_OPEN;
        eventRepository.store(Event.restore(eventId, event.organizerId(), event.publicAddress(), event.basicInformation(),
                event.poster().orElse(null), event.visibility(), event.bookingSettings(), event.cancellationPolicy(), status,
                beforeOpening ? null : APPROVED_AT, List.of(), event.wizardProgress(), event.lastModifiedAt()));
    }

    GetEventPosterFileService getEventPosterFileService() {
        return new GetEventPosterFileService(posterStorage);
    }

    GetEventCategoriesService getEventCategoriesService() {
        return new GetEventCategoriesService(categoryRepository);
    }
}
