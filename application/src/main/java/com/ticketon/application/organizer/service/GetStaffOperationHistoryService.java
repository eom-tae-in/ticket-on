package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.in.GetStaffOperationHistoryQuery;
import com.ticketon.application.organizer.port.in.GetStaffOperationHistoryUseCase;
import com.ticketon.application.organizer.port.in.StaffOperationHistoryResult;
import com.ticketon.application.organizer.port.in.StaffOperationHistoryResult.Entry;
import com.ticketon.application.organizer.port.out.StaffOperationHistoryQueryPort;
import com.ticketon.application.organizer.query.StaffOperationHistoryEntry;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주최자 이력의 운영진 조작 기록 조회 — 읽기 전용이라 도메인을 거치지 않고 조회 포트가 돌려준 칸을 옮긴다.
 * <p>
 * 한 줄을 더 읽어 더 앞의 기록이 있는지 안다. 있으면 이번에 돌려준 마지막 기록의 id를 이어 보기 값으로 준다.
 */
@Service
public class GetStaffOperationHistoryService implements GetStaffOperationHistoryUseCase {

    private final StaffOperationHistoryQueryPort historyQueryPort;

    public GetStaffOperationHistoryService(StaffOperationHistoryQueryPort historyQueryPort) {
        this.historyQueryPort = historyQueryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public StaffOperationHistoryResult getHistory(GetStaffOperationHistoryQuery query) {
        int limitWithOneMore = Math.addExact(query.limit(), 1);
        List<StaffOperationHistoryEntry> fetched =
                historyQueryPort.historyOf(query.organizerId(), query.beforeRecordId(), limitWithOneMore);
        boolean hasMore = fetched.size() > query.limit();
        List<Entry> entries = fetched.stream()
                .limit(query.limit())
                .map(GetStaffOperationHistoryService::toEntry)
                .toList();
        return new StaffOperationHistoryResult(entries, hasMore ? entries.getLast().recordId() : null);
    }

    private static Entry toEntry(StaffOperationHistoryEntry entry) {
        return new Entry(entry.recordId(), entry.operatedAt(), entry.staffAccountId(), entry.staffNickname(),
                entry.staffName(), entry.staffRole(), entry.action(), entry.targetKind(), entry.targetId(),
                entry.summary());
    }
}
