package com.ticketon.domain.event;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 구역 색 — 좌석 선택 화면에서 구역을 가르는 색(docs/02 "관객에게는 번호를 보여주지 않습니다", docs/13 "좌석 선택 화면 — 구역은 색으로 구분하고
 * 범례를 둠"). 배치도 에디터에서 좌석을 구역에 배정하는 일이 곧 그 색으로 칠하는 일이다("좌석을 구역에 배정 (색칠)").
 * <p>
 * 화면이 그대로 쓰는 웹 색 값 {@code #RRGGBB}(16진수 여섯 자리)로 받는다. 대소문자를 가리지 않고 받아 대문자로 맞춘다 — {@code #e5484d}와
 * {@code #E5484D}는 같은 색이다. 고를 수 있는 색의 목록은 문서에 없어 두지 않았다.
 *
 * @param value {@code #RRGGBB}, 16진수는 대문자
 */
public record ZoneColor(String value) {

    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9A-F]{6}");

    public ZoneColor {
        Objects.requireNonNull(value, "value");
        value = normalize(value);
        if (!HEX_COLOR.matcher(value).matches()) {
            throw new IllegalArgumentException("구역 색은 #RRGGBB 꼴이어야 합니다: " + value);
        }
    }

    /**
     * 적은 값으로 만든다. 앞뒤 공백을 걷고 대문자로 맞춘다.
     *
     * @throws IllegalArgumentException {@code #RRGGBB} 꼴이 아니면
     */
    public static ZoneColor of(String input) {
        return new ZoneColor(input);
    }

    /** {@link #of}로 만들 수 있는 값인지. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(String input) {
        return input != null && HEX_COLOR.matcher(normalize(input)).matches();
    }

    private static String normalize(String input) {
        return input.strip().toUpperCase(Locale.ROOT);
    }
}
