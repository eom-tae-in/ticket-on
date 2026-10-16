package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("행사 카테고리 (docs/03 \"카테고리\")")
class EventCategoryTest {

    private final EventCategoryCatalog catalog = EventCategoryCatalog.initial();

    @Nested
    @DisplayName("처음 목록")
    class InitialCatalog {

        @Test
        @DisplayName("공연 — 연극 · 뮤지컬 · 콘서트 · 클래식 · 국악 · 무용 · 코미디")
        void performance() {
            assertThat(namesOf(EventType.PERFORMANCE)).containsExactly("연극", "뮤지컬", "콘서트", "클래식", "국악", "무용", "코미디");
        }

        @Test
        @DisplayName("전시 — 회화 · 사진 · 미디어아트 · 설치 · 공예")
        void exhibition() {
            assertThat(namesOf(EventType.EXHIBITION)).containsExactly("회화", "사진", "미디어아트", "설치", "공예");
        }

        @Test
        @DisplayName("페스티벌 — 음악 · 푸드 · 문화 · 지역축제")
        void festival() {
            assertThat(namesOf(EventType.FESTIVAL)).containsExactly("음악", "푸드", "문화", "지역축제");
        }

        @ParameterizedTest(name = "{0}")
        @DisplayName("유형마다 4~7개이고 '기타'는 목록에 들어 있지 않다 — 기타는 늘 끝에 붙는 따로 된 선택이다 (docs/99 C)")
        @EnumSource(EventType.class)
        void fourToSevenPerTypeWithoutOther(EventType type) {
            assertThat(namesOf(type)).hasSizeBetween(4, 7).doesNotContain("기타");
        }

        private List<String> namesOf(EventType type) {
            return catalog.categoriesOf(type).stream().map(category -> category.name().value()).toList();
        }
    }

    @Nested
    @DisplayName("정식 카테고리 고르기")
    class SelectFormal {

        @Test
        @DisplayName("유형의 목록에 있는 카테고리를 고른다 — 공연의 연극")
        void selectsCategoryInType() {
            EventCategorySelection selection = catalog.select(EventType.PERFORMANCE, "연극", null);

            assertThat(selection.type()).isEqualTo(EventType.PERFORMANCE);
            assertThat(selection.category()).isEqualTo(EventCategory.of(EventType.PERFORMANCE, "연극"));
            assertThat(selection.isOther()).isFalse();
            assertThat(selection.genreName()).isNull();
            assertThat(selection.categoryName()).isEqualTo("연극");
        }

        @ParameterizedTest(name = "{0} + {1}")
        @DisplayName("유형과 맞지 않는 카테고리는 막는다 — 다른 유형의 목록에만 있는 이름")
        @CsvSource({
                "PERFORMANCE, 회화",
                "EXHIBITION, 뮤지컬",
                "FESTIVAL, 연극",
                "PERFORMANCE, 지역축제"
        })
        void rejectsCategoryOfAnotherType(EventType type, String categoryName) {
            assertRejected(() -> catalog.select(type, categoryName, null), EventErrorCode.EVENT_CATEGORY_NOT_IN_TYPE);
        }

        @Test
        @DisplayName("아직 정식 카테고리가 아닌 이름은 막는다 — '기타'를 고르고 장르명으로 적는다")
        void rejectsNameNotYetPromoted() {
            assertRejected(() -> catalog.select(EventType.PERFORMANCE, "낭독극", null), EventErrorCode.EVENT_CATEGORY_NOT_IN_TYPE);
        }

        @Test
        @DisplayName("앞뒤 공백과 한글 자모 꼴(NFD)은 맞춰서 견준다")
        void matchesAfterNormalizing() {
            String decomposed = Normalizer.normalize("뮤지컬", Normalizer.Form.NFD);

            assertThat(catalog.select(EventType.PERFORMANCE, "  연극 ", null).categoryName()).isEqualTo("연극");
            assertThat(catalog.select(EventType.PERFORMANCE, decomposed, "").categoryName()).isEqualTo("뮤지컬");
        }

        @Test
        @DisplayName("정식 카테고리를 고르고 장르명을 적으면 막는다 — 빈 칸은 적지 않은 것으로 본다")
        void rejectsGenreNameForFormalCategory() {
            assertRejected(() -> catalog.select(EventType.PERFORMANCE, "연극", "낭독극"),
                    EventErrorCode.EVENT_GENRE_NAME_ONLY_FOR_OTHER);
            assertThat(catalog.select(EventType.PERFORMANCE, "연극", "  ").isOther()).isFalse();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @DisplayName("카테고리를 고르지 않으면 받지 않는다 — 필수 항목")
        @ValueSource(strings = {"", "   "})
        void rejectsMissingCategory(String categoryName) {
            assertThatIllegalArgumentException().isThrownBy(() -> catalog.select(EventType.PERFORMANCE, categoryName, null));
        }
    }

    @Nested
    @DisplayName("'기타'와 장르명")
    class SelectOther {

        @Test
        @DisplayName("'기타'를 고르고 장르명을 적는다 — 공연 · 기타 · 낭독극")
        void selectsOtherWithGenreName() {
            EventCategorySelection selection = catalog.select(EventType.PERFORMANCE, "기타", " 낭독극 ");

            assertThat(selection.type()).isEqualTo(EventType.PERFORMANCE);
            assertThat(selection.isOther()).isTrue();
            assertThat(selection.category()).isNull();
            assertThat(selection.genreName()).isEqualTo(EventCategoryName.of("낭독극"));
            assertThat(selection.categoryName()).isEqualTo("기타");
        }

