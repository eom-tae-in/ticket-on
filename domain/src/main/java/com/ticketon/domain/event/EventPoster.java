package com.ticketon.domain.event;

import com.ticketon.common.ImageFormat;
import com.ticketon.domain.policy.QuantityPolicy;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 행사 포스터 — 파일 저장소에 올린 그림 파일을 가리키는 열쇠(docs/03 "기본 정보").
 * <p>
 * <b>{@link QuantityPolicy#POSTER_ASPECT_RATIO 3:4 세로} 고정이다</b> — 비율이 제각각이면 목록 카드가 지저분해진다. 화면이 업로드 후 크롭
 * 도구를 주고, 서버는 올릴 때 그림 크기로 비율과 용량 상한을 확인한다({@link EventPosterImage}). 파일 자체는 파일 저장소에 있고 행사는 그 열쇠만
 * 갖는다.
 * <p>
 * 포스터는 1단계의 필수 항목이지만 행사를 만든 뒤 따로 올린다 — 올릴 행사가 먼저 있어야 하기 때문이다. 그래서 행사는 포스터 없이 작성 중일 수
 * 있고, 판매를 시작하기 전에 갖춰야 한다({@link Event#isBasicInformationComplete}).
 * <p>
 * <b>열쇠는 추측할 수 없는 값 + 형식의 확장자다</b>(예: {@code Xb3…(32자).jpg}, {@link #issue}) — 주최자 로고 열쇠와 같은 꼴이다. 올린 사람이 붙인
 * 파일 이름은 쓰지 않는다. 열쇠는 누구나 로그인 없이 여는 공개 주소에 그대로 들어가므로(카톡 링크 미리보기도 이 주소로 포스터를 가져간다),
 * 순차 번호처럼 추측할 수 있으면 링크 전용 · 암호 보호 행사의 포스터나 바꾸기 전 포스터를 찾아볼 수 있고, 사람이 적은 이름이면 경로를 거슬러
 * 올라가는 이름({@code ../})이 섞일 수 있다. 그래서 열쇠는 URL 안전 문자와 점 하나로 된 한 덩어리만 받는다.
 *
 * @param fileKey 파일 저장소에서 포스터 파일을 가리키는 열쇠 — URL 안전 문자 {@value #TOKEN_LENGTH}자 + "." + 그림 형식의 확장자
 */
public record EventPoster(String fileKey) {

    /** 열쇠 앞부분(추측할 수 없는 값)의 길이(URL 안전 문자 수) — 한 자에 6비트, 192비트. */
    public static final int TOKEN_LENGTH = 32;

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{" + TOKEN_LENGTH + "}");

    public EventPoster {
        Objects.requireNonNull(fileKey, "fileKey");
        if (formatOf(fileKey).isEmpty()) {
            throw new IllegalArgumentException("포스터 파일 열쇠의 꼴이 아닙니다: " + fileKey);
        }
    }

    /**
     * 새로 올린 포스터에 열쇠를 붙인다.
     *
     * @param token  추측할 수 없는 값 — 서비스가 TokenGenerator로 {@value #TOKEN_LENGTH}자를 만들어 넘긴다
     * @param format 포스터 파일의 실제 형식({@link EventPosterImage#format})
     * @throws IllegalArgumentException 값의 꼴이 맞지 않으면
     */
    public static EventPoster issue(String token, ImageFormat format) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(format, "format");
        return new EventPoster(token + "." + format.fileExtension());
    }

    /** 포스터 파일 열쇠의 꼴인지. 공개 주소로 들어온 열쇠를 저장소에 묻기 전에 같은 규칙으로 거르려고 둔다. */
    public static boolean isValidFileKey(String fileKey) {
        return fileKey != null && formatOf(fileKey).isPresent();
    }

    /** 포스터 파일의 형식 — 열쇠의 확장자로 안다. 파일을 내줄 때 형식(Content-Type)을 정한다. */
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
