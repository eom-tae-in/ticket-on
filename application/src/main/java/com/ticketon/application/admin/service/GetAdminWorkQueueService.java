package com.ticketon.application.admin.service;

import com.ticketon.application.admin.port.in.AdminWorkQueueResult;
import com.ticketon.application.admin.port.in.GetAdminWorkQueueUseCase;
import com.ticketon.application.admin.port.out.AdminWorkQueueQueryPort;
import com.ticketon.application.admin.query.PendingAdminWork;
import com.ticketon.common.BusinessDayCalculator;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.organizer.OrganizerReviewDeadline;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 처리 목록 조회 — 조회 포트가 모은 요청에 강조 여부를 붙인다. 강조 기준은 종류마다 다르고, 기준 판단은 도메인이 한다.
 * <ul>
 *   <li>주최자 신청 — 심사 목표일이 다가왔는지({@link OrganizerReviewDeadline#isApproaching}). 목표일은 제출한 날부터 영업일 3일이다.</li>
 *   <li>소유자 변경 — 강조하지 않는다. 이미 바뀐 일을 사후 확인하는 것이라 문서에 정한 기한이 없다.</li>
 *   <li>소유자 이전 신청 — 강조하지 않는다. 문서에 정한 처리 기한이 없다.</li>
 *   <li>주최자명 변경 — 강조하지 않는다. 이미 바뀐 일을 사후 확인하는 것이라 문서에 정한 기한이 없다.</li>
 *   <li>정산 계좌 · 사업자 정보 변경 확인 — 강조하지 않는다. 문서에 정한 처리 기한이 없다(확인 중에는 기존 값이 그대로 쓰인다).</li>
 * </ul>
 * 종류를 더하는 이슈는 강조 기준도 여기 함께 더한다. 오늘은 한국 날짜로 본다.
 */
@Service
public class GetAdminWorkQueueService implements GetAdminWorkQueueUseCase {

    private final AdminWorkQueueQueryPort workQueueQueryPort;
    private final BusinessDayCalculator businessDayCalculator;
    private final Clock clock;

    public GetAdminWorkQueueService(AdminWorkQueueQueryPort workQueueQueryPort,
            BusinessDayCalculator businessDayCalculator, Clock clock) {
        this.workQueueQueryPort = workQueueQueryPort;
        this.businessDayCalculator = businessDayCalculator;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminWorkQueueResult getWorkQueue() {
        LocalDate today = KoreanTime.toDate(clock.instant());
        return new AdminWorkQueueResult(workQueueQueryPort.pendingWork().stream()
                .map(work -> new AdminWorkQueueResult.Item(work.kind(), work.targetId(), work.title(),
                        work.receivedAt(), isHighlighted(work, today)))
                .toList());
    }

    private boolean isHighlighted(PendingAdminWork work, LocalDate today) {
        return switch (work.kind()) {
            case ORGANIZER_APPLICATION -> OrganizerReviewDeadline.of(work.receivedAt(), businessDayCalculator)
                    .isApproaching(today, businessDayCalculator);
            case OWNERSHIP_CHANGE, OWNERSHIP_CLAIM, ORGANIZER_NAME_CHANGE, ORGANIZER_CHANGE_REQUEST -> false;
        };
    }
}
