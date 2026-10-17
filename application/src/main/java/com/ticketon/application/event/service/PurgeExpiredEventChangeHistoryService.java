package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.PurgeExpiredEventChangeHistoryResult;
import com.ticketon.application.event.port.in.PurgeExpiredEventChangeHistoryUseCase;
import com.ticketon.application.event.port.out.EventChangeHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 보관 기간이 지난 행사 변경 이력 지우기 — 이력이 남아 있는 행사를 찾아 행사마다 따로 판단하고 지운다({@link EventChangeHistoryPurger}).
 * <p>
 * <b>트랜잭션은 행사마다 따로다</b> — 이 서비스는 트랜잭션을 열지 않는다. 한 트랜잭션으로 묶으면 한 행사의 실패가 모든 행사의 삭제를 되돌리고, 지운
 * 포스터 파일과 이력이 어긋날 수 있다. 행사마다 나누면 실패한 행사만 이력이 그대로 남아 다음 작업이 다시 시도하고, 행사 줄도 그 행사를 지우는
 * 동안만 잡는다.
 * <p>
 * 실패를 삼키지 않는다 — 나머지 행사를 모두 처리한 뒤 실패한 행사 id와 원인(첫 원인은 cause, 나머지는 suppressed)을 담아 던진다. 정해진 시각
 * 작업의 오류 기록에 남는다(작성 중 행사 30일 미수정 알림과 같은 방식).
 */
@Service
public class PurgeExpiredEventChangeHistoryService implements PurgeExpiredEventChangeHistoryUseCase {

    private final EventChangeHistoryRepository historyRepository;
    private final EventChangeHistoryPurger purger;
    private final Clock clock;

    public PurgeExpiredEventChangeHistoryService(EventChangeHistoryRepository historyRepository, EventChangeHistoryPurger purger,
            Clock clock) {
        this.historyRepository = historyRepository;
        this.purger = purger;
        this.clock = clock;
    }

    @Override
    public PurgeExpiredEventChangeHistoryResult purgeExpiredChangeHistory() {
        Instant now = clock.instant();
        List<Long> purged = new ArrayList<>();
        Map<Long, RuntimeException> failures = new LinkedHashMap<>();
        for (long eventId : historyRepository.findEventIdsWithHistory()) {
            try {
                if (purger.purgeIfExpired(eventId, now)) {
                    purged.add(eventId);
                }
            } catch (RuntimeException failure) {
                failures.put(eventId, failure);
            }
        }
        if (!failures.isEmpty()) {
            throw failureOf(failures, purged);
        }
        return new PurgeExpiredEventChangeHistoryResult(purged);
    }

    private static IllegalStateException failureOf(Map<Long, RuntimeException> failures, List<Long> purged) {
        List<RuntimeException> causes = List.copyOf(failures.values());
        IllegalStateException failure = new IllegalStateException("보관 기간이 지난 변경 이력을 지우지 못한 행사가 있습니다: 행사 "
                + failures.keySet() + " (지운 행사 " + purged + ")", causes.getFirst());
        causes.stream().skip(1).forEach(failure::addSuppressed);
        return failure;
    }
}
