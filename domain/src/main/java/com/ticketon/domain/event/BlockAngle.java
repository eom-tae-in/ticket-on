package com.ticketon.domain.event;

/**
 * 블록의 각도 — 부채꼴 객석을 만들려고 블록을 돌린 정도(docs/02 "에디터는 블록 단위" — 블록 위치 이동, 각도 조절 (부채꼴 객석용)).
 * <p>
 * 도(°) 단위 정수로, 0 이상 360 미만으로 맞춰 둔다 — -15°로 돌리면 345°, 375°로 돌리면 15°다({@link #ofDegrees}). 블록을 돌려도 좌석의 행 · 열과
 * 번호는 그대로다.
 *
 * @param degrees 0 이상 360 미만
 */
public record BlockAngle(int degrees) {

    private static final int FULL_TURN = 360;

    /** 돌리지 않은 블록. */
    public static final BlockAngle NONE = new BlockAngle(0);

    public BlockAngle {
        if (degrees < 0 || degrees >= FULL_TURN) {
            throw new IllegalArgumentException("블록 각도는 0 이상 360 미만으로 맞춰 둡니다: " + degrees);
        }
    }

    /** 돌린 각도로 만든다. 0 이상 360 미만으로 맞춘다 — -15는 345, 375는 15. */
    public static BlockAngle ofDegrees(int degrees) {
        return new BlockAngle(Math.floorMod(degrees, FULL_TURN));
    }
}
