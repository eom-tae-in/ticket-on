package com.ticketon.domain.organizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.common.ImageFormat;
import com.ticketon.common.TestImages;
import com.ticketon.domain.policy.ProposedPolicy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("주최자 로고 그림 (docs/01 \"공개 페이지 — 로고\")")
class OrganizerLogoImageTest {

    @Test
    @DisplayName("정사각형 PNG · JPEG를 로고로 받는다 — 한 변의 픽셀과 실제 형식을 안다")
    void acceptsSquarePngAndJpeg() {
        OrganizerLogoImage png = OrganizerLogoImage.of("image/png", TestImages.png(512, 512));
        OrganizerLogoImage jpeg = OrganizerLogoImage.of("image/jpeg", TestImages.jpeg(300, 300));

        assertThat(png.format()).isEqualTo(ImageFormat.PNG);
        assertThat(png.sideLength()).isEqualTo(512);
        assertThat(jpeg.format()).isEqualTo(ImageFormat.JPEG);
        assertThat(jpeg.sideLength()).isEqualTo(300);
        assertThat(OrganizerLogoImage.problemOf("image/png", TestImages.png(512, 512))).isEmpty();
    }

    @Test
    @DisplayName("정사각형이 아닌 그림은 받지 않는다 — 한 픽셀만 달라도")
    void rejectsNonSquare() {
        expectProblem("image/png", TestImages.png(512, 511), OrganizerLogoImageProblem.NOT_SQUARE);
        expectProblem("image/jpeg", TestImages.jpeg(400, 300), OrganizerLogoImageProblem.NOT_SQUARE);
    }

    @Test
    @DisplayName("용량 상한(제안 2MB)을 넘으면 받지 않는다 — 상한과 같은 크기는 받는다")
    void rejectsOverSizeLimit() {
        long limit = ProposedPolicy.ORGANIZER_LOGO_MAXIMUM_SIZE.bytes();
        byte[] square = TestImages.png(64, 64);
        byte[] atLimit = Arrays.copyOf(square, (int) limit);
        byte[] overLimit = Arrays.copyOf(square, (int) limit + 1);

        assertThat(limit).isEqualTo(2_097_152L);
        assertThat(OrganizerLogoImage.problemOf("image/png", atLimit)).isEmpty();
        expectProblem("image/png", overLimit, OrganizerLogoImageProblem.TOO_LARGE);
    }

    @Test
    @DisplayName("받는 형식이 아닌 그림(GIF · BMP)과 그림이 아닌 파일(SVG 글자)은 받지 않는다")
    void rejectsUnsupportedFormat() {
        expectProblem("image/gif", TestImages.of("gif", 64, 64), OrganizerLogoImageProblem.UNSUPPORTED_FORMAT);
        expectProblem("image/bmp", TestImages.of("bmp", 64, 64), OrganizerLogoImageProblem.UNSUPPORTED_FORMAT);
        expectProblem("image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"64\" height=\"64\"/>".getBytes(StandardCharsets.UTF_8),
                OrganizerLogoImageProblem.UNSUPPORTED_FORMAT);
    }

    @Test
    @DisplayName("적은 형식만 그림이고 실제 바이트가 다르면 받지 않는다 — 확장자나 적은 형식만 믿지 않는다")
    void rejectsDeclaredFormatThatDoesNotMatchContent() {
        expectProblem("image/png", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8),
                OrganizerLogoImageProblem.UNSUPPORTED_FORMAT);
        expectProblem("image/png", TestImages.jpeg(64, 64), OrganizerLogoImageProblem.UNSUPPORTED_FORMAT);
        expectProblem("image/jpeg", TestImages.png(64, 64), OrganizerLogoImageProblem.UNSUPPORTED_FORMAT);
    }

    @Test
    @DisplayName("실제 바이트는 그림이어도 적은 형식이 없거나 그림 형식이 아니면 받지 않는다")
    void rejectsMissingOrUnknownDeclaredFormat() {
        expectProblem(null, TestImages.png(64, 64), OrganizerLogoImageProblem.UNSUPPORTED_FORMAT);
        expectProblem("application/octet-stream", TestImages.png(64, 64), OrganizerLogoImageProblem.UNSUPPORTED_FORMAT);
    }

    @Test
    @DisplayName("시그니처만 맞고 머리말이 깨진 파일은 받지 않는다")
    void rejectsBrokenHeader() {
        byte[] truncated = Arrays.copyOf(TestImages.png(64, 64), 16);

        expectProblem("image/png", truncated, OrganizerLogoImageProblem.UNSUPPORTED_FORMAT);
    }

    @Test
    @DisplayName("빈 파일은 받지 않는다")
    void rejectsEmpty() {
        expectProblem("image/png", new byte[0], OrganizerLogoImageProblem.EMPTY);
    }

    @Test
    @DisplayName("받은 내용은 사본이라 넘긴 배열이나 꺼낸 배열을 고쳐도 바뀌지 않는다")
    void contentIsCopied() {
        byte[] content = TestImages.png(32, 32);
        OrganizerLogoImage image = OrganizerLogoImage.of("image/png", content);
        byte[] original = content.clone();

        content[0] = 0;
        image.content()[1] = 0;

        assertThat(image.content()).isEqualTo(original);
        assertThat(image.sizeInBytes()).isEqualTo(original.length);
        assertThat(image).isEqualTo(OrganizerLogoImage.of("image/png", original));
        assertThat(image.toString()).contains("PNG", "32x32").doesNotContain(Arrays.toString(original));
    }

    private static void expectProblem(String mediaType, byte[] content, OrganizerLogoImageProblem problem) {
        assertThat(OrganizerLogoImage.problemOf(mediaType, content)).contains(problem);
        assertThatIllegalArgumentException().isThrownBy(() -> OrganizerLogoImage.of(mediaType, content))
                .withMessageContaining(problem.name());
    }
}
