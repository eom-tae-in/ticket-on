package com.ticketon.batch.event;

import com.ticketon.application.event.port.in.NotifyIdleDraftEventsResult;
import com.ticketon.application.event.port.in.NotifyIdleDraftEventsUseCase;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.policy.ProposedPolicy;
import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * 작성 중 행사 30일 미수정 알림 작업 — 매일 {@link ProposedPolicy#DRAFT_EVENT_IDLE_NOTICE_RUN_TIME}(한국 시각)에
 * {@link NotifyIdleDraftEventsUseCase}를 부른다(docs/04 "작성 중 행사 관리", docs/12 "주최자 대상 — 작성 중 행사 30일 미수정").
 * <p>
 * 들어오는 어댑터라 유스케이스를 부르기만 한다 — 누구에게 언제 보낼지, 같은 구간에 다시 보내지 않는 일은 유스케이스가 한다. 그래서 작업이 하루
 * 빠져도 다음 작업이 밀린 행사에 알리고, 다시 돌거나 두 벌이 돌아도 같은 구간에는 한 번만 간다. 30일이 지났는지는 유스케이스가 서비스의 시계로
 * 판정하고, 이 작업의 시각은 언제 깨울지만 정한다.
 * <p>
 * 유스케이스가 실패한 행사를 담아 던지면 그대로 둔다 — 스케줄러가 오류 기록을 남기고 다음 날 다시 돈다.
 */
@Component
public class DraftEventIdleNoticeJob implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(DraftEventIdleNoticeJob.class);

    private final NotifyIdleDraftEventsUseCase notifyIdleDraftEventsUseCase;

    public DraftEventIdleNoticeJob(NotifyIdleDraftEventsUseCase notifyIdleDraftEventsUseCase) {
        this.notifyIdleDraftEventsUseCase = notifyIdleDraftEventsUseCase;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(this::run, trigger());
    }

    /** 매일 그 시각(한국 시각)에 깨우는 시각표. */
    static CronTrigger trigger() {
        LocalTime runTime = ProposedPolicy.DRAFT_EVENT_IDLE_NOTICE_RUN_TIME;
        String everyDay = "%d %d %d * * *".formatted(runTime.getSecond(), runTime.getMinute(), runTime.getHour());
        return new CronTrigger(everyDay, KoreanTime.ZONE);
    }

    /** 작성 중 행사 30일 미수정 알림을 한 번 돌린다. */
    public void run() {
        NotifyIdleDraftEventsResult result = notifyIdleDraftEventsUseCase.notifyIdleDraftEvents();
        log.info("작성 중 행사 30일 미수정 알림: 행사 {}건 {}", result.notifiedEventIds().size(), result.notifiedEventIds());
    }
}
