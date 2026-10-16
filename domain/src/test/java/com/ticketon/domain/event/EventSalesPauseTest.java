package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static com.ticketon.domain.event.EventStatusFixtures.ADMIN_ACCOUNT_ID;
import static com.ticketon.domain.event.EventStatusFixtures.BEFORE_OPEN;
import static com.ticketon.domain.event.EventStatusFixtures.DURING_SALES;
import static com.ticketon.domain.event.EventStatusFixtures.MARCH_14;
import static com.ticketon.domain.event.EventStatusFixtures.OPENS_AT;
import static com.ticketon.domain.event.EventStatusFixtures.PAUSED_AT;
import static com.ticketon.domain.event.EventStatusFixtures.STAFF_ACCOUNT_ID;
import static com.ticketon.domain.event.EventStatusFixtures.korean;
import static com.ticketon.domain.event.EventStatusFixtures.opensAtMarchFirst;
import static com.ticketon.domain.event.EventStatusFixtures.singlePerformance;
import static com.ticketon.domain.event.EventStatusFixtures.springPlayIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.policy.ProposedPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("판매 중단 · 재개 — 주최자 중단과 관리자 조치, 중단 이력 (docs/04 \"판매 중단\", docs/01 \"관리자가 멈춘 판매는 주최자가 풀 수 없습니다\")")
class EventSalesPauseTest {

    private static final SalesPauseReason CAST_ILLNESS = new SalesPauseReason("출연진 사정으로 잠시 판매를 멈춥니다");
    private static final Instant RESUMED_AT = korean(LocalDate.of(2026, 3, 11), 9, 0);
    private static final Instant RELEASED_AT = korean(LocalDate.of(2026, 3, 11), 8, 0);

    private static Event onSale() {
        return springPlayIn(EventStatus.ON_SALE);
    }

    @Nested
    @DisplayName("주최자 중단")
    class OrganizerPause {

        @Test
        @DisplayName("한 줄 사유와 함께 멈추면 누가 · 언제 · 왜 멈췄는지 이력에 남고, 관객에게 보일 사유가 된다")
        void pauseWithReason() {
            Event event = onSale();

            event.pauseSales(STAFF_ACCOUNT_ID, CAST_ILLNESS, singlePerformance(), DURING_SALES);

            assertThat(event.status()).isEqualTo(EventStatus.SALES_PAUSED);
            assertThat(event.salesPauses()).containsExactly(
                    new SalesPause(SalesPauseCause.ORGANIZER, STAFF_ACCOUNT_ID, CAST_ILLNESS, DURING_SALES, null));
            assertThat(event.activeOrganizerPause()).map(SalesPause::reason).contains(CAST_ILLNESS);
            assertThat(event.activeSalesHolds()).isEmpty();
        }

        @Test
        @DisplayName("사유는 선택이다 — 적지 않아도 멈춘다")
        void pauseWithoutReason() {
            Event event = onSale();

            event.pauseSales(STAFF_ACCOUNT_ID, null, singlePerformance(), DURING_SALES);

            assertThat(event.status()).isEqualTo(EventStatus.SALES_PAUSED);
            assertThat(event.salesPauses().getFirst().reason()).isNull();
        }

        @Test
        @DisplayName("재개하면 판매 중으로 돌아가고, 중단 이력은 재개한 시각으로 끝나 남는다")
        void resume() {
            Event event = onSale();
            event.pauseSales(STAFF_ACCOUNT_ID, CAST_ILLNESS, singlePerformance(), DURING_SALES);

            event.resumeSales(singlePerformance(), RESUMED_AT);

            assertThat(event.status()).isEqualTo(EventStatus.ON_SALE);
            assertThat(event.salesPauses()).containsExactly(
                    new SalesPause(SalesPauseCause.ORGANIZER, STAFF_ACCOUNT_ID, CAST_ILLNESS, DURING_SALES, RESUMED_AT));
            assertThat(event.activeOrganizerPause()).isEmpty();
        }

