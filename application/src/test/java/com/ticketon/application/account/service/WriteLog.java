package com.ticketon.application.account.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 메모리 저장소들이 함께 쓰는 쓰기 기록 — 계정 잠금·링크 대체·링크 저장·링크 사용·비밀번호 저장을 부른 차례대로 적는다.
 * 비밀번호 찾기와 재설정이 같은 잠금 순서(계정 → 링크)를 지키는지 보려고 둔다. 진짜 DB에서 두 흐름이 겹쳐도 교착되지 않는 모습은
 * 저장 어댑터 테스트가 본다.
 */
final class WriteLog {

    private final List<String> writes = new ArrayList<>();

    void record(String write) {
        writes.add(write);
    }

    /** 부른 차례대로의 쓰기. */
    List<String> writes() {
        return List.copyOf(writes);
    }

    void clear() {
        writes.clear();
    }
}
