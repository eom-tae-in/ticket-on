package com.ticketon.domain.organizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.common.ImageFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("주최자 로고 열쇠 (docs/01 \"공개 페이지 — 로고\")")
class OrganizerLogoTest {

    private static final String TOKEN = "Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jK";

    @Test
    @DisplayName("추측할 수 없는 값 32자 + 실제 형식의 확장자로 열쇠를 붙인다 — 올린 사람의 파일 이름은 쓰지 않는다")
    void issuesKeyFromTokenAndFormat() {
        assertThat(OrganizerLogo.issue(TOKEN, ImageFormat.PNG).fileKey()).isEqualTo(TOKEN + ".png");
        assertThat(OrganizerLogo.issue(TOKEN, ImageFormat.JPEG).fileKey()).isEqualTo(TOKEN + ".jpg");
    }

    @Test
    @DisplayName("열쇠의 확장자로 형식을 안다")
    void formatFromKey() {
        assertThat(new OrganizerLogo(TOKEN + ".png").format()).isEqualTo(ImageFormat.PNG);
        assertThat(new OrganizerLogo(TOKEN + ".jpg").format()).isEqualTo(ImageFormat.JPEG);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @DisplayName("경로를 거슬러 오르는 이름 · 하위 경로 · 짧거나 긴 값 · 모르는 확장자는 열쇠가 아니다")
    @ValueSource(strings = {
            "",
            "Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jK",
            "../Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6g.png",
            "organizer-logo/12.png",
            "Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0j.png",
            "Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jKz.png",
            "Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jK.svg",
            "Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jK.PNG",
            "Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jK.png.png",
            "Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0j%.png",
            " Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jK.png"})
    void rejectsMalformedKey(String fileKey) {
        assertThat(OrganizerLogo.isValidFileKey(fileKey)).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new OrganizerLogo(fileKey));
    }

    @Test
    @DisplayName("값의 꼴이 맞지 않으면 열쇠를 붙이지 않는다")
    void rejectsMalformedToken() {
        assertThatIllegalArgumentException().isThrownBy(() -> OrganizerLogo.issue("short", ImageFormat.PNG));
        assertThat(OrganizerLogo.isValidFileKey(null)).isFalse();
    }
}
