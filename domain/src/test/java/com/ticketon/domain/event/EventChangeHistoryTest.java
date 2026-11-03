package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.TWELVE_MONTHS_ORGANIZER;
import static com.ticketon.domain.event.EventFixtures.twelveMonthsSpringPlay;
import static com.ticketon.domain.event.EventStatusFixtures.ADDRESS;
import static com.ticketon.domain.event.EventStatusFixtures.CANCELLED_AT;
import static com.ticketon.domain.event.EventStatusFixtures.EVENT_ID;
import static com.ticketon.domain.event.EventStatusFixtures.LAST_MODIFIED_AT;
import static com.ticketon.domain.event.EventStatusFixtures.MARCH_14;
import static com.ticketon.domain.event.EventStatusFixtures.MARCH_15;
import static com.ticketon.domain.event.EventStatusFixtures.POSTER;
import static com.ticketon.domain.event.EventStatusFixtures.korean;
import static com.ticketon.domain.event.EventStatusFixtures.springPlayIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 극단 열두달(docs/15 박정훈)의 봄 정기공연 — 3/14 · 3/15 저녁 공연, 더블 캐스팅이라 3/14는 "주연: 김배우", 3/15는 출연을 아직 비워 두었다.
 * docs/03의 예시("주연: 김배우" → "주연: 이배우", "○○밴드 단독공연" → "○○밴드 앵콜공연", 10/14 공연 · 10/15 항의)를 옮겼다.
 */
@DisplayName("변경 이력 규칙 — 무엇을 언제 쌓고 언제 지우나 (docs/03 \"제목 · 소개 · 포스터는 변경 이력을 남깁니다\")")
class EventChangeHistoryTest {

    private static final SessionPerformers KIM = new SessionPerformers("주연: 김배우");
    private static final SessionPerformers LEE = new SessionPerformers("주연: 이배우");
    private static final EventPoster NEW_POSTER = new EventPoster("Nw7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.png");

    /** 3/14 19:00~21:00(회차 101, "주연: 김배우") · 3/15 19:00~21:00(회차 102, 출연 비움). */
    private static EventSchedule doubleCastSchedule() {
        return scheduleOf(session(101, 19, 21, KIM, null), MARCH_14, session(102, 19, 21, null, null), MARCH_15);
    }

