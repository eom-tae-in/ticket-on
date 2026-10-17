package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static com.ticketon.domain.event.EventFixtures.lateSummerFestival;
import static com.ticketon.domain.event.EventFixtures.twelveMonthsSpringPlay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("행사 기본 정보 (docs/03 \"기본 정보\")")
class EventBasicInformationTest {

    @Nested
    @DisplayName("필수와 선택")
    class RequiredAndOptional {

        @Test
        @DisplayName("필수 항목과 선택 항목을 모두 적으면 그대로 담는다")
        void holdsAllItems() {
            EventBasicInformation information = twelveMonthsSpringPlay();

            assertThat(information.title().value()).isEqualTo("2026 봄 정기공연 〈열두 번째 달〉");
            assertThat(information.type()).isEqualTo(EventType.PERFORMANCE);
            assertThat(information.category().categoryName()).isEqualTo("연극");
            assertThat(information.introduction()).startsWith("극단 열두달이").contains("\n");
            assertThat(information.venue()).isEqualTo(new Venue("○○아트홀", "서울특별시 종로구 대학로 12"));
            assertThat(information.venueDetails()).isEqualTo("지하 1층, 주차 불가");
            assertThat(information.mapLink()).isEqualTo(ExternalLink.of("https://map.example/place/12"));
            assertThat(information.publicContact()).isEqualTo(EventPublicContact.of("twelve-months@example.com"));
        }

        @Test
        @DisplayName("상세 안내 · 지도 링크 · 공개용 연락처는 선택이라 비워도 된다")
        void optionalItemsMayBeAbsent() {
            EventBasicInformation information = lateSummerFestival();

            assertThat(information.venueDetails()).isNull();
            assertThat(information.mapLink()).isNull();
            assertThat(information.publicContact()).isNull();
        }

        @ParameterizedTest(name = "{0}이(가) 없으면 실패")
        @DisplayName("필수 항목(제목 · 유형 · 카테고리 · 소개 · 장소)이 빠지면 받지 않는다")
        @ValueSource(strings = {"제목", "유형", "카테고리", "소개", "장소"})
        void rejectsMissingRequiredItem(String missingItem) {
            assertThatNullPointerException().isThrownBy(() -> withoutRequiredItem(missingItem));
        }

        @Test
        @DisplayName("공백뿐인 제목 · 소개 · 공연장 이름 · 주소는 적지 않은 것으로 보고 받지 않는다")
        void rejectsBlankRequiredText() {
            EventBasicInformation base = twelveMonthsSpringPlay();

            assertThatIllegalArgumentException().isThrownBy(() -> EventTitle.of("   "));
            assertThatIllegalArgumentException().isThrownBy(() -> new EventBasicInformation(base.title(), base.type(),
                    base.category(), " \n ", base.venue(), null, null, null));
            assertThatIllegalArgumentException().isThrownBy(() -> new Venue("  ", "서울특별시 종로구 대학로 12"));
            assertThatIllegalArgumentException().isThrownBy(() -> new Venue("○○아트홀", " "));
        }

        @Test
        @DisplayName("소개 · 장소 · 상세 안내는 앞뒤 공백을 걷고, 공백뿐인 상세 안내는 없는 것으로 둔다")
        void stripsTextAndTreatsBlankDetailsAsAbsent() {
            EventBasicInformation base = twelveMonthsSpringPlay();

            EventBasicInformation information = new EventBasicInformation(base.title(), base.type(), base.category(),
                    "  소개 글  ", new Venue(" ○○아트홀 ", " 서울특별시 종로구 대학로 12 "), "   ", null, null);

            assertThat(information.introduction()).isEqualTo("소개 글");
            assertThat(information.venue()).isEqualTo(new Venue("○○아트홀", "서울특별시 종로구 대학로 12"));
            assertThat(information.venueDetails()).isNull();
            assertThat(new EventBasicInformation(base.title(), base.type(), base.category(), "소개", base.venue(),
                    " 주차 불가 ", null, null).venueDetails()).isEqualTo("주차 불가");
        }

        private EventBasicInformation withoutRequiredItem(String missingItem) {
            EventBasicInformation base = twelveMonthsSpringPlay();
            return new EventBasicInformation(
                    missingItem.equals("제목") ? null : base.title(),
                    missingItem.equals("유형") ? null : base.type(),
                    missingItem.equals("카테고리") ? null : base.category(),
                    missingItem.equals("소개") ? null : base.introduction(),
                    missingItem.equals("장소") ? null : base.venue(),
                    base.venueDetails(), base.mapLink(), base.publicContact());
        }
    }

    @Nested
    @DisplayName("유형과 카테고리")
    class TypeAndCategory {

