package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerBusinessDetails;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerPublicProfile;
import com.ticketon.domain.organizer.OrganizerStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 메모리 주최자 저장소 — 저장 순서대로 1부터 id를 붙인다. */
final class FakeOrganizerRepository implements OrganizerRepository {

    private final List<Organizer> organizers = new ArrayList<>();
    private final Map<Long, OrganizerBusinessDetails> businessDetails = new HashMap<>();
    private final RowLockLog rowLockLog;
    private int publicProfileSaves;
    private int nameSaves;
    private boolean failingPublicProfileSaves;

    FakeOrganizerRepository() {
        this(new RowLockLog());
    }

    /** 다른 메모리 저장소와 같은 잠금 기록에 주최자 줄 잠금을 적는다 — 줄 사이의 잠금 순서를 보려고. */
    FakeOrganizerRepository(RowLockLog rowLockLog) {
        this.rowLockLog = rowLockLog;
    }

    @Override
    public Organizer add(Organizer newOrganizer) {
        if (newOrganizer.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 주최자입니다");
        }
        Organizer saved = Organizer.restore(organizers.size() + 1, newOrganizer.name(), newOrganizer.status(),
                newOrganizer.publicProfile());
        organizers.add(saved);
        return saved;
    }

    /** 승인 · 닫기 같은 기능을 거치지 않고 그 상태의 주최자를 바로 둔다. */
    Organizer store(String name, OrganizerStatus status) {
        Organizer saved = Organizer.restore(organizers.size() + 1, OrganizerName.of(name), status,
                OrganizerPublicProfile.empty());
        organizers.add(saved);
        return saved;
    }

    List<Organizer> savedOrganizers() {
        return List.copyOf(organizers);
    }

    @Override
    public Optional<Organizer> findById(long id) {
        return organizers.stream().filter(organizer -> organizer.id().orElseThrow() == id).findFirst();
    }

    /** 메모리 저장소에는 다른 트랜잭션이 없어 잠글 것이 없다 — 잠금 기록에 차례만 남기고 지금 저장된 값을 돌려준다. */
    @Override
    public Optional<Organizer> findByIdForUpdate(long id) {
        rowLockLog.record("주최자 " + id + " FOR UPDATE");
        return findById(id);
    }

    @Override
    public void saveApproval(Organizer approvedOrganizer, OrganizerBusinessDetails businessDetails) {
        throw new UnsupportedOperationException("주최자 신청 제출·주최 홈·운영진 권한 확인에서는 승인을 저장하지 않습니다");
    }

    @Override
    public Optional<OrganizerBusinessDetails> findBusinessDetails(long organizerId) {
        return Optional.ofNullable(businessDetails.get(organizerId));
    }

    @Override
    public void saveBusinessDetails(long organizerId, OrganizerBusinessDetails businessDetails) {
        throw new UnsupportedOperationException("주최 관리에서는 사업자 정보 · 정산 계좌를 바로 저장하지 않습니다 — 관리자가 승인할 때 저장합니다");
    }

    /** 승인을 거치지 않고 그 주최자의 지금 사업자 정보 · 정산 계좌를 둔다. */
    void storeBusinessDetails(long organizerId, OrganizerBusinessDetails businessDetails) {
        this.businessDetails.put(organizerId, businessDetails);
    }

    @Override
    public void savePublicProfile(Organizer organizer) {
        if (failingPublicProfileSaves) {
            throw new IllegalStateException("공개 정보 저장 실패를 흉내 냅니다");
        }
        long id = organizer.id().orElseThrow(() -> new IllegalArgumentException("저장 전 주최자입니다"));
        Organizer stored = findById(id).orElseThrow(() -> new IllegalArgumentException("없는 주최자입니다: " + id));
        organizers.set(organizers.indexOf(stored), Organizer.restore(id, organizer.name(), stored.status(),
                organizer.publicProfile()));
        publicProfileSaves++;
    }

    @Override
    public void saveName(Organizer renamedOrganizer) {
        long id = renamedOrganizer.id().orElseThrow(() -> new IllegalArgumentException("저장 전 주최자입니다"));
        Organizer stored = findById(id).orElseThrow(() -> new IllegalArgumentException("없는 주최자입니다: " + id));
        organizers.set(organizers.indexOf(stored), Organizer.restore(id, renamedOrganizer.name(), stored.status(),
                stored.publicProfile()));
        nameSaves++;
    }

    /** 주최자명을 저장한 횟수. */
    int nameSaves() {
        return nameSaves;
    }

    /** 이제부터 공개 정보 저장이 실패한다 — 저장 단계에서 트랜잭션이 되돌려지는 경우를 본다. */
    void failPublicProfileSaves() {
        failingPublicProfileSaves = true;
    }

    /** 공개 정보를 저장한 횟수. */
    int publicProfileSaves() {
        return publicProfileSaves;
    }
}
