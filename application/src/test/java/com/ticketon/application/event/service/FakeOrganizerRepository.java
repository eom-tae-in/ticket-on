package com.ticketon.application.event.service;

import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerBusinessDetails;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerPublicProfile;
import com.ticketon.domain.organizer.OrganizerStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 메모리 주최자 저장소 — 행사 유스케이스가 쓰는 찾기(권한 확인의 id 찾기, 외래 키 확인 잠금)만 한다. 잠근 차례를 잠금 기록에 남긴다. 주최자를
 * 고쳐 저장하는 일은 주최자 유스케이스의 테스트가 본다.
 */
final class FakeOrganizerRepository implements OrganizerRepository {

    private final Map<Long, Organizer> organizers = new LinkedHashMap<>();
    private final RowLockLog rowLockLog;
    private long nextId = 1;

    FakeOrganizerRepository(RowLockLog rowLockLog) {
        this.rowLockLog = rowLockLog;
    }

    /** 표에 주최자를 바로 넣는다. */
    long store(String name, OrganizerStatus status) {
        long id = nextId++;
        organizers.put(id, Organizer.restore(id, OrganizerName.of(name), status, OrganizerPublicProfile.empty()));
        return id;
    }

    @Override
    public Optional<Organizer> findById(long id) {
        return Optional.ofNullable(organizers.get(id));
    }

    /** 메모리 저장소에는 다른 트랜잭션이 없어 잠글 것이 없다 — 잠금 기록에 차례만 남기고 지금 저장된 값을 돌려준다. */
    @Override
    public Optional<Organizer> findByIdForUpdate(long id) {
        rowLockLog.record("주최자 " + id + " FOR UPDATE");
        return findById(id);
    }

    @Override
    public Optional<Organizer> findByIdForKeyShare(long id) {
        rowLockLog.record("주최자 " + id + " FOR KEY SHARE");
        return findById(id);
    }

    @Override
    public Organizer add(Organizer newOrganizer) {
        throw new UnsupportedOperationException("행사 유스케이스는 주최자를 만들지 않습니다");
    }

    @Override
    public void saveApproval(Organizer approvedOrganizer, OrganizerBusinessDetails businessDetails) {
        throw new UnsupportedOperationException("행사 유스케이스는 주최자 승인을 저장하지 않습니다");
    }

    @Override
    public Optional<OrganizerBusinessDetails> findBusinessDetails(long organizerId) {
        throw new UnsupportedOperationException("행사 유스케이스는 사업자 정보를 읽지 않습니다");
    }

    @Override
    public void saveBusinessDetails(long organizerId, OrganizerBusinessDetails businessDetails) {
        throw new UnsupportedOperationException("행사 유스케이스는 사업자 정보를 저장하지 않습니다");
    }

    @Override
    public void savePublicProfile(Organizer organizer) {
        throw new UnsupportedOperationException("행사 유스케이스는 공개 정보를 저장하지 않습니다");
    }

    @Override
    public void saveName(Organizer renamedOrganizer) {
        throw new UnsupportedOperationException("행사 유스케이스는 주최자명을 저장하지 않습니다");
    }
}
