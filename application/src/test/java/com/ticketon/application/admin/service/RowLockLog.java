package com.ticketon.application.admin.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 메모리 저장소들이 함께 쓰는 줄 잠금 기록 — 운영진 계정 줄과 주최자 줄을 잠근 차례를 "계정 1 FOR KEY SHARE", "주최자 1 FOR UPDATE"처럼
 * 적는다. 흐름이 잠금 순서(계정 줄 → 주최자 줄, organizer 서비스의 StaffAccountLocks)를 지키는지 보려고 둔다. 진짜 DB에서 두 흐름이 겹쳐도 교착하지 않는
 * 모습은 app-api의 경합 통합 테스트가 본다.
 */
final class RowLockLog {

    private final List<String> locks = new ArrayList<>();

    void record(String lock) {
        locks.add(lock);
    }

    /** 잠근 줄 — 잠근 차례대로. */
    List<String> locks() {
        return List.copyOf(locks);
    }
}
