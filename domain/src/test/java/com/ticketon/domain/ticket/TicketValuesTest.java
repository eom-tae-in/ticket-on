package com.ticketon.domain.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("티켓의 값 — 번호 · QR 열쇠 · 회차 (docs/07)")
class TicketValuesTest {

    @Nested
    @DisplayName("티켓 번호 = 부모 번호 + 순번 (docs/07 \"티켓 표기\")")
    class Number {

        @Test
        @DisplayName("예매번호 ...0091 로 4매 예매 → 0091-1 · 0091-2 · 0091-3 · 0091-4")
        void fourTicketsOfOneBooking() {
            TicketParentNumber booking = TicketParentNumber.of("0091");

            assertThat(IntStream.rangeClosed(1, 4).mapToObj(sequence -> TicketNumber.of(booking, sequence).value()))
                    .containsExactly("0091-1", "0091-2", "0091-3", "0091-4");
        }

        @Test
        @DisplayName("같은 행사를 두 번 예매하면 0091-1과 0134-1로 갈린다")
        void secondBookingDiffers() {
            TicketNumber first = TicketNumber.of(TicketParentNumber.of("0091"), 1);
            TicketNumber second = TicketNumber.of(TicketParentNumber.of("0134"), 1);

            assertThat(first.value()).isEqualTo("0091-1");
            assertThat(second.value()).isEqualTo("0134-1");
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("순번은 1부터다")
        void sequenceStartsAtOne() {
            assertThatIllegalArgumentException().isThrownBy(() -> TicketNumber.of(TicketParentNumber.of("0091"), 0));
        }

        @Test
        @DisplayName("부모 번호는 비어 있거나 앞뒤에 공백이 있을 수 없다")
        void parentNumberIsFilled() {
            assertThatIllegalArgumentException().isThrownBy(() -> TicketParentNumber.of(" "));
            assertThatIllegalArgumentException().isThrownBy(() -> TicketParentNumber.of(" 0091"));
        }
    }

    @Nested
    @DisplayName("QR 열쇠 — 추측할 수 없는 URL 안전 문자 32자 (docs/07 \"QR에 무엇이 담기나\")")
    class Key {

        @Test
        @DisplayName("URL 안전 문자 32자를 받는다")
        void acceptsUrlSafeToken() {
            String token = "abcdefghijklmnopqrstuvwxyz012-_9";

            assertThat(QrKey.issue(token).value()).isEqualTo(token);
            assertThat(QrKey.isValid(token)).isTrue();
        }

        @Test
        @DisplayName("길이가 다르거나 URL 안전 문자가 아니면 받지 않는다")
        void rejectsOtherForms() {
            assertThatIllegalArgumentException().isThrownBy(() -> QrKey.issue("A".repeat(31)));
            assertThatIllegalArgumentException().isThrownBy(() -> QrKey.issue("A".repeat(31) + "+"));
            assertThat(QrKey.isValid(null)).isFalse();
            assertThat(QrKey.isValid("A".repeat(33))).isFalse();
        }

        @Test
        @DisplayName("문자열로 바꾸면 값을 가린다 — 알면 남의 QR을 띄울 수 있다")
        void hidesValue() {
            assertThat(TicketFixtures.KEY.toString()).doesNotContain("AAAA");
        }
    }

    @Nested
    @DisplayName("티켓이 가리키는 회차")
    class Session {

        @Test
        @DisplayName("예매 티켓의 회차는 산 때의 가격을 갖고, 처음에는 포기하지 않았다")
        void purchasedSession() {
            TicketSession session = TicketSession.purchased(TicketFixtures.SESSION_MARCH_14, TicketFixtures.sessionPrice(50_000));

            assertThat(session.purchasedPrice()).contains(TicketFixtures.sessionPrice(50_000));
            assertThat(session.isForfeited()).isFalse();
        }

        @Test
        @DisplayName("초대권 티켓의 회차는 가격이 없다")
        void invitedSession() {
            assertThat(TicketSession.invited(TicketFixtures.SESSION_MARCH_14).purchasedPrice()).isEmpty();
        }

        @Test
        @DisplayName("저장소에서 포기 시각과 함께 되살린다")
        void restoresForfeit() {
            Instant forfeitedAt = Instant.parse("2026-05-02T03:00:00Z");

            TicketSession session = TicketSession.restore(TicketFixtures.SESSION_MAY_4, TicketFixtures.sessionPrice(50_000), forfeitedAt);

            assertThat(session.isForfeited()).isTrue();
            assertThat(session.forfeitedAt()).contains(forfeitedAt);
        }

        @Test
        @DisplayName("회차 id는 1 이상이다")
        void sessionIdIsPositive() {
            assertThatIllegalArgumentException().isThrownBy(() -> TicketSession.invited(0));
        }
    }

    @Nested
    @DisplayName("취소 사유 8종 — 부모마다 붙을 수 있는 사유 (docs/07, docs/09)")
    class CancellationReasons {

        @Test
        @DisplayName("사유는 문서의 8종이다")
        void eightReasons() {
            assertThat(TicketCancellationReason.values()).extracting(TicketCancellationReason::label)
                    .containsExactly("관객 취소", "좌석 변경", "회차 취소", "행사 취소", "공연 중단", "관리자 조치", "초대권 회수", "결제 오류 환불");
        }

        @Test
        @DisplayName("관객 취소 · 좌석 변경 · 관리자 조치 · 결제 오류 환불은 예매 티켓에만, 초대권 회수는 초대권 티켓에만 붙는다")
        void reasonsByParent() {
            assertThat(TicketCancellationReason.values())
                    .filteredOn(reason -> reason.appliesTo(TicketParentKind.BOOKING))
                    .containsExactly(TicketCancellationReason.AUDIENCE_CANCELLATION, TicketCancellationReason.SEAT_CHANGE,
                            TicketCancellationReason.SESSION_CANCELLED, TicketCancellationReason.EVENT_CANCELLED,
                            TicketCancellationReason.PERFORMANCE_STOPPED, TicketCancellationReason.ADMIN_ACTION,
                            TicketCancellationReason.PAYMENT_ERROR_REFUND);
            assertThat(TicketCancellationReason.values())
                    .filteredOn(reason -> reason.appliesTo(TicketParentKind.INVITATION))
                    .containsExactly(TicketCancellationReason.SESSION_CANCELLED, TicketCancellationReason.EVENT_CANCELLED,
                            TicketCancellationReason.PERFORMANCE_STOPPED, TicketCancellationReason.INVITATION_RECALLED);
        }
    }
}
