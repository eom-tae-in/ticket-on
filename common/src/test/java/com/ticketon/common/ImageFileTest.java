package com.ticketon.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("그림 파일 — 시그니처로 형식, ImageIO로 크기")
class ImageFileTest {

    @Nested
    @DisplayName("형식")
    class Format {

        @Test
        @DisplayName("첫머리 시그니처로 PNG와 JPEG를 알아본다")
        void detectsBySignature() {
            assertThat(ImageFormat.detect(TestImages.png(4, 4))).contains(ImageFormat.PNG);
            assertThat(ImageFormat.detect(TestImages.jpeg(4, 4))).contains(ImageFormat.JPEG);
        }

        @Test
        @DisplayName("GIF · 글자 · 빈 파일 · 시그니처보다 짧은 파일은 알아보지 못한다")
        void unknownContent() {
            assertThat(ImageFormat.detect(TestImages.of("gif", 4, 4))).isEmpty();
            assertThat(ImageFormat.detect("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)))
                    .isEmpty();
            assertThat(ImageFormat.detect(new byte[0])).isEmpty();
            assertThat(ImageFormat.detect(new byte[] {(byte) 0x89, 0x50})).isEmpty();
            assertThat(ImageFormat.detect(null)).isEmpty();
        }

        @Test
        @DisplayName("올린 쪽이 적은 형식은 대소문자와 매개변수를 따지지 않는다")
        void mediaTypeIgnoresCaseAndParameters() {
            assertThat(ImageFormat.ofMediaType("image/png")).contains(ImageFormat.PNG);
            assertThat(ImageFormat.ofMediaType(" IMAGE/JPEG; charset=binary")).contains(ImageFormat.JPEG);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @DisplayName("알아볼 수 없는 형식 이름은 빈 값이다")
        @ValueSource(strings = {"", "image/gif", "image/svg+xml", "application/octet-stream", "image/jpg", "png"})
        void unknownMediaType(String mediaType) {
            assertThat(ImageFormat.ofMediaType(mediaType)).isEmpty();
        }

        @Test
        @DisplayName("저장할 때 붙이는 확장자 — PNG는 png, JPEG는 jpg")
        void fileExtension() {
            assertThat(ImageFormat.PNG.fileExtension()).isEqualTo("png");
            assertThat(ImageFormat.JPEG.fileExtension()).isEqualTo("jpg");
            assertThat(ImageFormat.ofFileExtension("jpg")).contains(ImageFormat.JPEG);
            assertThat(ImageFormat.ofFileExtension("jpeg")).isEmpty();
            assertThat(ImageFormat.ofFileExtension("PNG")).isEmpty();
        }
    }

    @Nested
    @DisplayName("크기")
    class Size {

        @Test
        @DisplayName("PNG와 JPEG의 가로·세로를 읽는다")
        void readsWidthAndHeight() {
            assertThat(ImageFile.inspect(TestImages.png(300, 200))).contains(new ImageFile(ImageFormat.PNG, 300, 200));
            assertThat(ImageFile.inspect(TestImages.jpeg(120, 160))).contains(new ImageFile(ImageFormat.JPEG, 120, 160));
        }

        @Test
        @DisplayName("가로와 세로가 같으면 정사각형이다")
        void square() {
            assertThat(new ImageFile(ImageFormat.PNG, 512, 512).isSquare()).isTrue();
            assertThat(new ImageFile(ImageFormat.PNG, 512, 511).isSquare()).isFalse();
        }

        @Test
        @DisplayName("시그니처만 맞고 머리말이 깨진 파일은 크기를 읽을 수 없어 빈 값이다")
        void brokenHeader() {
            byte[] png = TestImages.png(300, 200);
            byte[] truncatedPng = Arrays.copyOf(png, 12);
            byte[] jpegSignatureOnly = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};

            assertThat(ImageFile.inspect(truncatedPng)).isEmpty();
            assertThat(ImageFile.inspect(jpegSignatureOnly)).isEmpty();
        }

        @Test
        @DisplayName("알아볼 수 없는 형식은 빈 값이다")
        void unknownFormat() {
            assertThat(ImageFile.inspect(TestImages.of("gif", 4, 4))).isEmpty();
            assertThat(ImageFile.inspect(new byte[0])).isEmpty();
        }

        @Test
        @DisplayName("가로·세로가 1보다 작으면 만들 수 없다")
        void positiveSides() {
            assertThatIllegalArgumentException().isThrownBy(() -> new ImageFile(ImageFormat.PNG, 0, 10));
            assertThatIllegalArgumentException().isThrownBy(() -> new ImageFile(ImageFormat.PNG, 10, -1));
        }
    }
}
