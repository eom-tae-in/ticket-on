package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.TWELVE_MONTHS_ORGANIZER;
import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static com.ticketon.domain.event.EventFixtures.lateSummerFestival;
import static com.ticketon.domain.event.EventFixtures.twelveMonthsSpringPlay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("행사 (docs/03 \"기본 정보\", \"공개 수준 3단계\")")
class EventTest {

    private static final EventPublicAddress ADDRESS = new EventPublicAddress("Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0");
    private static final EventPasswordHash HASH_OF_0315 = new EventPasswordHash("{test}0315");

    private static Event newSpringPlay() {
        return Event.create(TWELVE_MONTHS_ORGANIZER, ADDRESS, twelveMonthsSpringPlay());
    }

    @Nested
    @DisplayName("만들기")
    class Create {

        @Test
        @DisplayName("1단계 기본 정보로 만든다 — 저장 전이라 id가 없고, 포스터는 아직 없고, 공개로 시작한다")
        void createsWithBasicInformation() {
            Event event = newSpringPlay();

            assertThat(event.id()).isEmpty();
            assertThat(event.organizerId()).isEqualTo(TWELVE_MONTHS_ORGANIZER);
            assertThat(event.publicAddress()).isEqualTo(ADDRESS);
            assertThat(event.basicInformation()).isEqualTo(twelveMonthsSpringPlay());
            assertThat(event.poster()).isEmpty();
            assertThat(event.visibility()).isEqualTo(EventVisibilitySetting.publicByDefault());
        }

        @Test
        @DisplayName("저장소에서 되살리면 저장된 값 그대로다")
        void restores() {
            EventVisibilitySetting protectedSetting = new EventVisibilitySetting(EventVisibility.PASSWORD_PROTECTED, HASH_OF_0315);

            Event event = Event.restore(7L, TWELVE_MONTHS_ORGANIZER, ADDRESS, twelveMonthsSpringPlay(),
                    new EventPoster("poster-key.jpg"), protectedSetting);

            assertThat(event.id()).hasValue(7L);
            assertThat(event.poster()).contains(new EventPoster("poster-key.jpg"));
            assertThat(event.visibility()).isEqualTo(protectedSetting);
        }

        @Test
        @DisplayName("행사 id와 주최자 id는 1 이상이다")
        void idsArePositive() {
            assertThatIllegalArgumentException().isThrownBy(() -> Event.create(0L, ADDRESS, twelveMonthsSpringPlay()));
            assertThatIllegalArgumentException().isThrownBy(() -> Event.restore(0L, TWELVE_MONTHS_ORGANIZER, ADDRESS,
                    twelveMonthsSpringPlay(), null, EventVisibilitySetting.publicByDefault()));
        }
    }

    @Nested
    @DisplayName("기본 정보와 포스터")
    class BasicInformationAndPoster {

        @Test
        @DisplayName("포스터를 올리기 전에는 1단계 필수 항목이 다 차지 않았다 — 올리면 다 찬다")
        void posterCompletesBasicInformation() {
            Event event = newSpringPlay();
            assertThat(event.isBasicInformationComplete()).isFalse();

            event.changePoster(new EventPoster("poster-key.jpg"));

            assertThat(event.isBasicInformationComplete()).isTrue();
        }

        @Test
        @DisplayName("포스터를 바꾸면 바꾸기 전 포스터를 돌려준다 — 처음 올리면 빈 값")
        void changePosterReturnsPrevious() {
            Event event = newSpringPlay();

            assertThat(event.changePoster(new EventPoster("first.jpg"))).isEmpty();
            assertThat(event.changePoster(new EventPoster("second.jpg"))).contains(new EventPoster("first.jpg"));
            assertThat(event.poster()).contains(new EventPoster("second.jpg"));
        }

