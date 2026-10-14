package com.ticketon.application.account.service;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** 테스트용 무작위 수 — 정해 둔 자리를 돌려주고, 받은 범위를 부른 차례대로 적어 둔다. */
final class FixedIndexRandom implements RandomGenerator {

    private final int index;
    private final List<Integer> requestedBounds = new ArrayList<>();

    FixedIndexRandom(int index) {
        this.index = index;
    }

    List<Integer> requestedBounds() {
        return List.copyOf(requestedBounds);
    }

    @Override
    public int nextInt(int bound) {
        requestedBounds.add(bound);
        if (index >= bound) {
            throw new IllegalStateException("정해 둔 자리(" + index + ")가 범위(" + bound + ")를 벗어납니다");
        }
        return index;
    }

    @Override
    public long nextLong() {
        throw new UnsupportedOperationException("nextInt(bound)만 쓴다");
    }
}