    private static EventSchedule scheduleOf(EventSession first, LocalDate firstDate, EventSession second, LocalDate secondDate) {
        return EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of(
                EventDate.restore(11, firstDate, null, List.of(first)),
                EventDate.restore(12, secondDate, null, List.of(second))));
    }

    private static EventSession session(long id, int startHour, Integer endHour, SessionPerformers performers, Instant cancelledAt) {
        TimedSessionTime time = endHour == null
                ? TimedSessionTime.startingAt(LocalTime.of(startHour, 0))
                : TimedSessionTime.of(LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
        return EventSession.restore(id, time, performers, SessionPriceAdjustment.none(), cancelledAt);
    }

    /** 제목만 바꾼 기본 정보. */
    private static EventBasicInformation titled(EventBasicInformation information, String title) {
        return new EventBasicInformation(EventTitle.of(title), information.type(), information.category(), information.introduction(),
                information.venue(), information.venueDetails(), information.mapLink(), information.publicContact());
    }

    /** 소개만 바꾼 기본 정보. */
    private static EventBasicInformation introduced(EventBasicInformation information, String introduction) {
        return new EventBasicInformation(information.title(), information.type(), information.category(), introduction,
                information.venue(), information.venueDetails(), information.mapLink(), information.publicContact());
    }

    @Nested
    @DisplayName("언제 쌓나")
    class WhenRecorded {

        @ParameterizedTest
        @EnumSource(value = EventStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("판매를 시작한 뒤(판매 대기부터)의 행사만 쌓는다")
        void recordsAfterSalesStarted(EventStatus status) {
            assertThat(EventChangeHistory.isRecorded(springPlayIn(status))).isTrue();
        }

        @Test
        @DisplayName("작성 중의 변경은 쌓지 않는다 — 공개 전 내용은 관객이 본 적이 없다")
        void draftIsNotRecorded() {
            Event draft = springPlayIn(EventStatus.DRAFT);
            EventBasicInformation before = draft.basicInformation();
            draft.saveBasicInformationStep(titled(before, "○○밴드 앵콜공연"), LAST_MODIFIED_AT);

            assertThat(EventChangeHistory.isRecorded(draft)).isFalse();
            assertThat(EventChangeHistory.basicInformationVersions(draft, before)).isEmpty();
            Optional<EventPoster> previous = draft.changePoster(NEW_POSTER, LAST_MODIFIED_AT);
            assertThat(EventChangeHistory.posterVersion(draft, previous)).isEmpty();
            EventSchedule schedule = doubleCastSchedule();
            draft.changeSessionPerformers(schedule, 101, LEE, LAST_MODIFIED_AT);
            assertThat(EventChangeHistory.performersVersion(draft, schedule, 101, Optional.of(KIM))).isEmpty();
        }

        @Test
        @DisplayName("블록 이름은 대상 칸이 아니다 — 대상은 제목 · 소개 · 포스터 · 회차 출연 넷뿐이다 (docs/03, docs/99 B)")
        void blockNameIsNotTarget() {
            assertThat(Arrays.asList(EventChangeHistoryTarget.values())).containsExactly(EventChangeHistoryTarget.TITLE,
                    EventChangeHistoryTarget.INTRODUCTION, EventChangeHistoryTarget.POSTER, EventChangeHistoryTarget.SESSION_PERFORMERS);
            assertThat(Arrays.stream(EventChangeHistoryTarget.values()).filter(EventChangeHistoryTarget::isPerSession))
                    .containsExactly(EventChangeHistoryTarget.SESSION_PERFORMERS);
        }
    }

    @Nested
    @DisplayName("첫 버전 — 판매 대기로 넘어간(공개된) 순간의 내용")
    class FirstVersions {

        @Test
        @DisplayName("제목 · 소개 · 포스터와 출연이 적힌 회차(3/14 김배우)의 출연이 바꾸기 전 없이 쌓인다 — 출연을 비운 3/15는 쌓지 않는다")
        void titleIntroductionPosterAndWrittenPerformers() {
            Event published = springPlayIn(EventStatus.AWAITING_OPEN);

            assertThat(EventChangeHistory.firstVersions(published, doubleCastSchedule())).containsExactly(
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.TITLE, null, null, "2026 봄 정기공연 〈열두 번째 달〉"),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.INTRODUCTION, null, null,
                            twelveMonthsSpringPlay().introduction()),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.POSTER, null, null, POSTER.fileKey()),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, 101L, null, "주연: 김배우"));
        }

        @Test
        @DisplayName("오픈 시각이 지나 곧바로 판매 중이 됐어도 같다")
        void alsoWhenOpenedRightAway() {
            assertThat(EventChangeHistory.firstVersions(springPlayIn(EventStatus.ON_SALE), doubleCastSchedule())).hasSize(4);
        }

        @Test
        @DisplayName("작성 중이거나 포스터가 없으면 첫 버전을 쌓을 수 없다 — 판매 시작 흐름의 잘못이다")
        void requiresStartedSalesWithPoster() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> EventChangeHistory.firstVersions(springPlayIn(EventStatus.DRAFT), doubleCastSchedule()));
            Event withoutPoster = Event.restore(EVENT_ID, TWELVE_MONTHS_ORGANIZER, ADDRESS, twelveMonthsSpringPlay(), null,
                    EventVisibilitySetting.publicByDefault(), BookingSettings.defaults(), CancellationPolicy.standard(),
                    EventStatus.AWAITING_OPEN, null, List.of(), EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS, LAST_MODIFIED_AT);
            assertThatIllegalStateException().isThrownBy(() -> EventChangeHistory.firstVersions(withoutPoster, doubleCastSchedule()));
        }

        @Test
        @DisplayName("다른 행사의 일정이면 막는다")
        void rejectsOtherEventsSchedule() {
            EventSchedule other = EventSchedule.restore(EVENT_ID + 1, SessionMode.TIMED, null, List.of());

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> EventChangeHistory.firstVersions(springPlayIn(EventStatus.AWAITING_OPEN), other));
        }
    }

    @Nested
    @DisplayName("바꿀 때마다 새 버전")
    class Changes {

        @Test
        @DisplayName("제목을 \"○○밴드 단독공연\"에서 \"○○밴드 앵콜공연\"으로 바꾸면 바꾸기 전 · 뒤가 담긴 제목 버전 하나")
        void titleChange() {
            Event published = springPlayIn(EventStatus.ON_SALE);
            published.saveBasicInformationStep(titled(published.basicInformation(), "○○밴드 단독공연"), LAST_MODIFIED_AT);
            EventBasicInformation before = published.basicInformation();

            published.saveBasicInformationStep(titled(before, "○○밴드 앵콜공연"), LAST_MODIFIED_AT);

            assertThat(EventChangeHistory.basicInformationVersions(published, before)).containsExactly(
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.TITLE, null, "○○밴드 단독공연", "○○밴드 앵콜공연"));
        }

        @Test
        @DisplayName("제목과 소개를 함께 고치면 칸마다 하나씩 — 소개 \"주연: 김배우\" → \"주연: 이배우\"")
        void titleAndIntroduction() {
            Event published = springPlayIn(EventStatus.ON_SALE);
            EventBasicInformation before = introduced(published.basicInformation(), "주연: 김배우");
            published.saveBasicInformationStep(before, LAST_MODIFIED_AT);

            published.saveBasicInformationStep(introduced(titled(before, "○○밴드 앵콜공연"), "주연: 이배우"), LAST_MODIFIED_AT);

            assertThat(EventChangeHistory.basicInformationVersions(published, before)).containsExactly(
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.TITLE, null, "2026 봄 정기공연 〈열두 번째 달〉", "○○밴드 앵콜공연"),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.INTRODUCTION, null, "주연: 김배우", "주연: 이배우"));
        }

        @Test
        @DisplayName("상세 안내 · 연락처처럼 대상이 아닌 칸만 바꾸면 쌓지 않는다")
        void nonTargetFields() {
            Event published = springPlayIn(EventStatus.AWAITING_OPEN);
            EventBasicInformation before = published.basicInformation();

            published.saveBasicInformationStep(new EventBasicInformation(before.title(), before.type(), before.category(),
                    before.introduction(), before.venue(), "지하 1층, 주차 가능", before.mapLink(), null), LAST_MODIFIED_AT);

            assertThat(EventChangeHistory.basicInformationVersions(published, before)).isEmpty();
        }

        @Test
        @DisplayName("포스터를 바꾸면 바꾸기 전 · 뒤 파일 열쇠가 담긴 포스터 버전 — 같은 포스터면 없다")
        void posterChange() {
            Event published = springPlayIn(EventStatus.ON_SALE);

            Optional<EventPoster> previous = published.changePoster(NEW_POSTER, LAST_MODIFIED_AT);

            assertThat(EventChangeHistory.posterVersion(published, previous)).contains(
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.POSTER, null, POSTER.fileKey(), NEW_POSTER.fileKey()));
            assertThat(EventChangeHistory.posterVersion(published, Optional.of(NEW_POSTER))).isEmpty();
        }

        @Test
        @DisplayName("3/14 출연을 \"주연: 김배우\"에서 \"주연: 이배우\"로 고치면 그 회차의 출연 버전 — 비운 3/15에 적거나 다시 비워도 쌓는다")
        void performersChange() {
            Event published = springPlayIn(EventStatus.ON_SALE);
            EventSchedule schedule = doubleCastSchedule();

            published.changeSessionPerformers(schedule, 101, LEE, LAST_MODIFIED_AT);
            assertThat(EventChangeHistory.performersVersion(published, schedule, 101, Optional.of(KIM))).contains(
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, 101L, "주연: 김배우", "주연: 이배우"));

            published.changeSessionPerformers(schedule, 102, KIM, LAST_MODIFIED_AT);
            assertThat(EventChangeHistory.performersVersion(published, schedule, 102, Optional.empty())).contains(
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, 102L, null, "주연: 김배우"));

            published.changeSessionPerformers(schedule, 102, null, LAST_MODIFIED_AT);
            assertThat(EventChangeHistory.performersVersion(published, schedule, 102, Optional.of(KIM))).contains(
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, 102L, "주연: 김배우", null));
            assertThat(EventChangeHistory.performersVersion(published, schedule, 102, Optional.empty())).isEmpty();
        }
    }

    @Nested
    @DisplayName("운영자의 제목 변경은 소유자에게 알린다")
    class OwnerNotice {

        private final EventChangeHistoryVersion titleChange =
                new EventChangeHistoryVersion(EventChangeHistoryTarget.TITLE, null, "○○밴드 단독공연", "○○밴드 앵콜공연");

        @Test
        @DisplayName("운영자가 제목을 바꾸면 알린다 — 소유자가 직접 바꾸면 알리지 않는다")
        void operatorTitleChange() {
            assertThat(EventChangeHistory.notifiesOwner(titleChange, StaffRole.OPERATOR)).isTrue();
            assertThat(EventChangeHistory.notifiesOwner(titleChange, StaffRole.OWNER)).isFalse();
        }

        @Test
        @DisplayName("제목이 아닌 칸이나 첫 버전은 운영자가 남겨도 알리지 않는다")
        void otherTargetsAndFirstVersion() {
            assertThat(EventChangeHistory.notifiesOwner(
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.INTRODUCTION, null, "주연: 김배우", "주연: 이배우"),
                    StaffRole.OPERATOR)).isFalse();
            assertThat(EventChangeHistory.notifiesOwner(
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.TITLE, null, null, "○○밴드 단독공연"), StaffRole.OPERATOR))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("보관은 행사가 끝난 날부터 6개월")
    class Retention {

        /** 10/14 19:00~21:00 공연 하나 — docs/03 예시의 "10/14 — 공연". */
        private EventSchedule octoberFourteenth(Integer endHour) {
            return EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of(
                    EventDate.restore(11, LocalDate.of(2026, 10, 14), null, List.of(session(101, 19, endHour, null, null)))));
        }

        @Test
        @DisplayName("10/14에 끝난 행사는 다음 해 4/14까지 보관하고, 6개월 + 1일인 4/15부터 지운다 — 하루 전인 4/14 23:59에는 지우지 않는다")
        void sixMonthsPlusOneDay() {
            EventSchedule schedule = octoberFourteenth(21);

            assertThat(EventChangeHistory.retainedThrough(korean(2026, 10, 14, 21, 0))).isEqualTo(LocalDate.of(2027, 4, 14));
            assertThat(EventChangeHistory.isPurgeDue(schedule, korean(2027, 4, 14, 23, 59))).isFalse();
            assertThat(EventChangeHistory.isPurgeDue(schedule, korean(2027, 4, 15, 0, 0))).isTrue();
            assertThat(EventChangeHistory.isPurgeDue(schedule, korean(2027, 10, 15, 4, 0))).isTrue();
        }

        @Test
        @DisplayName("바꾼 날이 아니라 끝난 날부터 센다 — 2/1에 제목을 바꿨어도 10/15 항의 때 이력이 남아 있다 (docs/03 예시)")
        void countsFromEventEndNotChange() {
            assertThat(EventChangeHistory.isPurgeDue(octoberFourteenth(21), korean(2026, 10, 15, 10, 0))).isFalse();
            assertThat(EventChangeHistory.isPurgeDue(octoberFourteenth(21), korean(2026, 8, 1, 10, 0))).isFalse();
        }

        @Test
        @DisplayName("한국 날짜로 센다 — 4/15 00:30(한국)은 UTC로 아직 4/14지만 지울 때다")
        void usesKoreanDate() {
            assertThat(EventChangeHistory.isPurgeDue(octoberFourteenth(21), Instant.parse("2027-04-14T15:30:00Z"))).isTrue();
        }

        @Test
        @DisplayName("달의 끝을 넘으면 그 달의 마지막 날까지다 — 8/31에 끝나면 2/28까지 보관하고 3/1부터 지운다")
        void monthEnd() {
            EventSchedule augustEnd = EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of(
                    EventDate.restore(11, LocalDate.of(2026, 8, 31), null, List.of(session(101, 19, 21, null, null)))));

            assertThat(EventChangeHistory.isPurgeDue(augustEnd, korean(2027, 2, 28, 23, 59))).isFalse();
            assertThat(EventChangeHistory.isPurgeDue(augustEnd, korean(2027, 3, 1, 0, 0))).isTrue();
        }

        @Test
        @DisplayName("끝난 날은 취소되지 않은 마지막 회차로 본다 — 3/15가 취소됐으면 3/14에 끝난 것이라 9/15부터 지운다")
        void lastNonCancelledSession() {
            EventSchedule schedule = scheduleOf(session(101, 19, 21, null, null), MARCH_14,
                    session(102, 19, 21, null, CANCELLED_AT), MARCH_15);

            assertThat(EventChangeHistory.isPurgeDue(schedule, korean(2026, 9, 14, 12, 0))).isFalse();
            assertThat(EventChangeHistory.isPurgeDue(schedule, korean(2026, 9, 15, 0, 0))).isTrue();
        }

        @Test
        @DisplayName("이력을 지울 때 함께 지울 포스터 파일은 포스터 버전의 바꾸기 전 포스터다 — 첫 버전 · 지금 포스터 · 다른 칸은 들지 않는다")
        void replacedPostersAreDiscardedWithHistory() {
            EventPoster second = new EventPoster("Sc7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.jpg");

            assertThat(EventChangeHistory.replacedPosters(List.of(
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.TITLE, null, null, "○○밴드 단독공연"),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.POSTER, null, null, POSTER.fileKey()),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.POSTER, null, POSTER.fileKey(), second.fileKey()),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.TITLE, null, "○○밴드 단독공연", "○○밴드 앵콜공연"),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.POSTER, null, second.fileKey(), NEW_POSTER.fileKey()))))
                    .containsExactly(POSTER, second);
        }

        @Test
        @DisplayName("끝난 날을 알 수 없으면 지우지 않는다 — 회차가 모두 취소됐거나, 마지막 회차의 종료 시각이 비어 있거나, 회차가 없으면")
        void keepsWhenEndUnknown() {
            Instant muchLater = korean(2030, 1, 1, 0, 0);
            EventSchedule allCancelled = scheduleOf(session(101, 19, 21, null, CANCELLED_AT), MARCH_14,
                    session(102, 19, 21, null, CANCELLED_AT), MARCH_15);

            assertThat(EventChangeHistory.isPurgeDue(allCancelled, muchLater)).isFalse();
            assertThat(EventChangeHistory.isPurgeDue(octoberFourteenth(null), muchLater)).isFalse();
            assertThat(EventChangeHistory.isPurgeDue(EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of()), muchLater))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("버전 한 줄")
    class Version {

        @Test
        @DisplayName("회차 출연만 회차를 가리키고, 행사에 하나인 칸은 회차가 없다")
        void sessionOnlyForPerformers() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, null, null, "주연: 김배우"));
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new EventChangeHistoryVersion(EventChangeHistoryTarget.TITLE, 101L, null, "○○밴드 단독공연"));
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, 0L, null, "주연: 김배우"));
        }

        @Test
        @DisplayName("제목 · 소개 · 포스터는 비울 수 없고, 바꾸기 전과 같은 내용은 새 버전이 아니다")
        void contentRules() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new EventChangeHistoryVersion(EventChangeHistoryTarget.POSTER, null, POSTER.fileKey(), null));
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new EventChangeHistoryVersion(EventChangeHistoryTarget.TITLE, null, "○○밴드 단독공연", "○○밴드 단독공연"));
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, 101L, null, null));
        }
    }
}
