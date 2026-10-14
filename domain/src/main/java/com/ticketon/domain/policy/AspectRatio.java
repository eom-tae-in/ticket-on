package com.ticketon.domain.policy;

/**
 * 가로세로 비율. 포스터 3:4 세로(docs/03)처럼 가로:세로 순서로 적는다.
 *
 * @param width  가로 (1 이상)
 * @param height 세로 (1 이상)
 */
public record AspectRatio(int width, int height) {

    public AspectRatio {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("가로세로 비율은 1 이상이어야 합니다: " + width + ":" + height);
        }
    }

    /** 세로가 가로보다 길면 세로형이다. */
    public boolean isPortrait() {
        return height > width;
    }

    @Override
    public String toString() {
        return width + ":" + height;
    }
}