        @Test
        @DisplayName("유형과 맞지 않는 카테고리는 막는다 — 공연에 전시의 회화")
        void rejectsCategoryOfAnotherType() {
            EventBasicInformation base = twelveMonthsSpringPlay();
            EventCategorySelection painting = EventCategoryCatalog.initial().select(EventType.EXHIBITION, "회화", null);

            assertRejected(() -> new EventBasicInformation(base.title(), EventType.PERFORMANCE, painting, base.introduction(),
                    base.venue(), null, null, null), EventErrorCode.EVENT_CATEGORY_NOT_IN_TYPE);
        }

        @Test
        @DisplayName("유형을 바꾸면 카테고리도 그 유형의 것으로 다시 골라야 한다 — 연극을 고른 채 전시로 바꾸면 막는다")
        void changingTypeRequiresCategoryOfThatType() {
            EventBasicInformation base = twelveMonthsSpringPlay();

            assertRejected(() -> new EventBasicInformation(base.title(), EventType.EXHIBITION, base.category(),
                    base.introduction(), base.venue(), null, null, null), EventErrorCode.EVENT_CATEGORY_NOT_IN_TYPE);
        }

        @Test
        @DisplayName("'기타' + 장르명도 카테고리가 된다 — 공연 · 기타 · 낭독극")
        void otherWithGenreName() {
            EventBasicInformation base = twelveMonthsSpringPlay();
            EventCategorySelection reading = EventCategoryCatalog.initial().select(EventType.PERFORMANCE, "기타", "낭독극");

            EventBasicInformation information = new EventBasicInformation(base.title(), EventType.PERFORMANCE, reading,
                    base.introduction(), base.venue(), null, null, null);

            assertThat(information.category().isOther()).isTrue();
            assertThat(information.category().genreName().value()).isEqualTo("낭독극");
        }
    }

    @Nested
    @DisplayName("제목")
    class Title {

        @Test
        @DisplayName("앞뒤 공백을 걷고 한글 자모 꼴(NFD)을 완성형(NFC)으로 맞춘다 — 검색에서 같은 제목으로 찾히게")
        void normalizes() {
            String decomposed = Normalizer.normalize("○○밴드 단독공연", Normalizer.Form.NFD);

            assertThat(EventTitle.of("  " + decomposed + " ")).isEqualTo(EventTitle.of("○○밴드 단독공연"));
        }

        @Test
        @DisplayName("제목은 " + ProposedPolicy.EVENT_TITLE_MAXIMUM_LENGTH + "자까지 받는다 (확인 필요) — 글자 수는 사람이 세는 대로")
        void maximumLength() {
            String longest = "공".repeat(ProposedPolicy.EVENT_TITLE_MAXIMUM_LENGTH - 1) + "🎭";

            assertThat(EventTitle.of(longest).value()).isEqualTo(longest);
            assertThat(EventTitle.isValid(longest)).isTrue();
            assertThat(EventTitle.isValid(longest + "공")).isFalse();
            assertThatIllegalArgumentException().isThrownBy(() -> EventTitle.of(longest + "공"));
        }

        @Test
        @DisplayName("입력 검증 도우미는 생성 규칙과 같다")
        void isValidMatchesConstructor() {
            assertThat(EventTitle.isValid(null)).isFalse();
            assertThat(EventTitle.isValid("  ")).isFalse();
            assertThat(EventTitle.isValid(" ○○밴드 앵콜공연 ")).isTrue();
        }
    }

    @Nested
    @DisplayName("지도 링크 · 공개용 연락처 · 포스터")
    class OtherItems {

        @Test
        @DisplayName("지도 링크는 외부 지도의 http(s) 주소만 받는다 — 누구나 누르는 공개 페이지의 링크라서")
        void mapLinkIsWebAddress() {
            assertThat(ExternalLink.isValid("https://map.example/place/12")).isTrue();
            assertThat(ExternalLink.isValid("javascript:alert(1)")).isFalse();
        }

        @Test
        @DisplayName("공개용 연락처는 형식을 보지 않고 적은 그대로 두며, 문자열로 바꾸면 값을 가린다")
        void publicContactIsFreeTextAndMasked() {
            assertThat(EventPublicContact.of(" 010-2345-6789 ").value()).isEqualTo("010-2345-6789");
            assertThat(EventPublicContact.of("카카오톡 오픈채팅 '열두달'").value()).isEqualTo("카카오톡 오픈채팅 '열두달'");
            assertThatIllegalArgumentException().isThrownBy(() -> EventPublicContact.of("  "));
            assertThat(EventPublicContact.of("010-2345-6789")).hasToString("EventPublicContact[****]");
            assertThat(twelveMonthsSpringPlay().toString()).doesNotContain("twelve-months@example.com");
        }
    }
}
