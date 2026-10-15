package com.ticketon.common;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 그림 파일 형식 — 파일 첫머리의 시그니처(매직 넘버)로 알아본다.
 * <p>
 * 올린 쪽이 적은 형식(Content-Type)이나 파일 이름의 확장자는 바꿔 적을 수 있어 그것만 믿지 않는다. 실제 바이트가 그 형식으로 시작하는지
 * {@link #detect}로 본다. 어떤 형식을 받을지는 그 파일을 쓰는 쪽(주최자 로고 등)이 정한다. 여기에는 알아볼 수 있는 형식만 둔다.
 */
public enum ImageFormat {

    /** PNG — 첫 8바이트가 {@code 89 50 4E 47 0D 0A 1A 0A}. */
    PNG("image/png", "png", "png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),

    /** JPEG — 첫 3바이트가 {@code FF D8 FF}. */
    JPEG("image/jpeg", "jpg", "jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    private final String mediaType;
    private final String fileExtension;
    private final String imageIoFormatName;
    private final byte[] signature;

    ImageFormat(String mediaType, String fileExtension, String imageIoFormatName, byte[] signature) {
        this.mediaType = mediaType;
        this.fileExtension = fileExtension;
        this.imageIoFormatName = imageIoFormatName;
        this.signature = signature;
    }

    /**
     * 실제 바이트가 어느 형식으로 시작하는지. 시그니처만 보므로 뒤가 깨진 파일도 형식은 알아본다 — 그림으로 읽히는지는 {@link ImageFile#inspect}가 본다.
     *
     * @return 알아볼 수 없으면 빈 값
     */
    public static Optional<ImageFormat> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(format -> format.startsTheContent(content)).findFirst();
    }

    /**
     * 올린 쪽이 적은 형식(Content-Type)에 맞는 그림 형식. 대소문자와 뒤에 붙은 매개변수({@code ; charset=…})는 따지지 않는다.
     *
     * @return 알아볼 수 없는 형식이거나 비었으면 빈 값
     */
    public static Optional<ImageFormat> ofMediaType(String declaredMediaType) {
        if (declaredMediaType == null) {
            return Optional.empty();
        }
        String essence = declaredMediaType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(format -> format.mediaType.equals(essence)).findFirst();
    }

    /**
     * 파일 확장자(점 없이, 예: {@code png})에 맞는 그림 형식. 이 서비스가 붙인 확장자만 알아본다 — 소문자, JPEG는 {@code jpg}.
     *
     * @return 알아볼 수 없으면 빈 값
     */
    public static Optional<ImageFormat> ofFileExtension(String fileExtension) {
        return Arrays.stream(values()).filter(format -> format.fileExtension.equals(fileExtension)).findFirst();
    }

    /** 응답에 싣는 형식(Content-Type), 예: {@code image/png}. */
    public String mediaType() {
        return mediaType;
    }

    /** 저장할 때 붙이는 확장자(점 없이), 예: {@code png}. */
    public String fileExtension() {
        return fileExtension;
    }

    /** JDK ImageIO가 이 형식의 해독기를 찾을 때 쓰는 이름. */
    String imageIoFormatName() {
        return imageIoFormatName;
    }

    private boolean startsTheContent(byte[] content) {
        return content.length >= signature.length
                && Arrays.equals(content, 0, signature.length, signature, 0, signature.length);
    }
}
