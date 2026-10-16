package com.ticketon.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("도면의 값 — 구역 이름 · 색 · 기본가 · 정원, 블록 이름 · 크기 · 위치 · 각도, 좌석 번호 (docs/02 \"구역\" · \"블록\" · \"에디터는 블록 단위\")")
class FloorPlanValuesTest {

    @Nested
    @DisplayName("구역 이름")
    class ZoneNames {

        @Test
        @DisplayName("\"R석\" · \"스탠딩A\" — 앞뒤 공백을 걷는다 (docs/02 예시)")
        void stripped() {
            assertThat(ZoneName.of("  R석 ").value()).isEqualTo("R석");
            assertThat(ZoneName.of("스탠딩A").value()).isEqualTo("스탠딩A");
        }

        @Test
        @DisplayName("자모를 늘어놓은 꼴(NFD)도 완성형(NFC)으로 맞춰 같은 이름이 된다")
        void normalizedToNfc() {
            assertThat(ZoneName.of(Normalizer.normalize("R석", Normalizer.Form.NFD))).isEqualTo(ZoneName.of("R석"));
        }

        @Test
        @DisplayName("비우거나 공백뿐이면 받지 않는다")
        void blankRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> ZoneName.of("  "));
            assertThat(ZoneName.isValid("  ")).isFalse();
            assertThat(ZoneName.isValid(null)).isFalse();
        }

        @Test
        @DisplayName("20자까지 받고 21자는 받지 않는다 (제안값, 확인 필요)")
        void maximumLength() {
            assertThat(ProposedPolicy.ZONE_NAME_MAXIMUM_LENGTH).isEqualTo(20);
            assertThat(ZoneName.isValid("가".repeat(20))).isTrue();
            assertThat(ZoneName.isValid("가".repeat(21))).isFalse();
            assertThatIllegalArgumentException().isThrownBy(() -> ZoneName.of("가".repeat(21)));
        }
    }

    @Nested
    @DisplayName("구역 색 — #RRGGBB")
    class ZoneColors {

        @Test
        @DisplayName("소문자로 적어도 대문자로 맞춰 같은 색이다")
        void normalizedToUpperCase() {
            assertThat(ZoneColor.of(" #e5484d ").value()).isEqualTo("#E5484D");
            assertThat(ZoneColor.of("#e5484d")).isEqualTo(ZoneColor.of("#E5484D"));
        }

        @Test
        @DisplayName("#RRGGBB 꼴이 아니면 받지 않는다")
        void malformedRejected() {
            assertThat(ZoneColor.isValid("#FFF")).isFalse();
            assertThat(ZoneColor.isValid("E5484D")).isFalse();
            assertThat(ZoneColor.isValid("#E5484G")).isFalse();
            assertThat(ZoneColor.isValid("red")).isFalse();
            assertThat(ZoneColor.isValid(null)).isFalse();
            assertThatIllegalArgumentException().isThrownBy(() -> ZoneColor.of("#FFF"));
        }
    }

    @Nested
    @DisplayName("구역 기본가 · 정원")
    class ZonePriceAndCapacity {

        @Test
        @DisplayName("R석 50,000원 (docs/02 \"두 개의 축\" 예시)")
        void basePrice() {
            assertThat(ZoneBasePrice.of(50_000).won()).isEqualTo(50_000);
        }

        @Test
        @DisplayName("기본가는 1원 이상 — 돈을 받지 않는 표는 초대권이다")
        void basePriceAtLeastOneWon() {
            assertThat(ZoneBasePrice.isValid(1)).isTrue();
            assertThat(ZoneBasePrice.isValid(0)).isFalse();
            assertThatIllegalArgumentException().isThrownBy(() -> ZoneBasePrice.of(0));
            assertThatIllegalArgumentException().isThrownBy(() -> ZoneBasePrice.of(-1000));
        }

        @Test
        @DisplayName("스탠딩A 300 (docs/02 \"구역\" 예시) — 정원은 1 이상")
        void capacity() {
            assertThat(ZoneCapacity.of(300).seats()).isEqualTo(300);
            assertThat(ZoneCapacity.isValid(1)).isTrue();
            assertThat(ZoneCapacity.isValid(0)).isFalse();
            assertThatIllegalArgumentException().isThrownBy(() -> ZoneCapacity.of(0));
        }
    }

    @Nested
    @DisplayName("블록 이름 — 자유 입력, 비우면 자동")
    class BlockNames {

        @Test
        @DisplayName("\"1층\" · \"좌측\" — 앞뒤 공백을 걷고 NFC로 맞춘다 (docs/02 예시)")
        void normalized() {
            assertThat(BlockName.of(" 1층 ").value()).isEqualTo("1층");
            assertThat(BlockName.of(Normalizer.normalize("좌측", Normalizer.Form.NFD))).isEqualTo(BlockName.of("좌측"));
        }

        @Test
        @DisplayName("적지 않았거나 공백뿐이면 비어 있다 — 도면이 '블록 N'을 붙인다")
        void blankInputIsEmpty() {
            assertThat(BlockName.fromInput(null)).isEmpty();
            assertThat(BlockName.fromInput("   ")).isEmpty();
            assertThat(BlockName.fromInput("2층")).contains(BlockName.of("2층"));
            assertThat(BlockName.isAcceptable(null)).isTrue();
            assertThat(BlockName.isAcceptable("  ")).isTrue();
            assertThatIllegalArgumentException().isThrownBy(() -> BlockName.of("  "));
        }

        @Test
        @DisplayName("자동 이름은 '블록 1', '블록 2' (docs/02)")
        void automaticName() {
            assertThat(BlockName.automatic(1).value()).isEqualTo("블록 1");
            assertThat(BlockName.automatic(2).value()).isEqualTo("블록 2");
            assertThatIllegalArgumentException().isThrownBy(() -> BlockName.automatic(0));
        }

        @Test
        @DisplayName("20자까지 받고 21자는 받지 않는다 (제안값, 확인 필요)")
        void maximumLength() {
            assertThat(ProposedPolicy.BLOCK_NAME_MAXIMUM_LENGTH).isEqualTo(20);
            assertThat(BlockName.isAcceptable("가".repeat(20))).isTrue();
            assertThat(BlockName.isAcceptable("가".repeat(21))).isFalse();
            assertThatIllegalArgumentException().isThrownBy(() -> BlockName.fromInput("가".repeat(21)));
        }
    }

    @Nested
    @DisplayName("블록 크기 — 행 × 열")
    class BlockSizes {

        @Test
        @DisplayName("5행 × 10열 덩어리는 50석, 10행 × 20열은 200석 (docs/02 · #43 예시)")
        void seatCount() {
            assertThat(BlockSize.of(5, 10).seatCount()).isEqualTo(50);
            assertThat(BlockSize.of(10, 20).seatCount()).isEqualTo(200);
        }

        @Test
        @DisplayName("행 · 열은 1~50 (제안값, 확인 필요)")
        void bounds() {
            assertThat(ProposedPolicy.SEAT_BLOCK_MAXIMUM_ROWS).isEqualTo(50);
            assertThat(ProposedPolicy.SEAT_BLOCK_MAXIMUM_COLUMNS).isEqualTo(50);
            assertThat(BlockSize.isValid(1, 1)).isTrue();
            assertThat(BlockSize.isValid(50, 50)).isTrue();
            assertThat(BlockSize.isValid(0, 10)).isFalse();
            assertThat(BlockSize.isValid(5, 0)).isFalse();
            assertThat(BlockSize.isValid(51, 10)).isFalse();
            assertThat(BlockSize.isValid(5, 51)).isFalse();
            assertThatIllegalArgumentException().isThrownBy(() -> BlockSize.of(51, 10));
        }

        @Test
        @DisplayName("5행 × 10열 블록에는 5열 10번까지 있다 — 에디터의 '행'이 표기의 '열'")
        void containsSeatNumber() {
            BlockSize size = BlockSize.of(5, 10);

            assertThat(size.contains(SeatNumber.of(5, 10))).isTrue();
            assertThat(size.contains(SeatNumber.of(6, 1))).isFalse();
            assertThat(size.contains(SeatNumber.of(1, 11))).isFalse();
        }
    }

    @Nested
    @DisplayName("블록 각도 — 부채꼴 객석")
    class BlockAngles {

        @Test
        @DisplayName("0 이상 360 미만으로 맞춘다 — -15°는 345°, 375°는 15°")
        void normalized() {
            assertThat(BlockAngle.ofDegrees(15).degrees()).isEqualTo(15);
            assertThat(BlockAngle.ofDegrees(-15).degrees()).isEqualTo(345);
            assertThat(BlockAngle.ofDegrees(375).degrees()).isEqualTo(15);
            assertThat(BlockAngle.ofDegrees(360)).isEqualTo(BlockAngle.NONE);
            assertThatIllegalArgumentException().isThrownBy(() -> new BlockAngle(360));
        }
    }

    @Nested
    @DisplayName("좌석 번호")
    class SeatNumbers {

        @Test
        @DisplayName("12열 7번 (docs/02 \"좌석 번호 표기\" 예시)")
        void text() {
            assertThat(SeatNumber.of(12, 7).text()).isEqualTo("12열 7번");
        }

        @Test
        @DisplayName("열 · 번은 1부터")
        void startsAtOne() {
            assertThatIllegalArgumentException().isThrownBy(() -> SeatNumber.of(0, 7));
            assertThatIllegalArgumentException().isThrownBy(() -> SeatNumber.of(12, 0));
        }
    }
}
