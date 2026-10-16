package com.ticketon.application.admin.service;

import com.ticketon.application.organizer.port.out.OrganizerChangeRequestRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerChangeRequest;
import com.ticketon.domain.organizer.OrganizerChangeRequestKind;
import com.ticketon.domain.organizer.OrganizerChangeRequestStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.RejectionReason;
import com.ticketon.domain.organizer.RequestedOrganizerChange;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 메모리 변경 요청 저장소 — 표처럼 저장된 값만 갖고, 읽을 때마다 새 객체로 되살린다. 처리 결과 저장은 저장된 요청이 아직 확인 중일 때만
 * 바꾼다(아니면 충돌) — 두 관리자의 경합을 흉내 낸다. 새 요청 저장은 관리자 확인에서 쓰지 않는다.
 */
final class FakeOrganizerChangeRequestRepository implements OrganizerChangeRequestRepository {

    private final Map<Long, OrganizerChangeRequest> requests = new LinkedHashMap<>();
    private long nextId = 1;
    private int reviewSaves;
    private Runnable beforeNextSave;

    /** 소유자가 낸 확인 중 요청을 표에 바로 넣는다. */
    long store(long organizerId, long requesterStaffAccountId, RequestedOrganizerChange change, Instant requestedAt) {
        long id = nextId++;
        requests.put(id, OrganizerChangeRequest.restore(id, organizerId, requesterStaffAccountId, change, requestedAt,
                OrganizerChangeRequestStatus.UNDER_REVIEW, null, null, null));
        return id;
    }

    /** 다른 관리자가 먼저 반려해 저장한 것을 흉내 낸다 — 이 저장소를 거치지 않고 표만 바꾼다. */
    void rejectedByAnotherAdmin(long id, long adminAccountId, Instant rejectedAt) {
        OrganizerChangeRequest stored = requests.get(id);
        requests.put(id, OrganizerChangeRequest.restore(id, stored.organizerId(), stored.requesterStaffAccountId(),
                stored.requestedChange(), stored.requestedAt(), OrganizerChangeRequestStatus.REJECTED, rejectedAt,
                adminAccountId, RejectionReason.of("다른 관리자가 먼저 반려함")));
    }

    /** 다음 처리 결과 저장 바로 전에 다른 관리자가 그 요청을 먼저 반려해 커밋한 것을 흉내 낸다. */
    void letAnotherAdminRejectBeforeNextSave(long id, long adminAccountId, Instant rejectedAt) {
        beforeNextSave = () -> rejectedByAnotherAdmin(id, adminAccountId, rejectedAt);
    }

    /** 처리 결과를 저장한 횟수. */
    int reviewSaves() {
        return reviewSaves;
    }

    @Override
    public boolean hasUnderReview(long organizerId, OrganizerChangeRequestKind kind) {
        return requests.values().stream().anyMatch(request -> request.organizerId() == organizerId
                && request.kind() == kind && request.status() == OrganizerChangeRequestStatus.UNDER_REVIEW);
    }

    @Override
    public OrganizerChangeRequest add(OrganizerChangeRequest newRequest) {
        throw new UnsupportedOperationException("관리자 확인에서는 새 변경 요청을 만들지 않습니다");
    }

    @Override
    public Optional<OrganizerChangeRequest> findById(long id) {
        OrganizerChangeRequest stored = requests.get(id);
        if (stored == null) {
            return Optional.empty();
        }
        return Optional.of(OrganizerChangeRequest.restore(id, stored.organizerId(), stored.requesterStaffAccountId(),
                stored.requestedChange(), stored.requestedAt(), stored.status(), stored.reviewedAt().orElse(null),
                stored.reviewerAdminAccountId().isPresent() ? stored.reviewerAdminAccountId().getAsLong() : null,
                stored.rejectionReason().orElse(null)));
    }

    @Override
    public List<OrganizerChangeRequest> findUnderReviewOf(long organizerId) {
        return requests.keySet().stream()
                .map(id -> findById(id).orElseThrow())
                .filter(request -> request.organizerId() == organizerId)
                .filter(request -> request.status() == OrganizerChangeRequestStatus.UNDER_REVIEW)
                .toList();
    }

    @Override
    public void saveReview(OrganizerChangeRequest reviewedRequest) {
        Runnable concurrentReview = beforeNextSave;
        beforeNextSave = null;
        if (concurrentReview != null) {
            concurrentReview.run();
        }
        long id = reviewedRequest.id().orElseThrow();
        if (requests.get(id).status() != OrganizerChangeRequestStatus.UNDER_REVIEW) {
            throw new ConflictException(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED, "확인 경합");
        }
        requests.put(id, reviewedRequest);
        reviewSaves++;
    }
}
