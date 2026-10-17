package com.ticketon.batch.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.application.event.port.in.NotifyIdleDraftEventsResult;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.SimpleTriggerContext;

@DisplayName("작성 중 행사 30일 미수정 알림 작업")
class DraftEventIdleNoticeJobTest {

    private final AtomicInteger runs = new AtomicInteger();
    private final DraftEventIdleNoticeJob job = new DraftEventIdleNoticeJob(() -> {
        runs.incrementAndGet();
        return new NotifyIdleDraftEventsResult(List.of(7L));
    });

    @Test
    @DisplayName("매일 오전 10시(한국 시각 — 제안값)에 깨운다 — 9:59면 그날 10시, 10시가 지났으면 다음 날 10시")
    void runsEveryDayAtTenInKorea() {
        assertThat(nextRunAfter(LocalDateTime.of(2026, 10, 14, 9, 59)))
                .isEqualTo(KoreanTime.toInstant(LocalDateTime.of(2026, 10, 14, 10, 0)));
        assertThat(nextRunAfter(LocalDateTime.of(2026, 10, 14, 10, 0, 1)))
                .isEqualTo(KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0)));
        assertThat(nextRunAfter(LocalDateTime.of(2026, 12, 31, 23, 0)))
                .isEqualTo(KoreanTime.toInstant(LocalDateTime.of(2027, 1, 1, 10, 0)));
    }

    @Test
    @DisplayName("스케줄러에 작업 하나를 맡기고, 깨우면 유스케이스를 한 번 부른다")
    void registersAndRunsUseCase() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        job.configureTasks(registrar);
        registrar.getTriggerTaskList().getFirst().getRunnable().run();

        assertThat(registrar.getTriggerTaskList()).hasSize(1);
        assertThat(runs).hasValue(1);
    }

    private static Instant nextRunAfter(LocalDateTime koreanDateTime) {
        SimpleTriggerContext context = new SimpleTriggerContext(MutableClock.at(koreanDateTime));
        return DraftEventIdleNoticeJob.trigger().nextExecution(context);
    }
}
