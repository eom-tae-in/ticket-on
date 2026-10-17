package com.ticketon.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.common.ImageFormat;
import com.ticketon.common.TestImages;
import com.ticketon.domain.policy.ProposedPolicy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("행사 포스터 그림 — 3:4 세로 · 용량 상한 (docs/03 \"기본 정보\", docs/99 C \"포스터 비율\")")
class EventPosterImageTest {

    @Test
    @DisplayName("3:4 세로 PNG · JPEG를 포스터로 받는다 — 가로 · 세로 픽셀과 실제 형식을 안다")
    void acceptsThreeByFourPngAndJpeg() {
        EventPosterImage png = EventPosterImage.of("image/png", TestImages.png(900, 1200));
        EventPosterImage jpeg = EventPosterImage.of("image/jpeg", TestImages.jpeg(300, 400));

        assertThat(png.format()).isEqualTo(ImageFormat.PNG);
        assertThat(png.width()).isEqualTo(900);
        assertThat(png.height()).isEqualTo(1200);
        assertThat(jpeg.format()).isEqualTo(ImageFormat.JPEG);
        assertThat(EventPosterImage.problemOf("image/jpeg", TestImages.jpeg(300, 400))).isEmpty();
    }

    @ParameterizedTest(name = "{0}x{1}")
    @DisplayName("허용 오차(제안 1%) 안이면 받는다 — 세로 1,200픽셀이면 가로 891~909픽셀, 크롭 도구의 반올림(세로 1,199 → 가로 899)")
    @CsvSource({"891, 1200", "909, 1200", "899, 1199", "76, 101"})
    void acceptsWithinTolerance(int width, int height) {
        assertThat(EventPosterImage.problemOf("image/png", TestImages.png(width, height))).isEmpty();
    }

    @ParameterizedTest(name = "{0}x{1}")
    @DisplayName("3:4 세로가 아니면 받지 않는다 — 1:1, 4:3 가로, A4 세로, 오차 1%를 한 픽셀 넘긴 그림")
    @CsvSource({"1200, 1200", "1200, 900", "210, 297", "890, 1200", "910, 1200"})
    void rejectsOtherAspectRatios(int width, int height) {
        expectProblem("image/png", TestImages.png(width, height), EventPosterImageProblem.NOT_POSTER_ASPECT_RATIO);
    }

    @Test
    @DisplayName("용량 상한(제안 5MB)을 넘으면 받지 않는다 — 상한과 같은 크기는 받는다")
    void rejectsOverSizeLimit() {
        long limit = ProposedPolicy.EVENT_POSTER_MAXIMUM_SIZE.bytes();
        byte[] poster = TestImages.png(30, 40);
        byte[] atLimit = Arrays.copyOf(poster, (int) limit);
        byte[] overLimit = Arrays.copyOf(poster, (int) limit + 1);

        assertThat(limit).isEqualTo(5_242_880L);
        assertThat(EventPosterImage.problemOf("image/png", atLimit)).isEmpty();
        expectProblem("image/png", overLimit, EventPosterImageProblem.TOO_LARGE);
    }

    @Test
    @DisplayName("용량을 먼저 본다 — 상한을 넘은 파일은 비율이 맞지 않아도 용량으로 거절한다")
    void sizeIsCheckedBeforeAspectRatio() {
        byte[] overLimitSquare = Arrays.copyOf(TestImages.png(64, 64),
                (int) ProposedPolicy.EVENT_POSTER_MAXIMUM_SIZE.bytes() + 1);

        expectProblem("image/png", overLimitSquare, EventPosterImageProblem.TOO_LARGE);
    }

    @Test
    @DisplayName("받는 형식이 아닌 그림(GIF · BMP)과 그림이 아닌 파일(SVG 글자)은 받지 않는다")
    void rejectsUnsupportedFormat() {
        expectProblem("image/gif", TestImages.of("gif", 30, 40), EventPosterImageProblem.UNSUPPORTED_FORMAT);
        expectProblem("image/bmp", TestImages.of("bmp", 30, 40), EventPosterImageProblem.UNSUPPORTED_FORMAT);
        expectProblem("image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"300\" height=\"400\"/>".getBytes(StandardCharsets.UTF_8),
                EventPosterImageProblem.UNSUPPORTED_FORMAT);
    }

    @Test
    @DisplayName("적은 형식만 그림이고 실제 바이트가 다르면 받지 않는다 — 확장자나 적은 형식만 믿지 않는다")
    void rejectsDeclaredFormatThatDoesNotMatchContent() {
        expectProblem("image/jpeg", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8),
                EventPosterImageProblem.UNSUPPORTED_FORMAT);
        expectProblem("image/png", TestImages.jpeg(30, 40), EventPosterImageProblem.UNSUPPORTED_FORMAT);
        expectProblem("image/jpeg", TestImages.png(30, 40), EventPosterImageProblem.UNSUPPORTED_FORMAT);
    }

    @Test
    @DisplayName("실제 바이트는 그림이어도 적은 형식이 없거나 그림 형식이 아니면 받지 않는다")
    void rejectsMissingOrUnknownDeclaredFormat() {
        expectProblem(null, TestImages.png(30, 40), EventPosterImageProblem.UNSUPPORTED_FORMAT);
        expectProblem("application/octet-stream", TestImages.png(30, 40), EventPosterImageProblem.UNSUPPORTED_FORMAT);
    }

    @Test
    @DisplayName("시그니처만 맞고 머리말이 깨진 파일은 받지 않는다")
    void rejectsBrokenHeader() {
        byte[] truncated = Arrays.copyOf(TestImages.png(30, 40), 16);

        expectProblem("image/png", truncated, EventPosterImageProblem.UNSUPPORTED_FORMAT);
    }

    @Test
    @DisplayName("빈 파일은 받지 않는다")
    void rejectsEmpty() {
        expectProblem("image/png", new byte[0], EventPosterImageProblem.EMPTY);
    }

    @Test
    @DisplayName("받은 내용은 사본이라 넘긴 배열이나 꺼낸 배열을 고쳐도 바뀌지 않는다")
    void contentIsCopied() {
        byte[] content = TestImages.png(30, 40);
        EventPosterImage image = EventPosterImage.of("image/png", content);
        byte[] original = content.clone();

        content[0] = 0;
        image.content()[1] = 0;

        assertThat(image.content()).isEqualTo(original);
        assertThat(image.sizeInBytes()).isEqualTo(original.length);
        assertThat(image).isEqualTo(EventPosterImage.of("image/png", original));
        assertThat(image.toString()).contains("PNG", "30x40").doesNotContain(Arrays.toString(original));
    }

    private static void expectProblem(String mediaType, byte[] content, EventPosterImageProblem problem) {
        assertThat(EventPosterImage.problemOf(mediaType, content)).contains(problem);
        assertThatIllegalArgumentException().isThrownBy(() -> EventPosterImage.of(mediaType, content))
                .withMessageContaining(problem.name());
    }
}
