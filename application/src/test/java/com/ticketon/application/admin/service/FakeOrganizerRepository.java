package com.ticketon.application.admin.service;

import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerPublicProfile;
import com.ticketon.domain.organizer.OrganizerStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 메모리 주최자 저장소 — 표처럼 저장된 값(이름·상태)만 갖고, 읽을 때마다 새 객체로 되살린다. 승인 저장은 저장된 상태가 아직 심사 중일 때만
 * 바꾼다(아니면 충돌). 새 주최자 저장은 심사 화면·승인·반려에서 쓰지 않는다.
 */
final class FakeOrganizerRepository implements OrganizerRepository {

    private final Map<Long, OrganizerName> names = new LinkedHashMap<>();
    private final Map<Long, OrganizerStatus> statuses = new LinkedHashMap<>();
    private long nextId = 1;

    /** 표에 주최자를 바로 넣는다 — 새 주최자 신청은 신청서 제출(#20)이 한다. */
    long store(String name, OrganizerStatus status) {
        long id = nextId++;
        names.put(id, OrganizerName.of(name));
        statuses.put(id, status);
        return id;
    }

    OrganizerStatus statusOf(long organizerId) {
        return statuses.get(organizerId);
    }

    @Override
    public Organizer add(Organizer newOrganizer) {
        throw new UnsupportedOperationException("심사 화면·승인·반려에서는 새 주최자를 만들지 않습니다");
    }

    @Override
    public Optional<Organizer> findById(long id) {
        if (!names.containsKey(id)) {
            return Optional.empty();
        }
        return Optional.of(Organizer.restore(id, names.get(id), statuses.get(id), OrganizerPublicProfile.empty()));
    }

    @Override
    public void saveApproval(Organizer approvedOrganizer) {
        long id = approvedOrganizer.id().orElseThrow();
        if (statuses.get(id) != OrganizerStatus.UNDER_REVIEW) {
            throw new ConflictException(OrganizerErrorCode.ORGANIZER_NOT_UNDER_REVIEW, "승인 경합");
        }
        statuses.put(id, approvedOrganizer.status());
    }
}
