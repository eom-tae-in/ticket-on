package com.ticketon.application.account.service;

import com.ticketon.application.account.port.out.SessionTerminationPort;
import com.ticketon.domain.account.AccountRef;
import java.util.ArrayList;
import java.util.List;

/** 세션을 끊으라고 한 주체를 부른 차례대로 모아 두는 세션 끊기 포트. 실제 세션 저장소에서 끊기는 모습은 통합 테스트가 본다. */
final class RecordingSessionTerminationPort implements SessionTerminationPort {

    private final List<AccountRef> terminatedAccounts = new ArrayList<>();

    List<AccountRef> terminatedAccounts() {
        return List.copyOf(terminatedAccounts);
    }

    @Override
    public int terminateAllSessions(AccountRef account) {
        terminatedAccounts.add(account);
        return 0;
    }
}
