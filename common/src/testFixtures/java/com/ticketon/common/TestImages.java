package com.ticketon.common;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/**
 * 테스트용 그림 파일 — JDK ImageIO로 그 크기의 단색 그림을 그 형식으로 만든다.
 * <p>
 * 예: {@code TestImages.png(512, 512)} — 정사각형 PNG, {@code TestImages.of("gif", 4, 4)} — 받지 않는 형식.
 */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] png(int width, int height) {
        return of("png", width, height);
    }

    public static byte[] jpeg(int width, int height) {
        return of("jpeg", width, height);
    }

    /**
     * @param imageIoFormatName ImageIO의 형식 이름 — png, jpeg, gif, bmp 등
     */
    public static byte[] of(String imageIoFormatName, int width, int height) {
        BufferedImage picture = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(picture, imageIoFormatName, bytes)) {
                throw new IllegalArgumentException("ImageIO가 만들 수 없는 형식입니다: " + imageIoFormatName);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return bytes.toByteArray();
    }
}
