package com.ticketon.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.application.admin.port.in.AdminWorkQueueResult;
import com.ticketon.application.admin.query.AdminWorkKind;
import com.ticketon.application.admin.query.PendingAdminWork;
import com.ticketon.common.BusinessDayCalculator;
import com.ticketon.common.HolidayCalendar;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("관리자 처리 목록 유스케이스 — 주최자 신청은 심사 목표일(영업일 3일)이 다가오면 강조")
class GetAdminWorkQueueServiceTest {

    private final FakeAdminWorkQueueQueryPort queryPort = new FakeAdminWorkQueueQueryPort();
    private final MutableClock clock = MutableClock.at(LocalDateTime.of(2026, 10, 19, 9, 0));

    private final GetAdminWorkQueueService service = new GetAdminWorkQueueService(queryPort,
            new BusinessDayCalculator(HolidayCalendar.none()), clock);

    @Test
    @DisplayName("처리할 요청이 없으면 빈 목록이다")
    void emptyQueue() {
        assertThat(service.getWorkQueue().items()).isEmpty();
    }

    @Test
    @DisplayName("2026-10-19(월) 기준 — 목표일이 오늘(14일 제출)·내일(15일 제출)이면 강조하고, 모레(16일 제출)면 아직이다")
    void highlightsApplicationsNearReviewTarget() {
        queryPort.add(application(1, "극단 봄날", LocalDateTime.of(2026, 10, 14, 11, 0)));
        queryPort.add(application(2, "극단 열두달", LocalDateTime.of(2026, 10, 15, 10, 0)));
        queryPort.add(application(3, "늦여름", LocalDateTime.of(2026, 10, 16, 18, 0)));

        AdminWorkQueueResult result = service.getWorkQueue();

        assertThat(result.items()).containsExactly(
                item(1, "극단 봄날", LocalDateTime.of(2026, 10, 14, 11, 0), true),
                item(2, "극단 열두달", LocalDateTime.of(2026, 10, 15, 10, 0), true),
                item(3, "늦여름", LocalDateTime.of(2026, 10, 16, 18, 0), false));
    }

    @Test
    @DisplayName("오늘은 한국 날짜로 본다 — 10-19 23:30(한국)은 아직 19일이라 16일 제출 신청(목표일 21일)은 강조하지 않는다")
    void todayIsKoreanDate() {
        clock.moveTo(LocalDateTime.of(2026, 10, 19, 23, 30));
        queryPort.add(application(3, "늦여름", LocalDateTime.of(2026, 10, 16, 18, 0)));

        assertThat(service.getWorkQueue().items()).singleElement()
                .satisfies(item -> assertThat(item.highlighted()).isFalse());
        assertThat(KoreanTime.toDate(clock.instant())).isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    @DisplayName("목표일이 지난 신청도 강조한다")
    void overdueIsHighlighted() {
        clock.moveTo(LocalDateTime.of(2026, 11, 2, 9, 0));
        queryPort.add(application(2, "극단 열두달", LocalDateTime.of(2026, 10, 15, 10, 0)));

        assertThat(service.getWorkQueue().items()).singleElement()
                .satisfies(item -> assertThat(item.highlighted()).isTrue());
    }

    private static PendingAdminWork application(long applicationId, String name, LocalDateTime submittedAtKorea) {
        return new PendingAdminWork(AdminWorkKind.ORGANIZER_APPLICATION, applicationId, name,
                KoreanTime.toInstant(submittedAtKorea));
    }

    private static AdminWorkQueueResult.Item item(long applicationId, String name, LocalDateTime submittedAtKorea,
            boolean highlighted) {
        Instant submittedAt = KoreanTime.toInstant(submittedAtKorea);
        return new AdminWorkQueueResult.Item(AdminWorkKind.ORGANIZER_APPLICATION, applicationId, name, submittedAt,
                highlighted);
    }
}
