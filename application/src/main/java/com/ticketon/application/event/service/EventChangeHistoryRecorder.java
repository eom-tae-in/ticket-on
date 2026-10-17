package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.out.EventChangeHistoryRepository;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.event.EventChangeAuthor;
import com.ticketon.domain.event.EventChangeHistory;
import com.ticketon.domain.event.EventChangeHistoryVersion;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 변경 이력 쌓기 — 행사를 고치는 흐름(위저드 1단계 저장, 포스터 올리기, 회차 출연)과 판매 시작의 첫 버전이 함께 쓴다. 무엇을 쌓는지는
 * {@link EventChangeHistory}가 정하고, 여기서는 바꾼 운영진(계정 + 그때의 닉네임)을 붙여 저장소에 쌓는다.
 * <p>
 * <b>운영자가 제목을 바꾸면 소유자에게 알린다</b>({@link EventChangeHistory#notifiesOwner}, {@link EventTitleChangedByOperatorNotice}) — 이력을
 * 쌓는 같은 자리에서 보내, 제목 버전이 쌓인 변경에만 간다. 소유자가 직접 바꾸거나 첫 버전이면 보내지 않는다. 알림 발송은 같은 트랜잭션에서 발송
 * 이력을 남기고, 실패하면 고친 것 · 이력과 함께 되돌려진다.
 * <p>
 * <b>부르는 흐름의 트랜잭션 안에서 돈다</b> — 행사 줄을 FOR UPDATE로 잡은 뒤, 행사를 저장한 같은 트랜잭션에서 부른다. 고친 것이 되돌려지면 이력도
 * 함께 되돌려지고, 같은 행사의 버전은 행사 줄에서 차례로 쌓인다. 바꾼 운영진의 계정 줄과 주최자 줄은 그 흐름이 이미 외래 키 확인 잠금으로 잡아
 * 두었다({@link EventEditingLocks}) — 이력 줄이 그 계정을 가리켜도 새로 기다리지 않고, 주최자명 · 소유자 연락처는 잠그지 않고 읽는다.
 */
@Component
class EventChangeHistoryRecorder {

    private final EventChangeHistoryRepository historyRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final SendNotificationUseCase sendNotificationUseCase;

    EventChangeHistoryRecorder(EventChangeHistoryRepository historyRepository, StaffAccountRepository staffAccountRepository,
            OrganizerRepository organizerRepository, SendNotificationUseCase sendNotificationUseCase) {
        this.historyRepository = historyRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.organizerRepository = organizerRepository;
        this.sendNotificationUseCase = sendNotificationUseCase;
    }

    /**
     * 그 버전들을 바꾼 순간에 쌓고, 운영자가 바꾼 제목이 있으면 소유자에게 알린다. 쌓을 버전이 없으면(작성 중이거나 대상 칸이 바뀌지 않음) 아무것도
     * 하지 않는다.
     *
     * @param permission 잠근 뒤 다시 확인한 권한 — 바꾼 운영진과 그때의 역할
     * @param changedAt  바꾼 순간 — 그 흐름의 지금 시각
     */
    void record(StaffPermissionResult permission, long eventId, List<EventChangeHistoryVersion> versions, Instant changedAt) {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(versions, "versions");
        Objects.requireNonNull(changedAt, "changedAt");
        if (versions.isEmpty()) {
            return;
        }
        EventChangeAuthor author = authorOf(permission);
        historyRepository.add(eventId, versions, author, changedAt);
        versions.stream()
                .filter(version -> EventChangeHistory.notifiesOwner(version, permission.role()))
                .forEach(titleChange -> sendNotificationUseCase.send(EventTitleChangedByOperatorNotice.commandFor(
                        permission.organizerId(), organizerNameOf(permission), titleChange, author, changedAt)));
    }

    /** 바꾼 운영진 — 계정 id와 지금(바꾸는 순간) 닉네임. */
    private EventChangeAuthor authorOf(StaffPermissionResult permission) {
        long staffAccountId = permission.staffAccountId();
        StaffAccount account = staffAccountRepository.findById(staffAccountId)
                .orElseThrow(() -> new IllegalStateException("권한을 확인한 운영진 계정이 없습니다: " + staffAccountId));
        return new EventChangeAuthor(staffAccountId, account.nickname());
    }

    private String organizerNameOf(StaffPermissionResult permission) {
        long organizerId = permission.organizerId();
        return organizerRepository.findById(organizerId)
                .orElseThrow(() -> new IllegalStateException("권한을 확인한 주최자가 없습니다: " + organizerId))
                .name()
                .value();
    }
}