        @Test
        @DisplayName("[재개] 버튼은 판매 중단 중에만 누를 수 있다")
        void resumeOption() {
            Event event = onSale();
            assertThat(event.salesResumeOption(singlePerformance(), DURING_SALES)).isEqualTo(SalesResumeOption.NOT_PAUSED);

            event.pauseSales(STAFF_ACCOUNT_ID, null, singlePerformance(), DURING_SALES);

            assertThat(event.salesResumeOption(singlePerformance(), DURING_SALES)).isEqualTo(SalesResumeOption.AVAILABLE);
        }
    }

    @Nested
    @DisplayName("시간이 이긴다 — 중단 중에 마지막 회차의 예매 종료가 지나면")
    class TimeBeatsPause {

        @Test
        @DisplayName("판매 종료가 되고, 재개할 수 없고, [재개] 버튼이 사라지지만 중단 이력은 남는다")
        void pausedEventClosesByTime() {
            Event event = springPlayIn(EventStatus.SALES_PAUSED);
            Instant salesEnded = korean(MARCH_14, 18, 0);

            assertThat(event.salesResumeOption(singlePerformance(), salesEnded)).isEqualTo(SalesResumeOption.NOT_PAUSED);
            assertRejected(() -> event.resumeSales(singlePerformance(), salesEnded), EventErrorCode.SALES_ALREADY_CLOSED);

            assertThat(event.status()).isEqualTo(EventStatus.SALES_CLOSED);
            assertThat(event.salesPauses()).containsExactly(
                    new SalesPause(SalesPauseCause.ORGANIZER, STAFF_ACCOUNT_ID, null, PAUSED_AT, null));
        }
    }

    @Nested
    @DisplayName("먼저 지금 시각을 반영한다 — 판매 중으로 저장돼 있어도 예매 종료가 지났으면 판매 종료")
    class TimeFirst {

        private final Instant salesEnded = korean(MARCH_14, 18, 0);

        @Test
        @DisplayName("주최자 중단은 판매 종료를 반영한 뒤 판매 중이 아니라 막힌다")
        void organizerPauseRejectedAfterSalesEnd() {
            Event event = onSale();

            assertRejected(() -> event.pauseSales(STAFF_ACCOUNT_ID, CAST_ILLNESS, singlePerformance(), salesEnded),
                    EventErrorCode.SALES_NOT_PAUSABLE);

            assertThat(event.status()).isEqualTo(EventStatus.SALES_CLOSED);
            assertThat(event.salesPauses()).isEmpty();
        }

        @Test
        @DisplayName("관리자 조치는 판매 종료를 반영한 뒤 멈출 판매가 없어 걸리지 않는다")
        void adminHoldIgnoredAfterSalesEnd() {
            Event event = onSale();

            assertThat(event.holdSales(SalesPauseCause.ADMIN_SUSPENSION, ADMIN_ACCOUNT_ID, singlePerformance(), salesEnded)).isFalse();

            assertThat(event.status()).isEqualTo(EventStatus.SALES_CLOSED);
            assertThat(event.activeSalesHolds()).isEmpty();
            assertThat(event.salesPauses()).isEmpty();
        }

        @Test
        @DisplayName("판매 대기로 저장돼 있어도 오픈 시각이 지났으면 판매 중을 반영한 뒤 주최자가 멈출 수 있다")
        void organizerPauseAfterOpeningReflected() {
            Event event = springPlayIn(EventStatus.AWAITING_OPEN);

            event.pauseSales(STAFF_ACCOUNT_ID, null, singlePerformance(), DURING_SALES);

            assertThat(event.status()).isEqualTo(EventStatus.SALES_PAUSED);
            assertThat(event.bookingOpenedAt()).contains(DURING_SALES);
        }
    }

    @Nested
    @DisplayName("관리자 조치 — 정지 2단계 · 페이지 내리기")
    class AdminHold {

