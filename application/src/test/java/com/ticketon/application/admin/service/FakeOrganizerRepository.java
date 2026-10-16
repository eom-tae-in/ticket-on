package com.ticketon.application.admin.service;

import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerBusinessDetails;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerPublicProfile;
import com.ticketon.domain.organizer.OrganizerStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 메모리 주최자 저장소 — 표처럼 저장된 값(이름·상태·사업자 정보 · 정산 계좌)만 갖고, 읽을 때마다 새 객체로 되살린다. 승인 저장은 저장된
 * 상태가 아직 심사 중일 때만 바꾼다(아니면 충돌). 새 주최자 저장은 심사 화면·승인·반려에서 쓰지 않는다.
 */
final class FakeOrganizerRepository implements OrganizerRepository {

    private final Map<Long, OrganizerName> names = new LinkedHashMap<>();
    private final Map<Long, OrganizerStatus> statuses = new LinkedHashMap<>();
    private final Map<Long, OrganizerBusinessDetails> businessDetails = new LinkedHashMap<>();
    private final RowLockLog rowLockLog;
    private long nextId = 1;
    private int businessDetailsSaves;

    FakeOrganizerRepository() {
        this(new RowLockLog());
    }

    /** 다른 메모리 저장소와 같은 잠금 기록에 주최자 줄 잠금을 적는다 — 줄 사이의 잠금 순서를 보려고. */
    FakeOrganizerRepository(RowLockLog rowLockLog) {
        this.rowLockLog = rowLockLog;
    }

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

    /** 메모리 저장소에는 다른 트랜잭션이 없어 잠글 것이 없다 — 잠금 기록에 차례만 남기고 지금 저장된 값을 돌려준다. */
    @Override
    public Optional<Organizer> findByIdForUpdate(long id) {
        rowLockLog.record("주최자 " + id + " FOR UPDATE");
        return findById(id);
    }

    @Override
    public void savePublicProfile(Organizer organizer) {
        throw new UnsupportedOperationException("심사 화면·승인·반려에서는 공개 정보를 저장하지 않습니다");
    }

    @Override
    public void saveName(Organizer renamedOrganizer) {
        throw new UnsupportedOperationException("심사 화면·승인·반려·사후 확인에서는 주최자명을 저장하지 않습니다");
    }

    @Override
    public void saveApproval(Organizer approvedOrganizer, OrganizerBusinessDetails businessDetails) {
        long id = approvedOrganizer.id().orElseThrow();
        if (statuses.get(id) != OrganizerStatus.UNDER_REVIEW) {
            throw new ConflictException(OrganizerErrorCode.ORGANIZER_NOT_UNDER_REVIEW, "승인 경합");
        }
        statuses.put(id, approvedOrganizer.status());
        this.businessDetails.put(id, businessDetails);
    }

    @Override
    public Optional<OrganizerBusinessDetails> findBusinessDetails(long organizerId) {
        return Optional.ofNullable(this.businessDetails.get(organizerId));
    }

    /** 표처럼 주최자 줄을 잠근 뒤 읽은 값에 적용해 부른다고 보고 그대로 저장한다. 저장한 차례를 남긴다. */
    @Override
    public void saveBusinessDetails(long organizerId, OrganizerBusinessDetails businessDetails) {
        if (!names.containsKey(organizerId)) {
            throw new IllegalArgumentException("없는 주최자입니다: " + organizerId);
        }
        this.businessDetails.put(organizerId, businessDetails);
        businessDetailsSaves++;
    }

    /** 승인 · 닫기 같은 기능을 거치지 않고 그 주최자의 지금 사업자 정보 · 정산 계좌를 둔다. */
    void storeBusinessDetails(long organizerId, OrganizerBusinessDetails businessDetails) {
        this.businessDetails.put(organizerId, businessDetails);
    }

    /** 변경을 승인해 사업자 정보 · 정산 계좌를 저장한 횟수. */
    int businessDetailsSaves() {
        return businessDetailsSaves;
    }
}
