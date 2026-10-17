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

    /**
     * 그 가로 · 세로 픽셀의 비율이 이 비율과 허용 오차 안에서 같은지 — 가로 ÷ 세로가 이 비율의 가로 ÷ 세로에서 벗어난 정도가 이 비율의 허용 오차
     * 이하인지 본다. 경계는 같은 것으로 본다. 정수로만 셈해 반올림 오차가 없다.
     * <p>
     * 예: 3:4에 오차 1%면 세로 1,200픽셀의 가로는 891~909픽셀이다(900 ± 9). 1:1 · 4:3은 3:4에서 크게 벗어나 맞지 않는다.
     *
     * @param pixelWidth  가로 픽셀 (1 이상)
     * @param pixelHeight 세로 픽셀 (1 이상)
     * @param tolerance   허용 오차 — 이 비율에 견준 백분율
     * @throws IllegalArgumentException 가로나 세로가 1보다 작으면
     */
    public boolean isMatchedBy(int pixelWidth, int pixelHeight, Percent tolerance) {
        if (pixelWidth < 1 || pixelHeight < 1) {
            throw new IllegalArgumentException("그림의 가로 · 세로는 1 이상이어야 합니다: " + pixelWidth + "x" + pixelHeight);
        }
        long deviation = Math.abs((long) pixelWidth * height - (long) pixelHeight * width);
        return deviation * 100 <= (long) width * pixelHeight * tolerance.value();
    }

    @Override
    public String toString() {
        return width + ":" + height;
    }
}