        @Test
        @DisplayName("관리자가 멈춘 판매는 주최자가 재개할 수 없다 — [재개]에 '관리자 조치로 중단됨'")
        void organizerCannotResumeAdminHold() {
            Event event = onSale();

            event.holdSales(SalesPauseCause.ADMIN_SUSPENSION, ADMIN_ACCOUNT_ID, singlePerformance(), DURING_SALES);

            assertThat(event.status()).isEqualTo(EventStatus.SALES_PAUSED);
            assertThat(event.activeSalesHolds()).containsExactly(SalesPauseCause.ADMIN_SUSPENSION);
            assertThat(event.salesResumeOption(singlePerformance(), DURING_SALES)).isEqualTo(SalesResumeOption.HELD_BY_ADMIN);
            assertRejected(() -> event.resumeSales(singlePerformance(), RESUMED_AT), EventErrorCode.SALES_HELD_BY_ADMIN);
            assertThat(event.status()).isEqualTo(EventStatus.SALES_PAUSED);
        }

        @Test
        @DisplayName("조치를 풀어도 판매는 저절로 열리지 않는다 — 그 뒤 주최자가 직접 재개한다")
        void releaseDoesNotReopen() {
            Event event = onSale();
            event.holdSales(SalesPauseCause.ADMIN_PAGE_TAKEDOWN, ADMIN_ACCOUNT_ID, singlePerformance(), DURING_SALES);

            assertThat(event.releaseSalesHold(SalesPauseCause.ADMIN_PAGE_TAKEDOWN, RELEASED_AT)).isTrue();

            assertThat(event.status()).isEqualTo(EventStatus.SALES_PAUSED);
            assertThat(event.activeSalesHolds()).isEmpty();
            assertThat(event.salesResumeOption(singlePerformance(), RELEASED_AT)).isEqualTo(SalesResumeOption.AVAILABLE);

            event.resumeSales(singlePerformance(), RESUMED_AT);
            assertThat(event.status()).isEqualTo(EventStatus.ON_SALE);
            assertThat(event.salesPauses()).containsExactly(
                    new SalesPause(SalesPauseCause.ADMIN_PAGE_TAKEDOWN, ADMIN_ACCOUNT_ID, null, DURING_SALES, RELEASED_AT));
        }

        @Test
        @DisplayName("주최자가 먼저 멈춰 둔 판매에 조치가 걸렸다 풀리면 주최자가 멈춘 그대로다 (docs/99 B \"해제 시 판매 자동 재개\")")
        void organizerPauseSurvivesRelease() {
            Event event = onSale();
            event.pauseSales(STAFF_ACCOUNT_ID, CAST_ILLNESS, singlePerformance(), DURING_SALES);
            Instant heldAt = DURING_SALES.plusSeconds(3600);

            assertThat(event.holdSales(SalesPauseCause.ADMIN_SUSPENSION, ADMIN_ACCOUNT_ID, singlePerformance(), heldAt)).isTrue();
            event.releaseSalesHold(SalesPauseCause.ADMIN_SUSPENSION, RELEASED_AT);

            assertThat(event.status()).isEqualTo(EventStatus.SALES_PAUSED);
            assertThat(event.activeOrganizerPause()).map(SalesPause::reason).contains(CAST_ILLNESS);
            assertThat(event.salesPauses()).containsExactly(
                    new SalesPause(SalesPauseCause.ORGANIZER, STAFF_ACCOUNT_ID, CAST_ILLNESS, DURING_SALES, null),
                    new SalesPause(SalesPauseCause.ADMIN_SUSPENSION, ADMIN_ACCOUNT_ID, null, heldAt, RELEASED_AT));
        }

        @Test
        @DisplayName("정지와 페이지 내리기가 함께 걸려 있으면 하나만 풀어서는 재개할 수 없다")
        void bothHoldsMustBeReleased() {
            Event event = onSale();
            event.holdSales(SalesPauseCause.ADMIN_SUSPENSION, ADMIN_ACCOUNT_ID, singlePerformance(), DURING_SALES);
            event.holdSales(SalesPauseCause.ADMIN_PAGE_TAKEDOWN, ADMIN_ACCOUNT_ID, singlePerformance(), DURING_SALES);

            event.releaseSalesHold(SalesPauseCause.ADMIN_SUSPENSION, RELEASED_AT);

            assertThat(event.activeSalesHolds()).containsExactly(SalesPauseCause.ADMIN_PAGE_TAKEDOWN);
            assertRejected(() -> event.resumeSales(singlePerformance(), RESUMED_AT), EventErrorCode.SALES_HELD_BY_ADMIN);
        }

