package com.ticketon.api.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import com.ticketon.application.event.port.in.AddSeatBlockCommand;
import com.ticketon.application.event.port.in.AddSeatBlockUseCase;
import com.ticketon.application.event.port.in.AddTimedSessionCommand;
import com.ticketon.application.event.port.in.AddTimedSessionUseCase;
import com.ticketon.application.event.port.in.ChangeSessionPerformersCommand;
import com.ticketon.application.event.port.in.ChangeSessionPerformersUseCase;
import com.ticketon.application.event.port.in.ChooseSessionModeCommand;
import com.ticketon.application.event.port.in.ChooseSessionModeUseCase;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.CreateEventUseCase;
import com.ticketon.application.event.port.in.EventBasicInformationItems;
import com.ticketon.application.event.port.in.EventScheduleResult;
import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.application.event.port.in.RecordChangeHistoryFirstVersionCommand;
import com.ticketon.application.event.port.in.RecordChangeHistoryFirstVersionUseCase;
import com.ticketon.application.event.port.in.RenameSeatBlockCommand;
import com.ticketon.application.event.port.in.RenameSeatBlockUseCase;
import com.ticketon.application.event.port.in.SaveEventBasicInformationCommand;
import com.ticketon.application.event.port.in.SaveEventBasicInformationUseCase;
import com.ticketon.application.event.port.in.UploadEventPosterCommand;
import com.ticketon.application.event.port.in.UploadEventPosterUseCase;
import com.ticketon.application.event.port.out.EventChangeHistoryQueryPort;
import com.ticketon.application.event.query.EventChangeHistoryEntry;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.common.TestImages;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.event.BlockAngle;
import com.ticketon.domain.event.BlockName;
import com.ticketon.domain.event.BlockSize;
import com.ticketon.domain.event.CanvasPoint;
import com.ticketon.domain.event.EventChangeHistoryTarget;
import com.ticketon.domain.event.EventPosterImage;
import com.ticketon.domain.event.EventPublicContact;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.SessionMode;
import com.ticketon.domain.event.SessionPerformers;
import com.ticketon.domain.event.TimedSessionTime;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffRole;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)과 로컬 파일 저장소(임시 디렉터리)로 띄우고, 판매를 시작한 행사의 제목 · 포스터 · 회차 출연을
 * 유스케이스로 고쳐 변경 이력이 유효 기간과 함께 쌓이는지, 운영자가 제목을 바꾸면 소유자에게만 메일이 가는지 본다(#46 완료 조건 — "변경 이력이
 * 기간과 함께 쌓이고, 운영자 제목 변경 알림"). 판매 중에 1단계에서 고친 상세 안내 · 지도 링크 · 공개용 연락처도 같이 쌓이는지 본다(#231). Docker가
 * 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(docs/15)의 소유자 박정훈(조용한고래)과 운영자 기획 단원(푸른바람)이 봄 정기공연(11/14 저녁, "주연: 김배우")을 준비했다. 판매 시작(#58)이
 * 아직 없어, 행사를 판매 대기로 바꾸는 일은 표에 바로 하고 첫 버전은 판매 시작이 부를 유스케이스를 같은 트랜잭션에서 부른다. 운영진 로그인 ·
 * 주최자 · 소속도 표에 바로 넣는다. 시각은 시계를 옮겨 정한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("행사 변경 이력 쌓기 (진짜 PostgreSQL · 로컬 저장소)")
class EventChangeHistoryIntegrationTest {

    private static final Instant PUBLISHED_AT = korean(2026, 10, 15, 10, 0);
    private static final Instant OPENED_AT = korean(2026, 10, 18, 14, 0);
    private static final Instant FIRST_CHANGE = korean(2026, 10, 20, 14, 0);
    private static final Instant SECOND_CHANGE = korean(2026, 10, 25, 9, 0);
    private static final LocalDate NOVEMBER_14 = LocalDate.of(2026, 11, 14);
    private static final String POSTER_BUNDLE = "event-posters";
    private static final String MAP_LINK = "https://map.example/place/12";
    private static final String PUBLIC_CONTACT = "twelve-months@example.com";

    private static final MutableClock MUTABLE_CLOCK = MutableClock.at(PUBLISHED_AT);

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @TempDir
    private static Path storageDirectory;

    @DynamicPropertySource
    static void localStorage(DynamicPropertyRegistry registry) {
        registry.add("ticketon.storage.local.directory", () -> storageDirectory.toString());
    }

    @TestBean(name = "clock")
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private CreateEventUseCase createEventUseCase;

    @Autowired
    private ChooseSessionModeUseCase chooseSessionModeUseCase;

    @Autowired
    private AddTimedSessionUseCase addTimedSessionUseCase;

    @Autowired
    private ChangeSessionPerformersUseCase changeSessionPerformersUseCase;

    @Autowired
    private UploadEventPosterUseCase uploadEventPosterUseCase;

    @Autowired
    private SaveEventBasicInformationUseCase saveEventBasicInformationUseCase;

    @Autowired
    private AddSeatBlockUseCase addSeatBlockUseCase;

    @Autowired
    private RenameSeatBlockUseCase renameSeatBlockUseCase;

    @Autowired
    private RecordChangeHistoryFirstVersionUseCase recordFirstVersionUseCase;

    @Autowired
    private EventChangeHistoryQueryPort historyQueryPort;

    private long twelveMonths;
    private long junghoon;
    private long planner;
    private String junghoonEmail;
    private String plannerEmail;
    private long springPlay;
    private long novemberFourteenth;
    private String draftPosterKey;

    static Clock clock() {
        return MUTABLE_CLOCK;
    }

    private static Instant korean(int year, int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, minute));
    }

    /** 작성 중에 회차 · 출연 · 포스터를 갖춘 봄 정기공연을 판매 대기로 두고 첫 버전을 쌓는다 — 판매 시작 직후의 모습. */
    @BeforeEach
    void publishSpringPlay() {
        MUTABLE_CLOCK.moveTo(PUBLISHED_AT);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        junghoonEmail = "junghoon-" + suffix + "@example.com";
        plannerEmail = "planner-" + suffix + "@example.com";
        junghoon = storeStaff("박정훈", "고래" + suffix, junghoonEmail);
        planner = storeStaff("김기획", "바람" + suffix, plannerEmail);
        twelveMonths = storeOrganizer("극단 열두달 " + suffix);
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
        springPlay = createEventUseCase.create(new CreateEventCommand(acting(planner), items("2026 봄 정기공연 〈열두 번째 달〉"))).eventId();
        chooseSessionModeUseCase.choose(new ChooseSessionModeCommand(acting(planner), springPlay, SessionMode.TIMED, null));
        EventScheduleResult schedule = addTimedSessionUseCase.add(new AddTimedSessionCommand(acting(planner), springPlay, NOVEMBER_14,
                TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0))));
        novemberFourteenth = schedule.dates().getFirst().sessions().getFirst().sessionId();
        changePerformers("주연: 김배우");
        draftPosterKey = fileKeyOf(uploadPoster());
        jdbcTemplate.update("UPDATE event SET status = 'AWAITING_OPEN' WHERE id = ?", springPlay);
        transactionTemplate.executeWithoutResult(status -> recordFirstVersionUseCase.record(new RecordChangeHistoryFirstVersionCommand(
                new StaffPermissionResult(twelveMonths, planner, StaffRole.OPERATOR, StaffAction.EDIT_EVENT), springPlay, PUBLISHED_AT)));
    }

    @Test
    @DisplayName("판매를 시작한 순간의 제목 · 소개 · 포스터 · 11/14 출연이 첫 버전으로 쌓이고, 제목을 두 번 바꾸면 제목 버전이 셋이다")
    void titleChangedTwiceHasThreeVersions() {
        MUTABLE_CLOCK.moveTo(FIRST_CHANGE);
        changeTitle(planner, "2026 봄 앵콜공연 〈열두 번째 달〉");
        MUTABLE_CLOCK.moveTo(SECOND_CHANGE);
        changeTitle(junghoon, "2026 봄 앵콜공연 〈열두 번째 달〉 — 추가 회차");

        List<EventChangeHistoryEntry> history = historyQueryPort.findByEventId(springPlay);
        assertThat(history).filteredOn(entry -> entry.validFrom().equals(PUBLISHED_AT)).extracting(EventChangeHistoryEntry::target)
                .containsExactly(EventChangeHistoryTarget.TITLE, EventChangeHistoryTarget.INTRODUCTION, EventChangeHistoryTarget.POSTER,
                        EventChangeHistoryTarget.SESSION_PERFORMERS);
        assertThat(history).filteredOn(entry -> entry.target() == EventChangeHistoryTarget.TITLE)
                .extracting(EventChangeHistoryEntry::previousContent, EventChangeHistoryEntry::newContent, EventChangeHistoryEntry::validFrom,
                        EventChangeHistoryEntry::validUntil, EventChangeHistoryEntry::changedByStaffAccountId,
                        EventChangeHistoryEntry::noticeSent)
                .containsExactly(
                        tuple(null, "2026 봄 정기공연 〈열두 번째 달〉", PUBLISHED_AT, FIRST_CHANGE, planner, false),
                        tuple("2026 봄 정기공연 〈열두 번째 달〉", "2026 봄 앵콜공연 〈열두 번째 달〉", FIRST_CHANGE, SECOND_CHANGE, planner, false),
                        tuple("2026 봄 앵콜공연 〈열두 번째 달〉", "2026 봄 앵콜공연 〈열두 번째 달〉 — 추가 회차", SECOND_CHANGE, null, junghoon,
                                false));
        assertThat(history).filteredOn(entry -> entry.target() == EventChangeHistoryTarget.SESSION_PERFORMERS).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.sessionId()).isEqualTo(novemberFourteenth);
                    assertThat(entry.sessionDate()).isEqualTo(NOVEMBER_14);
                    assertThat(entry.newContent()).isEqualTo("주연: 김배우");
                });
    }

    @Test
    @DisplayName("운영자가 제목을 바꾸면 소유자에게만 '운영자가 행사 제목을 바꿈' 메일이 가고, 소유자 본인이 바꾸면 가지 않는다")
    void operatorTitleChangeMailsOwnerOnly() {
        MUTABLE_CLOCK.moveTo(FIRST_CHANGE);
        changeTitle(planner, "2026 봄 앵콜공연 〈열두 번째 달〉");

        assertThat(titleChangeMails()).containsExactly("EMAIL " + junghoonEmail + " true");

        MUTABLE_CLOCK.moveTo(SECOND_CHANGE);
        changeTitle(junghoon, "2026 봄 앵콜공연 〈열두 번째 달〉 — 추가 회차");

        assertThat(titleChangeMails()).containsExactly("EMAIL " + junghoonEmail + " true");
        assertThat(historyQueryPort.findByEventId(springPlay)).filteredOn(entry -> entry.target() == EventChangeHistoryTarget.TITLE)
                .hasSize(3);
    }

    @Test
    @DisplayName("판매를 시작한 뒤 포스터를 바꾸면 버전이 쌓이고, 바꾸기 전 파일은 커밋된 뒤에도 저장소에 남는다")
    void posterChangeKeepsPreviousFile() {
        MUTABLE_CLOCK.moveTo(FIRST_CHANGE);

        String changedPosterKey = fileKeyOf(uploadPoster());

        assertThat(historyQueryPort.findByEventId(springPlay)).filteredOn(entry -> entry.target() == EventChangeHistoryTarget.POSTER)
                .extracting(EventChangeHistoryEntry::previousContent, EventChangeHistoryEntry::newContent, EventChangeHistoryEntry::validUntil)
                .containsExactly(tuple(null, draftPosterKey, FIRST_CHANGE), tuple(draftPosterKey, changedPosterKey, null));
        assertThat(storageDirectory.resolve(POSTER_BUNDLE).resolve(draftPosterKey)).exists();
        assertThat(storageDirectory.resolve(POSTER_BUNDLE).resolve(changedPosterKey)).exists();
    }

    @Test
    @DisplayName("회차 출연을 \"주연: 이배우\"로 고치면 그 회차의 버전이 쌓인다 — 첫 버전은 바꾼 순간에 닫힌다")
    void performersChange() {
        MUTABLE_CLOCK.moveTo(FIRST_CHANGE);

        changePerformers("주연: 이배우");

        assertThat(historyQueryPort.findByEventId(springPlay))
                .filteredOn(entry -> entry.target() == EventChangeHistoryTarget.SESSION_PERFORMERS)
                .extracting(EventChangeHistoryEntry::previousContent, EventChangeHistoryEntry::newContent, EventChangeHistoryEntry::validUntil)
                .containsExactly(tuple(null, "주연: 김배우", FIRST_CHANGE), tuple("주연: 김배우", "주연: 이배우", null));
    }

    @Test
    @DisplayName("판매 중에 1단계에서 비어 있던 상세 안내에 \"지하 1층, 주차 불가\"를 넣고 지도 링크 · 연락처를 적은 뒤 지도 링크를 비우면 칸마다 버전이 "
            + "쌓인다 — 소유자에게 메일은 가지 않는다")
    void venueGuideChangedOnSale() {
        jdbcTemplate.update("UPDATE event SET status = 'ON_SALE', booking_opened_at = ? WHERE id = ?", Timestamp.from(OPENED_AT),
                springPlay);
        MUTABLE_CLOCK.moveTo(FIRST_CHANGE);
        saveVenueGuide("지하 1층, 주차 불가", ExternalLink.of(MAP_LINK), EventPublicContact.of(PUBLIC_CONTACT));
        MUTABLE_CLOCK.moveTo(SECOND_CHANGE);
        saveVenueGuide("지하 1층, 주차 불가", null, EventPublicContact.of(PUBLIC_CONTACT));

        List<EventChangeHistoryEntry> history = historyQueryPort.findByEventId(springPlay);
        assertThat(history).filteredOn(entry -> entry.validFrom().equals(PUBLISHED_AT)).extracting(EventChangeHistoryEntry::target)
                .containsExactly(EventChangeHistoryTarget.TITLE, EventChangeHistoryTarget.INTRODUCTION, EventChangeHistoryTarget.POSTER,
                        EventChangeHistoryTarget.SESSION_PERFORMERS);
        assertThat(history).filteredOn(entry -> entry.target() == EventChangeHistoryTarget.VENUE_DETAILS)
                .extracting(EventChangeHistoryEntry::previousContent, EventChangeHistoryEntry::newContent, EventChangeHistoryEntry::validFrom,
                        EventChangeHistoryEntry::validUntil, EventChangeHistoryEntry::changedByStaffAccountId,
                        EventChangeHistoryEntry::noticeSent)
                .containsExactly(tuple(null, "지하 1층, 주차 불가", FIRST_CHANGE, null, planner, false));
        assertThat(history).filteredOn(entry -> entry.target() == EventChangeHistoryTarget.MAP_LINK)
                .extracting(EventChangeHistoryEntry::previousContent, EventChangeHistoryEntry::newContent, EventChangeHistoryEntry::validFrom,
                        EventChangeHistoryEntry::validUntil)
                .containsExactly(tuple(null, MAP_LINK, FIRST_CHANGE, SECOND_CHANGE), tuple(MAP_LINK, null, SECOND_CHANGE, null));
        assertThat(history).filteredOn(entry -> entry.target() == EventChangeHistoryTarget.PUBLIC_CONTACT)
                .extracting(EventChangeHistoryEntry::previousContent, EventChangeHistoryEntry::newContent, EventChangeHistoryEntry::validUntil)
                .containsExactly(tuple(null, PUBLIC_CONTACT, null));
        assertThat(history).hasSize(8);
        assertThat(titleChangeMails()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM event WHERE id = ?", String.class, springPlay)).isEqualTo("ON_SALE");
    }

    @Test
    @DisplayName("판매를 시작할 때 적혀 있던 상세 안내 · 지도 링크 · 공개용 연락처는 첫 버전으로 쌓인다")
    void venueGuideFirstVersions() {
        long autumnReading = createEventUseCase.create(new CreateEventCommand(acting(planner), new EventBasicInformationItems(
                EventTitle.of("가을 낭독회"), EventType.PERFORMANCE, "연극", null, "극단 열두달의 가을 낭독회입니다.",
                new Venue("○○아트홀", "서울특별시 종로구 대학로 12"), "지하 1층, 주차 불가", ExternalLink.of(MAP_LINK),
                EventPublicContact.of(PUBLIC_CONTACT)))).eventId();
        chooseSessionModeUseCase.choose(new ChooseSessionModeCommand(acting(planner), autumnReading, SessionMode.TIMED, null));
        addTimedSessionUseCase.add(new AddTimedSessionCommand(acting(planner), autumnReading, NOVEMBER_14,
                TimedSessionTime.of(LocalTime.of(15, 0), LocalTime.of(17, 0))));
        String posterKey = fileKeyOf(uploadEventPosterUseCase.upload(new UploadEventPosterCommand(acting(planner), autumnReading,
                EventPosterImage.of("image/jpeg", TestImages.jpeg(300, 400)))));
        jdbcTemplate.update("UPDATE event SET status = 'AWAITING_OPEN' WHERE id = ?", autumnReading);

        transactionTemplate.executeWithoutResult(status -> recordFirstVersionUseCase.record(new RecordChangeHistoryFirstVersionCommand(
                new StaffPermissionResult(twelveMonths, junghoon, StaffRole.OWNER, StaffAction.EDIT_EVENT), autumnReading, PUBLISHED_AT)));

        assertThat(historyQueryPort.findByEventId(autumnReading))
                .extracting(EventChangeHistoryEntry::target, EventChangeHistoryEntry::previousContent, EventChangeHistoryEntry::newContent,
                        EventChangeHistoryEntry::validUntil)
                .containsExactly(
                        tuple(EventChangeHistoryTarget.TITLE, null, "가을 낭독회", null),
                        tuple(EventChangeHistoryTarget.INTRODUCTION, null, "극단 열두달의 가을 낭독회입니다.", null),
                        tuple(EventChangeHistoryTarget.POSTER, null, posterKey, null),
                        tuple(EventChangeHistoryTarget.VENUE_DETAILS, null, "지하 1층, 주차 불가", null),
                        tuple(EventChangeHistoryTarget.MAP_LINK, null, MAP_LINK, null),
                        tuple(EventChangeHistoryTarget.PUBLIC_CONTACT, null, PUBLIC_CONTACT, null));
    }

    @Test
    @DisplayName("블록 이름은 이력 대상이 아니다 — 판매를 시작한 뒤 \"1증\"을 \"1층\"으로 고쳐도 이력이 늘지 않는다")
    void blockNameIsNotRecorded() {
        long blockId = addSeatBlockUseCase.add(new AddSeatBlockCommand(acting(planner), springPlay, BlockName.fromInput("1증").orElse(null),
                BlockSize.of(5, 10), CanvasPoint.of(120, 80), BlockAngle.NONE)).blockId();
        MUTABLE_CLOCK.moveTo(FIRST_CHANGE);

        renameSeatBlockUseCase.rename(new RenameSeatBlockCommand(acting(planner), springPlay, blockId,
                BlockName.fromInput("1층").orElse(null)));

        assertThat(historyQueryPort.findByEventId(springPlay)).hasSize(4);
    }

    @Test
    @DisplayName("첫 버전은 판매 시작과 같은 트랜잭션에서만 쌓는다 — 트랜잭션 밖에서 부르면 막는다")
    void firstVersionRequiresTransaction() {
        assertThatExceptionOfType(IllegalTransactionStateException.class).isThrownBy(() -> recordFirstVersionUseCase.record(
                new RecordChangeHistoryFirstVersionCommand(
                        new StaffPermissionResult(twelveMonths, junghoon, StaffRole.OWNER, StaffAction.EDIT_EVENT), springPlay,
                        FIRST_CHANGE)));
        assertThat(historyQueryPort.findByEventId(springPlay)).hasSize(4);
    }

    /** 이 테스트의 소유자 · 운영자에게 간 '운영자가 행사 제목을 바꿈' 발송 이력 — "수단 받는 곳 성공". */
    private List<String> titleChangeMails() {
        return jdbcTemplate.queryForList("""
                SELECT channel || ' ' || recipient_address || ' ' || succeeded FROM notification_send_record
                WHERE kind = 'EVENT_TITLE_CHANGED_BY_OPERATOR' AND recipient_address IN (?, ?) ORDER BY id""", String.class,
                junghoonEmail, plannerEmail);
    }

    private void changeTitle(long staffAccountId, String title) {
        saveEventBasicInformationUseCase.save(new SaveEventBasicInformationCommand(acting(staffAccountId), springPlay, items(title)));
    }

    /** 운영자(기획 단원)가 제목 · 소개는 그대로 두고 상세 안내 · 지도 링크 · 공개용 연락처만 그 값으로 저장한다 — null이면 비운다. */
    private void saveVenueGuide(String venueDetails, ExternalLink mapLink, EventPublicContact publicContact) {
        EventBasicInformationItems items = items("2026 봄 정기공연 〈열두 번째 달〉");
        saveEventBasicInformationUseCase.save(new SaveEventBasicInformationCommand(acting(planner), springPlay,
                new EventBasicInformationItems(items.title(), items.type(), items.categoryName(), items.genreName(), items.introduction(),
                        items.venue(), venueDetails, mapLink, publicContact)));
    }

    private void changePerformers(String performers) {
        changeSessionPerformersUseCase.change(new ChangeSessionPerformersCommand(acting(planner), springPlay, novemberFourteenth,
                new SessionPerformers(performers)));
    }

    private EventWizardResult uploadPoster() {
        return uploadEventPosterUseCase.upload(new UploadEventPosterCommand(acting(planner), springPlay,
                EventPosterImage.of("image/jpeg", TestImages.jpeg(300, 400))));
    }

    private static String fileKeyOf(EventWizardResult result) {
        String posterUrl = result.posterUrl();
        return posterUrl.substring(posterUrl.lastIndexOf('/') + 1);
    }

    private ActingStaff acting(long staffAccountId) {
        return new ActingStaff(AccountRef.of(AccountKind.STAFF, staffAccountId), twelveMonths);
    }

    private static EventBasicInformationItems items(String title) {
        return new EventBasicInformationItems(EventTitle.of(title), EventType.PERFORMANCE, "연극", null,
                "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.", new Venue("○○아트홀", "서울특별시 종로구 대학로 12"), null, null,
                null);
    }

    private long storeStaff(String name, String nickname, String email) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO staff_account (status, name, nickname, email, email_lowercase, password_hash, signed_up_at,
                                           email_verified_at)
                VALUES ('ACTIVE', ?, ?, ?, lower(?), '{test}hashed-password', now() - interval '30 days',
                        now() - interval '30 days')
                RETURNING id""", Long.class, name, nickname, email, email);
        assertThat(id).isNotNull();
        return id;
    }

    private long storeOrganizer(String name) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO organizer (name, status) VALUES (?, 'ACTIVE') RETURNING id",
                Long.class, name);
        assertThat(id).isNotNull();
        return id;
    }

    private void storeMembership(long organizerId, long staffAccountId, String role) {
        jdbcTemplate.update("""
                INSERT INTO staff_membership (organizer_id, staff_account_id, role, started_at)
                VALUES (?, ?, ?, now() - interval '10 days')""", organizerId, staffAccountId, role);
    }
}
