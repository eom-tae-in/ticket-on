package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.out.OrganizerChangeRequestRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerChangeRequest;
import com.ticketon.domain.organizer.OrganizerChangeRequestKind;
import com.ticketon.domain.organizer.OrganizerChangeRequestStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 메모리 변경 요청 저장소 — 저장 순서대로 1부터 id를 붙이고, 표의 유일 색인처럼 한 주최자에 같은 종류의 확인 중인 요청은 하나만 받는다.
 * 승인 · 반려 저장은 주최 관리에서 쓰지 않는다.
 */
final class FakeOrganizerChangeRequestRepository implements OrganizerChangeRequestRepository {

    private final List<OrganizerChangeRequest> requests = new ArrayList<>();
    private Runnable beforeNextAdd;

    @Override
    public boolean hasUnderReview(long organizerId, OrganizerChangeRequestKind kind) {
        return requests.stream().anyMatch(request -> request.organizerId() == organizerId && request.kind() == kind
                && request.status() == OrganizerChangeRequestStatus.UNDER_REVIEW);
    }

    @Override
    public OrganizerChangeRequest add(OrganizerChangeRequest newRequest) {
        Runnable concurrentAdd = beforeNextAdd;
        beforeNextAdd = null;
        if (concurrentAdd != null) {
            concurrentAdd.run();
        }
        if (hasUnderReview(newRequest.organizerId(), newRequest.kind())) {
            throw new ConflictException(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW, "동시 요청 경합");
        }
        OrganizerChangeRequest saved = OrganizerChangeRequest.restore(requests.size() + 1, newRequest.organizerId(),
                newRequest.requesterStaffAccountId(), newRequest.requestedChange(), newRequest.requestedAt(),
                newRequest.status(), null, null, null);
        requests.add(saved);
        return saved;
    }

    @Override
    public Optional<OrganizerChangeRequest> findById(long id) {
        return requests.stream().filter(request -> request.id().orElseThrow() == id).findFirst();
    }

    @Override
    public List<OrganizerChangeRequest> findUnderReviewOf(long organizerId) {
        return requests.stream()
                .filter(request -> request.organizerId() == organizerId)
                .filter(request -> request.status() == OrganizerChangeRequestStatus.UNDER_REVIEW)
                .sorted(Comparator.comparing(OrganizerChangeRequest::requestedAt))
                .toList();
    }

    @Override
    public void saveReview(OrganizerChangeRequest reviewedRequest) {
        throw new UnsupportedOperationException("주최 관리에서는 변경 요청을 승인 · 반려하지 않습니다");
    }

    /** 저장된 요청 — 저장한 차례대로. */
    List<OrganizerChangeRequest> savedRequests() {
        return List.copyOf(requests);
    }

    /** 다음 저장 바로 전에 다른 트랜잭션이 같은 종류의 요청을 먼저 저장한 것을 흉내 낸다. */
    void addBeforeNextAdd(OrganizerChangeRequest concurrentRequest) {
        beforeNextAdd = () -> add(concurrentRequest);
    }
}