        @Test
        @DisplayName("같은 조치를 또 걸거나, 걸려 있지 않은 조치를 풀면 아무것도 바뀌지 않는다")
        void repeatedHoldAndReleaseAreIgnored() {
            Event event = onSale();
            event.holdSales(SalesPauseCause.ADMIN_SUSPENSION, ADMIN_ACCOUNT_ID, singlePerformance(), DURING_SALES);

            assertThat(event.holdSales(SalesPauseCause.ADMIN_SUSPENSION, ADMIN_ACCOUNT_ID, singlePerformance(), DURING_SALES)).isFalse();
            assertThat(event.releaseSalesHold(SalesPauseCause.ADMIN_PAGE_TAKEDOWN, RELEASED_AT)).isFalse();

            assertThat(event.salesPauses()).hasSize(1);
        }

        @Test
        @DisplayName("확인 필요 (#38) — 판매 대기에 조치를 걸면 오픈 시각이 와도 열리지 않고, 풀린 뒤 재개하면 시각대로 판매 대기 또는 판매 중")
        void holdBeforeOpening() {
            Event event = springPlayIn(EventStatus.AWAITING_OPEN);

            event.holdSales(SalesPauseCause.ADMIN_SUSPENSION, ADMIN_ACCOUNT_ID, singlePerformance(), BEFORE_OPEN);
            event.advanceByTime(singlePerformance(), OPENS_AT);
            assertThat(event.status()).isEqualTo(EventStatus.SALES_PAUSED);

            event.releaseSalesHold(SalesPauseCause.ADMIN_SUSPENSION, OPENS_AT);
            event.resumeSales(singlePerformance(), OPENS_AT);
            assertThat(event.status()).isEqualTo(EventStatus.ON_SALE);
        }

        @Test
        @DisplayName("확인 필요 (#38) — 판매 대기에 걸린 조치가 오픈 전에 풀려 재개하면 판매 대기로 돌아간다")
        void resumeBeforeOpeningReturnsToAwaiting() {
            Event event = springPlayIn(EventStatus.AWAITING_OPEN);
            event.holdSales(SalesPauseCause.ADMIN_PAGE_TAKEDOWN, ADMIN_ACCOUNT_ID, singlePerformance(), BEFORE_OPEN);
            event.releaseSalesHold(SalesPauseCause.ADMIN_PAGE_TAKEDOWN, BEFORE_OPEN);

            event.resumeSales(singlePerformance(), BEFORE_OPEN);

            assertThat(event.status()).isEqualTo(EventStatus.AWAITING_OPEN);
        }

        @Test
        @DisplayName("판매가 끝난 뒤 풀어도 이력의 끝난 시각은 남는다")
        void releaseAfterSalesClosed() {
            Event event = onSale();
            event.holdSales(SalesPauseCause.ADMIN_SUSPENSION, ADMIN_ACCOUNT_ID, singlePerformance(), DURING_SALES);
            Instant afterSalesClosed = korean(MARCH_14, 18, 30);
            event.advanceByTime(singlePerformance(), afterSalesClosed);

            assertThat(event.releaseSalesHold(SalesPauseCause.ADMIN_SUSPENSION, afterSalesClosed)).isTrue();

            assertThat(event.status()).isEqualTo(EventStatus.SALES_CLOSED);
            assertThat(event.salesPauses().getFirst().endedAt()).isEqualTo(afterSalesClosed);
        }

        @Test
        @DisplayName("주최자 중단은 관리자 조치로 걸거나 풀 수 없다")
        void organizerCauseIsNotAdminAction() {
            Event event = onSale();

            assertThatIllegalArgumentException().isThrownBy(
                    () -> event.holdSales(SalesPauseCause.ORGANIZER, ADMIN_ACCOUNT_ID, singlePerformance(), DURING_SALES));
            assertThatIllegalArgumentException().isThrownBy(() -> event.releaseSalesHold(SalesPauseCause.ORGANIZER, RELEASED_AT));
        }
    }

