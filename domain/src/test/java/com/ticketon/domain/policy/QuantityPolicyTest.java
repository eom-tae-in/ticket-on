package com.ticketon.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.ImageFormat;
import java.time.Period;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("정책 숫자 — 수량·비율 (docs/99 C)")
class QuantityPolicyTest {

    @Test
    @DisplayName("1회 선택 매수 기본값은 4석이다 (docs/06)")
    void defaultMaxSeatsPerSelection() {
        assertThat(QuantityPolicy.DEFAULT_MAX_SEATS_PER_SELECTION).isEqualTo(4);
    }

    @Test
    @DisplayName("1인 총 매수 기본값은 4매다 (docs/06)")
    void defaultMaxTicketsPerPerson() {
        assertThat(QuantityPolicy.DEFAULT_MAX_TICKETS_PER_PERSON).isEqualTo(4);
    }

    @Test
    @DisplayName("1회 선택 매수 경고선은 8석 또는 전체의 10%다 (docs/06)")
    void seatsPerSelectionWarning() {
        assertThat(QuantityPolicy.SEATS_PER_SELECTION_WARNING).isEqualTo(new QuantityThreshold(8, Percent.of(10)));
    }

    @Test
    @DisplayName("조기예매 임박은 30% / 10% 또는 10장 / 종료 3일이다 (docs/05)")
    void earlyBirdIndicators() {
        assertThat(QuantityPolicy.EARLY_BIRD_RUNNING_LOW).isEqualTo(Percent.of(30));
        assertThat(QuantityPolicy.EARLY_BIRD_ALMOST_GONE).isEqualTo(new QuantityThreshold(10, Percent.of(10)));
        assertThat(QuantityPolicy.EARLY_BIRD_ENDING_SOON).isEqualTo(Period.ofDays(3));
    }

    @Test
    @DisplayName("잔여 배지는 10% 또는 10석이다 (docs/13)")
    void remainingSeatsBadge() {
        assertThat(QuantityPolicy.REMAINING_SEATS_BADGE).isEqualTo(new QuantityThreshold(10, Percent.of(10)));
    }

    @Test
    @DisplayName("묶음권 할인율 기본값은 2일 3%, 3일 7%, 4일 10%다 (docs/05)")
    void defaultBundleDiscounts() {
        assertThat(QuantityPolicy.DEFAULT_BUNDLE_DISCOUNT_TWO_DAYS).isEqualTo(Percent.of(3));
        assertThat(QuantityPolicy.DEFAULT_BUNDLE_DISCOUNT_THREE_DAYS).isEqualTo(Percent.of(7));
        assertThat(QuantityPolicy.DEFAULT_BUNDLE_DISCOUNT_FOUR_DAYS).isEqualTo(Percent.of(10));
    }

    @Test
    @DisplayName("퍼센트 할인은 100원 단위로 내린다 (docs/05)")
    void percentDiscountRoundingUnit() {
        assertThat(QuantityPolicy.PERCENT_DISCOUNT_ROUNDING_UNIT_WON).isEqualTo(100L);
    }

    @Test
    @DisplayName("포스터는 3:4 세로다 (docs/03)")
    void posterAspectRatio() {
        assertThat(QuantityPolicy.POSTER_ASPECT_RATIO).isEqualTo(new AspectRatio(3, 4));
        assertThat(QuantityPolicy.POSTER_ASPECT_RATIO.isPortrait()).isTrue();
    }

    @Test
    @DisplayName("연령대 통계는 예매자 30명 이상일 때 보인다 (docs/11)")
    void ageGroupStatsMinimumBookers() {
        assertThat(QuantityPolicy.AGE_GROUP_STATS_MINIMUM_BOOKERS).isEqualTo(30);
    }

    @Test
    @DisplayName("배상은 입장료의 10%다 (docs/08)")
    void compensationRate() {
        assertThat(QuantityPolicy.COMPENSATION_RATE).isEqualTo(Percent.of(10));
    }

    @Test
    @DisplayName("관객은 만 14세부터 가입할 수 있다 (docs/01, docs/99 A-3)")
    void minimumSignupAge() {
        assertThat(QuantityPolicy.MINIMUM_SIGNUP_AGE).isEqualTo(14);
    }