        @ParameterizedTest(name = "{0}")
        @DisplayName("모든 유형에서 '기타'를 고를 수 있다")
        @EnumSource(EventType.class)
        void otherIsAvailableForEveryType(EventType type) {
            assertThat(catalog.select(type, "기타", "무언가").isOther()).isTrue();
        }

        @ParameterizedTest(name = "장르명 \"{0}\"")
        @DisplayName("'기타'를 고르고 장르명을 적지 않으면 막는다")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void rejectsOtherWithoutGenreName(String genreName) {
            assertRejected(() -> catalog.select(EventType.PERFORMANCE, "기타", genreName), EventErrorCode.EVENT_GENRE_NAME_REQUIRED);
            assertRejected(() -> EventCategorySelection.other(EventType.EXHIBITION, genreName),
                    EventErrorCode.EVENT_GENRE_NAME_REQUIRED);
        }

        @Test
        @DisplayName("장르명은 NFC로 맞춰 둔다 — 관리자의 '기타' 장르명 목록에서 같은 이름이 따로 세어지지 않게")
        void genreNameIsNormalized() {
            String decomposed = Normalizer.normalize("낭독극", Normalizer.Form.NFD);

            EventCategorySelection selection = EventCategorySelection.other(EventType.PERFORMANCE, decomposed);

            assertThat(selection.genreName()).isEqualTo(EventCategoryName.of("낭독극"));
        }

        @Test
        @DisplayName("장르명은 " + ProposedPolicy.EVENT_CATEGORY_NAME_MAXIMUM_LENGTH + "자까지 받는다 (확인 필요)")
        void genreNameMaximumLength() {
            String longest = "가".repeat(ProposedPolicy.EVENT_CATEGORY_NAME_MAXIMUM_LENGTH);

            assertThat(EventCategorySelection.other(EventType.PERFORMANCE, longest).genreName().value()).isEqualTo(longest);
            assertThat(EventCategoryName.isValid(longest)).isTrue();
            assertThat(EventCategoryName.isValid(longest + "가")).isFalse();
            assertThatIllegalArgumentException().isThrownBy(() -> EventCategorySelection.other(EventType.PERFORMANCE, longest + "가"));
        }
    }

    @Nested
    @DisplayName("목록은 데이터다")
    class CatalogIsData {

        @Test
        @DisplayName("관리자가 승격한 카테고리는 목록 끝에 붙어 고를 수 있게 된다 — 공연의 낭독극")
        void promotedCategoryBecomesSelectable() {
            List<EventCategory> categories = new ArrayList<>(catalog.categories());
            categories.add(EventCategory.of(EventType.PERFORMANCE, "낭독극"));
            EventCategoryCatalog promoted = new EventCategoryCatalog(categories);

            assertThat(promoted.categoriesOf(EventType.PERFORMANCE)).last()
                    .isEqualTo(EventCategory.of(EventType.PERFORMANCE, "낭독극"));
            assertThat(promoted.select(EventType.PERFORMANCE, "낭독극", null).categoryName()).isEqualTo("낭독극");
            assertRejected(() -> promoted.select(EventType.EXHIBITION, "낭독극", null), EventErrorCode.EVENT_CATEGORY_NOT_IN_TYPE);
        }

        @Test
        @DisplayName("같은 유형에 같은 이름의 카테고리는 둘일 수 없다 — 다른 유형끼리는 겹쳐도 된다")
        void duplicatesRejectedWithinType() {
            assertThatIllegalArgumentException().isThrownBy(() -> new EventCategoryCatalog(List.of(
                    EventCategory.of(EventType.PERFORMANCE, "연극"), EventCategory.of(EventType.PERFORMANCE, " 연극"))));

            EventCategoryCatalog sameNameAcrossTypes = new EventCategoryCatalog(List.of(
                    EventCategory.of(EventType.FESTIVAL, "음악"), EventCategory.of(EventType.PERFORMANCE, "음악")));
            assertThat(sameNameAcrossTypes.categories()).hasSize(2);
        }

        @Test
        @DisplayName("'기타'는 정식 카테고리 이름으로 쓸 수 없다")
        void otherIsNotAFormalCategory() {
            assertThatIllegalArgumentException().isThrownBy(() -> EventCategory.of(EventType.PERFORMANCE, " 기타 "));
        }

        @Test
        @DisplayName("같은 이름도 유형이 다르면 다른 카테고리다")
        void categoryIdentityIncludesType() {
            assertThat(EventCategory.of(EventType.FESTIVAL, "음악")).isNotEqualTo(EventCategory.of(EventType.PERFORMANCE, "음악"));
        }
    }

    @Nested
    @DisplayName("카테고리 선택의 꼴")
    class SelectionShape {

        @Test
        @DisplayName("정식 카테고리와 장르명 중 하나만 가진다")
        void exactlyOneOfCategoryOrGenreName() {
            EventCategory drama = EventCategory.of(EventType.PERFORMANCE, "연극");

            assertThatIllegalArgumentException().isThrownBy(
                    () -> new EventCategorySelection(EventType.PERFORMANCE, drama, EventCategoryName.of("낭독극")));
            assertThatIllegalArgumentException().isThrownBy(() -> new EventCategorySelection(EventType.PERFORMANCE, null, null));
        }

        @Test
        @DisplayName("정식 카테고리는 선택의 유형과 같은 유형이어야 한다")
        void categoryMustMatchSelectionType() {
            EventCategory painting = EventCategory.of(EventType.EXHIBITION, "회화");

            assertThatIllegalArgumentException().isThrownBy(() -> new EventCategorySelection(EventType.PERFORMANCE, painting, null));
            assertThat(EventCategorySelection.of(painting).type()).isEqualTo(EventType.EXHIBITION);
        }
    }
}
