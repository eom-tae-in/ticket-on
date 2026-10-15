package com.ticketon.domain.organizer;

import com.ticketon.common.ImageFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 주최자 로고 — 정사각형으로 잘라 올린 그림 파일(docs/01 "공개 페이지 — 로고").
 * <p>
 * <b>선택이다.</b> 없으면 이름 첫 글자 + 주최자마다 정해진 색으로 보인다({@link Organizer#initialAvatar}). 파일 자체는 파일 저장소에 있고
 * 주최자는 그 파일을 가리키는 열쇠만 갖는다. 부적절하거나 다른 단체를 흉내 내면 관리자가 초기화한다.
 * <p>
 * <b>열쇠는 추측할 수 없는 값 + 형식의 확장자다</b>(예: {@code Xb3…(32자).png}, {@link #issue}). 올린 사람이 붙인 파일 이름은 쓰지 않는다 —
 * 열쇠는 누구나 로그인 없이 여는 공개 주소에 그대로 들어가므로, 순차 번호처럼 추측할 수 있으면 지운 로고나 다른 주최자의 파일을 찾아볼 수 있고,
 * 사람이 적은 이름이면 경로를 거슬러 올라가는 이름({@code ../})이 섞일 수 있다. 그래서 열쇠는 URL 안전 문자와 점 하나로 된 한 덩어리만 받는다.
 *
 * @param fileKey 파일 저장소에서 로고 파일을 가리키는 열쇠 — URL 안전 문자 {@value #TOKEN_LENGTH}자 + "." + 그림 형식의 확장자
 */
public record OrganizerLogo(String fileKey) {

    /** 열쇠 앞부분(추측할 수 없는 값)의 길이(URL 안전 문자 수) — 한 자에 6비트, 192비트. */
    public static final int TOKEN_LENGTH = 32;

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{" + TOKEN_LENGTH + "}");

    public OrganizerLogo {
        Objects.requireNonNull(fileKey, "fileKey");
        if (formatOf(fileKey).isEmpty()) {
            throw new IllegalArgumentException("로고 파일 열쇠의 꼴이 아닙니다: " + fileKey);
        }
    }

    /**
     * 새로 올린 로고에 열쇠를 붙인다.
     *
     * @param token  추측할 수 없는 값 — 서비스가 TokenGenerator로 {@value #TOKEN_LENGTH}자를 만들어 넘긴다
     * @param format 로고 파일의 실제 형식({@link OrganizerLogoImage#format})
     * @throws IllegalArgumentException 값의 꼴이 맞지 않으면
     */
    public static OrganizerLogo issue(String token, ImageFormat format) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(format, "format");
        return new OrganizerLogo(token + "." + format.fileExtension());
    }

    /** 로고 파일 열쇠의 꼴인지. 공개 주소로 들어온 열쇠를 저장소에 묻기 전에 같은 규칙으로 거르려고 둔다. */
    public static boolean isValidFileKey(String fileKey) {
        return fileKey != null && formatOf(fileKey).isPresent();
    }

    /** 로고 파일의 형식 — 열쇠의 확장자로 안다. 파일을 내줄 때 형식(Content-Type)을 정한다. */
    public ImageFormat format() {
        return formatOf(fileKey).orElseThrow();
    }

    private static Optional<ImageFormat> formatOf(String fileKey) {
        int dot = fileKey.lastIndexOf('.');
        if (dot < 0 || !TOKEN.matcher(fileKey.substring(0, dot)).matches()) {
            return Optional.empty();
        }
        return ImageFormat.ofFileExtension(fileKey.substring(dot + 1));
    }
}
