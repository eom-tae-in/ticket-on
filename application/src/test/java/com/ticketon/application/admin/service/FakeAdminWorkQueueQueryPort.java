package com.ticketon.application.admin.service;

import com.ticketon.application.admin.port.out.AdminWorkQueueQueryPort;
import com.ticketon.application.admin.query.PendingAdminWork;
import java.util.ArrayList;
import java.util.List;

/** 처리 목록 조회 — 넣어 둔 요청을 넣은 순서(접수 순서로 넣는다)대로 돌려준다. */
final class FakeAdminWorkQueueQueryPort implements AdminWorkQueueQueryPort {

    private final List<PendingAdminWork> pendingWork = new ArrayList<>();

    void add(PendingAdminWork work) {
        pendingWork.add(work);
    }

    @Override
    public List<PendingAdminWork> pendingWork() {
        return List.copyOf(pendingWork);
    }
}