    @Nested
    @DisplayName("되살리기")
    class Restore {

        @Test
        @DisplayName("작성 중 행사에는 판매 중단 이력이 있을 수 없다")
        void draftHasNoPauses() {
            assertThatIllegalArgumentException().isThrownBy(() -> springPlayIn(EventStatus.DRAFT, opensAtMarchFirst(),
                    List.of(new SalesPause(SalesPauseCause.ORGANIZER, STAFF_ACCOUNT_ID, null, PAUSED_AT, PAUSED_AT))));
        }

        @Test
        @DisplayName("같은 까닭의 끝나지 않은 중단이 둘일 수 없다")
        void oneActivePausePerCause() {
            assertThatIllegalArgumentException().isThrownBy(() -> springPlayIn(EventStatus.SALES_PAUSED, opensAtMarchFirst(), List.of(
                    new SalesPause(SalesPauseCause.ADMIN_SUSPENSION, ADMIN_ACCOUNT_ID, null, PAUSED_AT, null),
                    new SalesPause(SalesPauseCause.ADMIN_SUSPENSION, ADMIN_ACCOUNT_ID, null, DURING_SALES, null))));
        }
    }

    @Nested
    @DisplayName("중단 이력 한 건과 사유")
    class Values {

        @Test
        @DisplayName("관리자 조치는 사유를 갖지 않고, 멈춘 시각보다 먼저 끝날 수 없다")
        void pauseRecordRules() {
            assertThatIllegalArgumentException().isThrownBy(() -> new SalesPause(SalesPauseCause.ADMIN_PAGE_TAKEDOWN, ADMIN_ACCOUNT_ID,
                    CAST_ILLNESS, PAUSED_AT, null));
            assertThatIllegalArgumentException().isThrownBy(() -> new SalesPause(SalesPauseCause.ORGANIZER, STAFF_ACCOUNT_ID, null,
                    PAUSED_AT, PAUSED_AT.minusSeconds(1)));
            assertThatIllegalArgumentException().isThrownBy(() -> new SalesPause(SalesPauseCause.ORGANIZER, 0L, null, PAUSED_AT, null));
        }

        @Test
        @DisplayName("사유는 앞뒤 공백을 걷고 NFC로 맞춘다 — 비우면 사유 없음")
        void reasonNormalized() {
            String decomposed = "한 줄";

            assertThat(SalesPauseReason.fromInput("  잠시 멈춥니다  ")).contains(new SalesPauseReason("잠시 멈춥니다"));
            assertThat(SalesPauseReason.fromInput(decomposed).orElseThrow().value()).isEqualTo("한 줄");
            assertThat(SalesPauseReason.fromInput("   ")).isEmpty();
            assertThat(SalesPauseReason.fromInput(null)).isEmpty();
        }

        @Test
        @DisplayName("사유는 한 줄 — 줄바꿈과 " + ProposedPolicy.SALES_PAUSE_REASON_MAXIMUM_LENGTH + "자 초과는 받지 않는다 (확인 필요)")
        void reasonIsOneShortLine() {
            String longest = "가".repeat(ProposedPolicy.SALES_PAUSE_REASON_MAXIMUM_LENGTH);

            assertThat(SalesPauseReason.fromInput(longest)).isPresent();
            assertThat(SalesPauseReason.isAcceptable(longest + "가")).isFalse();
            assertThat(SalesPauseReason.isAcceptable("잠시\n멈춥니다")).isFalse();
            assertThat(SalesPauseReason.isAcceptable(null)).isTrue();
            assertThatIllegalArgumentException().isThrownBy(() -> SalesPauseReason.fromInput(longest + "가"));
            assertThatIllegalArgumentException().isThrownBy(() -> SalesPauseReason.fromInput("잠시\n멈춥니다"));
        }
    }
}
