package com.ticketon.domain.event;

import java.util.Optional;

/**
 * 재입장 설정(docs/03 "재입장 설정"). <b>처음 값은 유형과 상관없이 끔</b>이다({@link #defaultSetting}).
 * <ul>
 *   <li>끔 — 두 번째 스캔부터 경고한다(docs/10 "중복 스캔").</li>
 *   <li>켬 — 여러 번 찍어도 정상 통과하고 입장 횟수만 기록한다.</li>
 * </ul>
 * 처음에는 페스티벌만 켜는 안이었으나, 모르고 켜지는 일을 막으려고 전부 끔으로 통일했다(docs/99 D "재입장 기본값").
 * 켤 때는 주최자에게 경고를 보여 준다({@link #organizerWarning}). 이 상수 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum ReentrySetting {

    /** 끔 — 두 번째 스캔부터 경고. */
    NOT_ALLOWED,

    /** 켬 — 여러 번 찍어도 정상 통과, 입장 횟수만 기록. */
    ALLOWED;

    /** 켤 때 주최자에게 보여 주는 경고 — docs/03 "재입장 설정"의 문구 그대로. */
    public static final String ALLOWED_WARNING = "켜면 같은 QR로 여러 번 입장할 수 있습니다. "
            + "티켓 이미지를 공유해 여러 명이 들어오는 것을 막을 수 없으니, 팔찌나 도장 같은 현장 수단을 함께 쓰시는 걸 권합니다.";

    /** 처음 값 — 끔. */
    public static ReentrySetting defaultSetting() {
        return NOT_ALLOWED;
    }

    /** 이 설정일 때 주최자에게 보여 줄 경고 — 켬이면 {@link #ALLOWED_WARNING}, 끔이면 없다. */
    public Optional<String> organizerWarning() {
        return this == ALLOWED ? Optional.of(ALLOWED_WARNING) : Optional.empty();
    }

    /** 같은 티켓으로 여러 번 들어와도 정상 통과하는지. */
    public boolean isAllowed() {
        return this == ALLOWED;
    }
}
