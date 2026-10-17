package com.ticketon.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.common.ImageFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("행사 포스터 열쇠 (docs/03 \"기본 정보\")")
class EventPosterTest {

    private static final String TOKEN = "Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL";

    @Test
    @DisplayName("추측할 수 없는 값 32자 + 실제 형식의 확장자로 열쇠를 붙인다 — 올린 사람의 파일 이름은 쓰지 않는다")
    void issuesKeyFromTokenAndFormat() {
        assertThat(EventPoster.issue(TOKEN, ImageFormat.JPEG).fileKey()).isEqualTo(TOKEN + ".jpg");
        assertThat(EventPoster.issue(TOKEN, ImageFormat.PNG).fileKey()).isEqualTo(TOKEN + ".png");
    }

    @Test
    @DisplayName("열쇠의 확장자로 형식을 안다")
    void formatFromKey() {
        assertThat(new EventPoster(TOKEN + ".jpg").format()).isEqualTo(ImageFormat.JPEG);
        assertThat(new EventPoster(TOKEN + ".png").format()).isEqualTo(ImageFormat.PNG);
        assertThat(EventPoster.isValidFileKey(TOKEN + ".jpg")).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @DisplayName("빈 값 · 경로를 거슬러 오르는 이름 · 하위 경로 · 짧거나 긴 값 · 모르는 확장자 · 앞뒤 공백은 열쇠가 아니다")
    @ValueSource(strings = {
            "",
            "  ",
            "poster-key.jpg",
            "Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL",
            "../Pq7rS2tU9vW4xY6zA1bC3dE5fG8h.jpg",
            "event-posters/7.jpg",
            "Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0k.jpg",
            "Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kLz.jpg",
            "Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.svg",
            "Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.JPG",
            "Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.jpeg",
            "Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.jpg.jpg",
            "Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0k%.jpg",
            " Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.jpg "})
    void rejectsMalformedKey(String fileKey) {
        assertThat(EventPoster.isValidFileKey(fileKey)).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new EventPoster(fileKey));
    }

    @Test
    @DisplayName("값의 꼴이 맞지 않으면 열쇠를 붙이지 않는다")
    void rejectsMalformedToken() {
        assertThatIllegalArgumentException().isThrownBy(() -> EventPoster.issue("short", ImageFormat.JPEG));
        assertThat(EventPoster.isValidFileKey(null)).isFalse();
    }
}