    @Test
    @DisplayName("첫 글자 표시의 색은 8가지다 — 주최자 로고와 운영진 프로필이 같은 팔레트 (docs/01)")
    void initialAvatarColorCount() {
        assertThat(QuantityPolicy.INITIAL_AVATAR_COLOR_COUNT).isEqualTo(8);
    }

    @Test
    @DisplayName("비밀번호는 8자 이상, 블록은 최대 50행 × 50열, 정산 계좌는 끝 4자리만 보인다 (docs/99 C)")
    void passwordBlockSizeAndAccountDigits() {
        assertThat(QuantityPolicy.PASSWORD_MINIMUM_LENGTH).isEqualTo(8);
        assertThat(QuantityPolicy.SEAT_BLOCK_MAXIMUM_ROWS).isEqualTo(50);
        assertThat(QuantityPolicy.SEAT_BLOCK_MAXIMUM_COLUMNS).isEqualTo(50);
        assertThat(QuantityPolicy.SETTLEMENT_ACCOUNT_VISIBLE_TRAILING_DIGITS).isEqualTo(4);
    }

    @Test
    @DisplayName("글자 수 상한은 제목 · 출연 100자, 소개 · 판매 중단 사유 50자, 닉네임 · 카테고리 · 블록 · 구역 · 입구 이름 · 쿠폰 코드 20자다 (docs/99 C \"입력·파일 상한\")")
    void textLengthLimits() {
        assertThat(QuantityPolicy.EVENT_TITLE_MAXIMUM_LENGTH).isEqualTo(100);
        assertThat(QuantityPolicy.SESSION_PERFORMERS_MAXIMUM_LENGTH).isEqualTo(100);
        assertThat(QuantityPolicy.STAFF_INTRODUCTION_MAXIMUM_LENGTH).isEqualTo(50);
        assertThat(QuantityPolicy.SALES_PAUSE_REASON_MAXIMUM_LENGTH).isEqualTo(50);
        assertThat(QuantityPolicy.NICKNAME_MAXIMUM_LENGTH).isEqualTo(20);
        assertThat(QuantityPolicy.EVENT_CATEGORY_NAME_MAXIMUM_LENGTH).isEqualTo(20);
        assertThat(QuantityPolicy.BLOCK_NAME_MAXIMUM_LENGTH).isEqualTo(20);
        assertThat(QuantityPolicy.ZONE_NAME_MAXIMUM_LENGTH).isEqualTo(20);
        assertThat(QuantityPolicy.ENTRANCE_NAME_MAXIMUM_LENGTH).isEqualTo(20);
        assertThat(QuantityPolicy.COUPON_CODE_MAXIMUM_LENGTH).isEqualTo(20);
    }

    @Test
    @DisplayName("파일 상한은 로고 2MB, 포스터 5MB(3:4에서 1% 오차), 요청 하나 10MB, 그림은 PNG · JPEG만이다 (docs/99 C \"입력·파일 상한\")")
    void fileLimits() {
        assertThat(QuantityPolicy.ORGANIZER_LOGO_MAXIMUM_SIZE).isEqualTo(FileSize.ofMegabytes(2));
        assertThat(QuantityPolicy.EVENT_POSTER_MAXIMUM_SIZE).isEqualTo(FileSize.ofMegabytes(5));
        assertThat(QuantityPolicy.EVENT_POSTER_ASPECT_RATIO_TOLERANCE).isEqualTo(Percent.of(1));
        assertThat(QuantityPolicy.UPLOAD_REQUEST_MAXIMUM_SIZE).isEqualTo(FileSize.ofMegabytes(10));
        assertThat(QuantityPolicy.ORGANIZER_LOGO_ACCEPTED_FORMATS).containsExactlyInAnyOrder(ImageFormat.PNG, ImageFormat.JPEG);
        assertThat(QuantityPolicy.EVENT_POSTER_ACCEPTED_FORMATS).containsExactlyInAnyOrder(ImageFormat.PNG, ImageFormat.JPEG);
    }
}
