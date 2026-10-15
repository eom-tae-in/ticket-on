package com.ticketon.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * 그림 파일의 실제 형식과 가로·세로 픽셀 — 올린 파일의 바이트에서 읽는다({@link #inspect}).
 * <p>
 * 형식은 시그니처로({@link ImageFormat#detect}), 크기는 JDK ImageIO의 그 형식 해독기로 읽는다. <b>머리말만 읽고 그림 전체를 풀지
 * 않는다</b> — 용량은 작아도 가로·세로를 아주 크게 적은 파일을 다 풀면 메모리를 크게 잡아먹으므로, 크기를 확인하는 데 필요한 만큼만 읽는다.
 * 임시 파일도 만들지 않는다(메모리 안에서 읽는다).
 *
 * @param format 실제 바이트의 형식
 * @param width  가로 픽셀 (1 이상)
 * @param height 세로 픽셀 (1 이상)
 */
public record ImageFile(ImageFormat format, int width, int height) {

    public ImageFile {
        Objects.requireNonNull(format, "format");
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("그림의 가로·세로는 1 이상이어야 합니다: " + width + "x" + height);
        }
    }

    /**
     * 바이트가 알아볼 수 있는 형식의 그림인지 보고, 그렇다면 형식과 크기를 돌려준다.
     *
     * @return 알아볼 수 없는 형식이거나, 시그니처는 맞지만 머리말이 깨져 크기를 읽을 수 없으면 빈 값
     */
    public static Optional<ImageFile> inspect(byte[] content) {
        return ImageFormat.detect(content).flatMap(format -> readSize(format, content));
    }

    /** 가로와 세로가 같은지. */
    public boolean isSquare() {
        return width == height;
    }

    /**
     * 믿을 수 없는 바이트를 JDK 해독기로 읽는다. 깨진 머리말에 해독기는 보통 IOException을 던지지만, 형식에 따라 실행 예외를 던지기도 한다 — 어느
     * 쪽이든 "크기를 읽을 수 없는 그림"으로 답한다.
     */
    private static Optional<ImageFile> readSize(ImageFormat format, byte[] content) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format.imageIoFormatName());
        if (!readers.hasNext()) {
            throw new IllegalStateException("JDK에 " + format + " 해독기가 없습니다");
        }
        ImageReader reader = readers.next();
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width < 1 || height < 1) {
                return Optional.empty();
            }
            return Optional.of(new ImageFile(format, width, height));
        } catch (IOException | RuntimeException unreadableHeader) {
            return Optional.empty();
        } finally {
            reader.dispose();
        }
    }
}
