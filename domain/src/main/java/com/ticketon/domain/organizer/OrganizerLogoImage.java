package com.ticketon.domain.organizer;

import com.ticketon.common.ImageFile;
import com.ticketon.common.ImageFormat;
import com.ticketon.domain.policy.ProposedPolicy;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * 주최자 로고로 올린 그림 파일 — 로고로 받을 수 있다고 확인한 파일만 이 값이 된다(docs/01 "공개 페이지 — 로고").
 * <p>
 * 넷을 차례로 본다({@link OrganizerLogoImageProblem}). 처음 걸린 것으로 거절한다.
 * <ol>
 *   <li>빈 파일이 아니다.</li>
 *   <li>용량이 상한({@link ProposedPolicy#ORGANIZER_LOGO_MAXIMUM_SIZE}, 확인 필요) 이하다.</li>
 *   <li>형식이 받는 형식({@link ProposedPolicy#ORGANIZER_LOGO_ACCEPTED_FORMATS}, 확인 필요)이다 — <b>올린 쪽이 적은 형식(Content-Type)과 실제
 *       바이트의 시그니처가 모두</b> 받는 형식이고 서로 같아야 한다. 확장자나 적은 형식만 믿지 않는다.</li>
 *   <li>정사각형이다 — 가로·세로 픽셀이 같다. 크롭은 화면이 하고, 서버는 잘린 결과가 정사각형인지만 본다. 크기는 JDK ImageIO로 그림 머리말만
 *       읽어 확인한다({@link ImageFile}).</li>
 * </ol>
 * 파일 이름은 받지 않는다. 저장할 때는 추측할 수 없는 열쇠로 이름을 새로 붙인다({@link OrganizerLogo#issue}).
 */
public final class OrganizerLogoImage {

    private final ImageFormat format;
    private final int sideLength;
    private final byte[] content;

    private OrganizerLogoImage(ImageFormat format, int sideLength, byte[] content) {
        this.format = format;
        this.sideLength = sideLength;
        this.content = content;
    }

    /**
     * 올린 파일을 로고로 받는다.
     *
     * @param declaredMediaType 올린 쪽이 적은 형식(Content-Type). 없으면 null.
     * @param content           파일 내용
     * @throws IllegalArgumentException 로고로 받을 수 없으면 — 들어오는 쪽이 먼저 {@link #problemOf}로 걸러야 한다
     */
    public static OrganizerLogoImage of(String declaredMediaType, byte[] content) {
        Objects.requireNonNull(content, "content");
        byte[] copied = content.clone();
        Inspection inspection = inspect(declaredMediaType, copied);
        if (inspection.problem() != null) {
            throw new IllegalArgumentException("로고로 받을 수 없는 파일입니다: " + inspection.problem());
        }
        ImageFile image = inspection.image();
        return new OrganizerLogoImage(image.format(), image.width(), copied);
    }

    /**
     * 올린 파일을 로고로 받을 수 없는 까닭. 들어오는 쪽이 입력을 검사할 때 같은 규칙을 쓰려고 둔다.
     *
     * @param declaredMediaType 올린 쪽이 적은 형식(Content-Type). 없으면 null.
     * @return 받을 수 있으면 빈 값
     */
    public static Optional<OrganizerLogoImageProblem> problemOf(String declaredMediaType, byte[] content) {
        Objects.requireNonNull(content, "content");
        return Optional.ofNullable(inspect(declaredMediaType, content).problem());
    }

    private static Inspection inspect(String declaredMediaType, byte[] content) {
        if (content.length == 0) {
            return Inspection.rejected(OrganizerLogoImageProblem.EMPTY);
        }
        if (ProposedPolicy.ORGANIZER_LOGO_MAXIMUM_SIZE.isExceededBy(content.length)) {
            return Inspection.rejected(OrganizerLogoImageProblem.TOO_LARGE);
        }
        Optional<ImageFormat> declaredFormat = ImageFormat.ofMediaType(declaredMediaType)
                .filter(ProposedPolicy.ORGANIZER_LOGO_ACCEPTED_FORMATS::contains);
        Optional<ImageFile> image = ImageFile.inspect(content);
        if (declaredFormat.isEmpty() || image.isEmpty() || image.get().format() != declaredFormat.get()) {
            return Inspection.rejected(OrganizerLogoImageProblem.UNSUPPORTED_FORMAT);
        }
        if (!image.get().isSquare()) {
            return Inspection.rejected(OrganizerLogoImageProblem.NOT_SQUARE);
        }
        return Inspection.accepted(image.get());
    }

    /** 실제 바이트의 형식. */
    public ImageFormat format() {
        return format;
    }

    /** 한 변의 픽셀 수 — 정사각형이라 가로와 세로가 같다. */
    public int sideLength() {
        return sideLength;
    }

    /** 파일 용량(바이트). */
    public long sizeInBytes() {
        return content.length;
    }

    /** 파일 내용 — 고쳐도 이 값은 바뀌지 않는 사본이다. */
    public byte[] content() {
        return content.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OrganizerLogoImage image && format == image.format && Arrays.equals(content, image.content);
    }

    @Override
    public int hashCode() {
        return 31 * format.hashCode() + Arrays.hashCode(content);
    }

    /** 내용은 싣지 않는다. */
    @Override
    public String toString() {
        return "OrganizerLogoImage[" + format + ", " + sideLength + "x" + sideLength + ", " + content.length + " bytes]";
    }

    private record Inspection(ImageFile image, OrganizerLogoImageProblem problem) {

        static Inspection accepted(ImageFile image) {
            return new Inspection(image, null);
        }

        static Inspection rejected(OrganizerLogoImageProblem problem) {
            return new Inspection(null, problem);
        }
    }
}
