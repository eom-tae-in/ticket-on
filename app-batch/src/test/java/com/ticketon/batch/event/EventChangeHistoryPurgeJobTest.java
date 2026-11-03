package com.ticketon.batch.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.application.event.port.in.PurgeExpiredEventChangeHistoryResult;
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

@DisplayName("보관 기간이 지난 행사 변경 이력 삭제 작업")
class EventChangeHistoryPurgeJobTest {

    private final AtomicInteger runs = new AtomicInteger();
    private final EventChangeHistoryPurgeJob job = new EventChangeHistoryPurgeJob(() -> {
        runs.incrementAndGet();
        return new PurgeExpiredEventChangeHistoryResult(List.of(7L));
    });

    @Test
    @DisplayName("매일 오전 4시(한국 시각 — 제안값)에 깨운다 — 3:59면 그날 4시, 4시가 지났으면 다음 날 4시")
    void runsEveryDayAtFourInKorea() {
        assertThat(nextRunAfter(LocalDateTime.of(2027, 4, 15, 3, 59)))
                .isEqualTo(KoreanTime.toInstant(LocalDateTime.of(2027, 4, 15, 4, 0)));
        assertThat(nextRunAfter(LocalDateTime.of(2027, 4, 15, 4, 0, 1)))
                .isEqualTo(KoreanTime.toInstant(LocalDateTime.of(2027, 4, 16, 4, 0)));
        assertThat(nextRunAfter(LocalDateTime.of(2027, 12, 31, 23, 0)))
                .isEqualTo(KoreanTime.toInstant(LocalDateTime.of(2028, 1, 1, 4, 0)));
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
        return EventChangeHistoryPurgeJob.trigger().nextExecution(context);
    }
}