        @Test
        @DisplayName("기본 정보를 바꾸면 true, 지금과 같으면 그대로 두고 false")
        void changeBasicInformation() {
            Event event = newSpringPlay();
            EventBasicInformation base = twelveMonthsSpringPlay();
            EventBasicInformation retitled = new EventBasicInformation(EventTitle.of("2026 봄 정기공연 〈열두 번째 달〉 앵콜"),
                    base.type(), base.category(), base.introduction(), base.venue(), base.venueDetails(), base.mapLink(),
                    base.publicContact());

            assertThat(event.changeBasicInformation(twelveMonthsSpringPlay())).isFalse();
            assertThat(event.changeBasicInformation(retitled)).isTrue();
            assertThat(event.basicInformation().title().value()).isEqualTo("2026 봄 정기공연 〈열두 번째 달〉 앵콜");
        }
    }

    @Nested
    @DisplayName("공개 수준")
    class Visibility {

        @Test
        @DisplayName("암호 보호인데 암호가 없으면 막는다")
        void rejectsPasswordProtectedWithoutPassword() {
            Event event = newSpringPlay();

            assertRejected(() -> event.changeVisibility(EventVisibility.PASSWORD_PROTECTED, null),
                    EventErrorCode.EVENT_PASSWORD_REQUIRED);
            assertThat(event.visibility()).isEqualTo(EventVisibilitySetting.publicByDefault());
        }

        @Test
        @DisplayName("암호 보호로 바꿔도 공개 주소는 그대로다 — 공개로 뿌린 링크가 같은 행사를 연다")
        void publicAddressStaysWhenVisibilityChanges() {
            Event event = newSpringPlay();

            assertThat(event.changeVisibility(EventVisibility.PASSWORD_PROTECTED, HASH_OF_0315)).isTrue();

            assertThat(event.visibility().requiresPassword()).isTrue();
            assertThat(event.visibility().isListed()).isFalse();
            assertThat(event.publicAddress()).isEqualTo(ADDRESS);
        }

        @Test
        @DisplayName("이미 암호 보호면 암호를 새로 적지 않아도 그대로 두고, 바뀐 것이 없으면 false")
        void keepsPasswordWhenStillProtected() {
            Event event = newSpringPlay();
            event.changeVisibility(EventVisibility.PASSWORD_PROTECTED, HASH_OF_0315);

            assertThat(event.changeVisibility(EventVisibility.PASSWORD_PROTECTED, null)).isFalse();
            assertThat(event.visibility().passwordHash()).isEqualTo(HASH_OF_0315);
        }

        @Test
        @DisplayName("링크 전용으로 바꾸면 목록에서 빠지고 암호는 없다")
        void linkOnly() {
            Event event = newSpringPlay();

            event.changeVisibility(EventVisibility.LINK_ONLY, null);

            assertThat(event.visibility()).isEqualTo(new EventVisibilitySetting(EventVisibility.LINK_ONLY, null));
        }
    }

    @Nested
    @DisplayName("화면별 이름")
    class ScreenName {

        @Test
        @DisplayName("관객 화면에서는 유형 이름, 주최자 화면에서는 '행사'로 부른다")
        void nameOnScreen() {
            Event play = newSpringPlay();
            Event festival = Event.create(TWELVE_MONTHS_ORGANIZER, ADDRESS, lateSummerFestival());

            assertThat(play.nameOn(ScreenSide.AUDIENCE)).isEqualTo("공연");
            assertThat(festival.nameOn(ScreenSide.AUDIENCE)).isEqualTo("페스티벌");
            assertThat(play.nameOn(ScreenSide.ORGANIZER)).isEqualTo("행사");
            assertThat(festival.nameOn(ScreenSide.ORGANIZER)).isEqualTo("행사");
        }

        @Test
        @DisplayName("유형을 바꾸면 관객 화면의 이름도 따라 바뀐다 — 공연을 전시로")
        void nameFollowsType() {
            Event event = newSpringPlay();
            EventBasicInformation base = twelveMonthsSpringPlay();

            event.changeBasicInformation(new EventBasicInformation(base.title(), EventType.EXHIBITION,
                    EventCategoryCatalog.initial().select(EventType.EXHIBITION, "사진", null), base.introduction(),
                    base.venue(), null, null, null));

            assertThat(event.nameOn(ScreenSide.AUDIENCE)).isEqualTo("전시");
        }
    }
}
