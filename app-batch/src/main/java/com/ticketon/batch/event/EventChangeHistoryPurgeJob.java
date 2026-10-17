package com.ticketon.batch.event;

import com.ticketon.application.event.port.in.PurgeExpiredEventChangeHistoryResult;
import com.ticketon.application.event.port.in.PurgeExpiredEventChangeHistoryUseCase;
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
 * 보관 기간이 지난 행사 변경 이력 삭제 작업 — 매일 {@link ProposedPolicy#EVENT_CHANGE_HISTORY_PURGE_RUN_TIME}(한국 시각)에
 * {@link PurgeExpiredEventChangeHistoryUseCase}를 부른다(docs/03 "보관은 행사가 끝난 날부터 6개월", docs/04 "행사가 끝난 뒤 일어나는 일 — 6개월").
 * <p>
 * 들어오는 어댑터라 유스케이스를 부르기만 한다 — 어느 행사의 보관 기간이 지났는지(행사가 끝난 날 + 6개월, 한국 날짜), 무엇을 지우는지는 유스케이스가
 * 서비스의 시계로 정한다. 그래서 작업이 하루 빠져도 다음 작업이 밀린 행사를 지우고, 다시 돌아도 이미 지운 행사는 대상이 아니다. 이 작업의 시각은
 * 언제 깨울지만 정한다.
 * <p>
 * 유스케이스가 실패한 행사를 담아 던지면 그대로 둔다 — 스케줄러가 오류 기록을 남기고 다음 날 다시 돈다.
 */
@Component
public class EventChangeHistoryPurgeJob implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(EventChangeHistoryPurgeJob.class);

    private final PurgeExpiredEventChangeHistoryUseCase purgeExpiredEventChangeHistoryUseCase;

    public EventChangeHistoryPurgeJob(PurgeExpiredEventChangeHistoryUseCase purgeExpiredEventChangeHistoryUseCase) {
        this.purgeExpiredEventChangeHistoryUseCase = purgeExpiredEventChangeHistoryUseCase;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(this::run, trigger());
    }

    /** 매일 그 시각(한국 시각)에 깨우는 시각표. */
    static CronTrigger trigger() {
        LocalTime runTime = ProposedPolicy.EVENT_CHANGE_HISTORY_PURGE_RUN_TIME;
        String everyDay = "%d %d %d * * *".formatted(runTime.getSecond(), runTime.getMinute(), runTime.getHour());
        return new CronTrigger(everyDay, KoreanTime.ZONE);
    }

    /** 보관 기간이 지난 변경 이력 삭제를 한 번 돌린다. */
    public void run() {
        PurgeExpiredEventChangeHistoryResult result = purgeExpiredEventChangeHistoryUseCase.purgeExpiredChangeHistory();
        log.info("보관 기간이 지난 행사 변경 이력 삭제: 행사 {}건 {}", result.purgedEventIds().size(), result.purgedEventIds());
    }
}
