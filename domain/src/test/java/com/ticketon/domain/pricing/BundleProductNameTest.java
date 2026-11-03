package com.ticketon.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("묶음권 상품 이름 — 앞뒤 공백 · NFC · 최대 길이")
class BundleProductNameTest {

    @Test
    @DisplayName("앞뒤 공백을 걷는다 — \" 3일 통권 \" → \"3일 통권\"")
    void stripped() {
        assertThat(BundleProductName.of(" 3일 통권 ").value()).isEqualTo("3일 통권");
    }

    @Test
    @DisplayName("자모를 늘어놓은 꼴(NFD)도 완성형으로 맞춰 같은 이름이 된다")
    void normalizedToNfc() {
        String decomposed = Normalizer.normalize("주말 2일권", Normalizer.Form.NFD);

        assertThat(BundleProductName.of(decomposed)).isEqualTo(BundleProductName.of("주말 2일권"));
    }

    @Test
    @DisplayName("비어 있으면 받지 않는다")
    void blankRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> BundleProductName.of("   "));
        assertThat(BundleProductName.isValid("   ")).isFalse();
        assertThat(BundleProductName.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("최대 길이까지 받는다 — 한글 한 자, 이모지 하나가 한 자 (확인 필요 #223)")
    void maximumLength() {
        int maximum = ProposedPolicy.BUNDLE_PRODUCT_NAME_MAXIMUM_LENGTH;

        assertThat(BundleProductName.isValid("권".repeat(maximum))).isTrue();
        assertThat(BundleProductName.isValid("🎫".repeat(maximum))).isTrue();
        assertThat(BundleProductName.isValid("권".repeat(maximum + 1))).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> BundleProductName.of("권".repeat(maximum + 1)));
    }

    @Test
    @DisplayName("제안한 최대 길이는 20자다 (확인 필요 #223)")
    void proposedMaximumLength() {
        assertThat(ProposedPolicy.BUNDLE_PRODUCT_NAME_MAXIMUM_LENGTH).isEqualTo(20);
    }
}
