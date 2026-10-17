package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.RecordChangeHistoryFirstVersionCommand;
import com.ticketon.application.event.port.in.RecordChangeHistoryFirstVersionUseCase;
import com.ticketon.application.event.port.out.EventChangeHistoryRepository;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.event.port.out.EventScheduleRepository;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventChangeHistory;
import com.ticketon.domain.event.EventChangeHistoryVersion;
import com.ticketon.domain.event.EventSchedule;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 변경 이력의 첫 버전 쌓기 — 판매 시작 흐름의 트랜잭션에 참여한다({@link Propagation#MANDATORY}, 운영진 조작 기록과 같다). 그 흐름이 저장한 행사와
 * 일정을 같은 트랜잭션에서 다시 읽어, 판매 대기로 넘어간 순간의 내용을 첫 버전으로 쌓는다({@link EventChangeHistory#firstVersions}).
 * <p>
 * 잠금: 부르는 흐름이 이미 행사 줄을 FOR UPDATE로 쥐고 있다 — 여기서는 다시 잠그지 않는다. 첫 버전은 행사마다 한 번이다 — 이미 이력이 있으면
 * 부르는 쪽의 잘못으로 막는다.
 */
@Service
public class RecordChangeHistoryFirstVersionService implements RecordChangeHistoryFirstVersionUseCase {

    private final EventRepository eventRepository;
    private final EventScheduleRepository scheduleRepository;
    private final EventChangeHistoryRepository historyRepository;
    private final EventChangeHistoryRecorder recorder;

    public RecordChangeHistoryFirstVersionService(EventRepository eventRepository, EventScheduleRepository scheduleRepository,
            EventChangeHistoryRepository historyRepository, EventChangeHistoryRecorder recorder) {
        this.eventRepository = eventRepository;
        this.scheduleRepository = scheduleRepository;
        this.historyRepository = historyRepository;
        this.recorder = recorder;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(RecordChangeHistoryFirstVersionCommand command) {
        long eventId = command.eventId();
        Event event = eventRepository.findById(eventId)
                .filter(found -> command.permission().covers(found.organizerId()))
                .orElseThrow(() -> new IllegalArgumentException("권한을 확인한 주최자의 행사가 아닙니다: 행사 " + eventId
                        + ", 주최자 " + command.permission().organizerId()));
        EventSchedule schedule = scheduleRepository.findByEventId(eventId)
                .orElseThrow(() -> new IllegalStateException("일정이 없는 행사는 판매를 시작할 수 없습니다: 행사 " + eventId));
        List<EventChangeHistoryVersion> firstVersions = EventChangeHistory.firstVersions(event, schedule);
        if (historyRepository.existsByEventId(eventId)) {
            throw new IllegalStateException("이미 변경 이력이 있는 행사입니다 — 첫 버전은 판매를 시작할 때 한 번 쌓습니다: 행사 " + eventId);
        }
        recorder.record(command.permission(), eventId, firstVersions, command.publishedAt());
    }
}
