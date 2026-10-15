package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.domain.account.StaffAccount;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

/**
 * 운영진 계정 줄 잠금 — 소속 줄을 바꾸는 주최자 쪽 흐름(초대 수락 · 거절, 소유자 넘기기, 내보내기, 나가기)이 무엇이든 바꾸기 전에 맨 먼저
 * 부른다({@link StaffAccountRepository#findByIdForUpdate}의 "잠금 순서 — 계정 줄 → 초대 줄 → 소속 줄").
 * <p>
 * 관련된 사람이 여럿이면 <b>id가 작은 쪽부터</b> 잠근다 — 같은 두 사람을 잠그는 두 흐름이 서로 반대 순서로 잠가 교착하지 않게. 조작하는 사람도
 * 잠근다: 운영진 조작 기록 · 소속 줄을 넣을 때 그 사람의 계정 줄에 외래 키 확인 잠금(KEY SHARE)이 걸리는데, 다른 흐름이 그 계정 줄을 먼저
 * 잠그고 이 흐름의 소속 줄을 기다리면 교착하기 때문이다.
 */
final class StaffAccountLocks {

    private StaffAccountLocks() {
    }

    /**
     * 그 사람들의 계정 줄을 id 순서로 잠그고 읽는다. 같은 id가 여러 번 오면 한 번만 잠근다.
     *
     * @return 잠근 계정 — 계정 id로 찾는다. 없는 계정은 담기지 않는다.
     */
    static Map<Long, StaffAccount> lockInIdOrder(StaffAccountRepository repository, long... staffAccountIds) {
        Map<Long, StaffAccount> locked = new HashMap<>();
        LongStream.of(staffAccountIds).distinct().sorted().forEach(staffAccountId -> {
            Optional<StaffAccount> account = repository.findByIdForUpdate(staffAccountId);
            account.ifPresent(found -> locked.put(staffAccountId, found));
        });
        return locked;
    }
}
