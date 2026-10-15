package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.out.OrganizerApplicationRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.ApplicantReviewState;
import com.ticketon.domain.organizer.OrganizerApplication;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.RejectionReason;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 메모리 주최자 신청서 저장소 — 저장소 약속을 표처럼 지킨다: 심사 중인 신청은 한 사람에게 하나(어기면 충돌), 저장 순서대로 1부터 id.
 */
final class FakeOrganizerApplicationRepository implements OrganizerApplicationRepository {

    private final Map<Long, OrganizerApplication> applications = new LinkedHashMap<>();
    private boolean anotherSubmissionWinsNextAdd;

    /** 다음 저장 직전에 같은 사람의 다른 신청이 먼저 저장된 것처럼 — 확인은 통과했지만 저장에서 진다. */
    void letAnotherSubmissionWinNextAdd() {
        anotherSubmissionWinsNextAdd = true;
    }

    /** 관리자가 반려한 것처럼 바꿔 둔다 — 반려 유스케이스는 관리자 심사(admin)의 일이고 그 테스트가 본다. */
    void reject(long applicationId, String reason, Instant reviewedAt) {
        OrganizerApplication application = findById(applicationId).orElseThrow();
        application.reject(RejectionReason.of(reason), reviewedAt);
        applications.put(applicationId, application);
    }

    List<OrganizerApplication> savedApplications() {
        return List.copyOf(applications.values());
    }

    @Override
    public ApplicantReviewState reviewStateOf(long applicantStaffAccountId) {
        return hasUnderReview(applicantStaffAccountId) ? ApplicantReviewState.APPLICATION_UNDER_REVIEW
                : ApplicantReviewState.NO_APPLICATION_UNDER_REVIEW;
    }

    @Override
    public OrganizerApplication add(OrganizerApplication newApplication) {
        if (newApplication.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 신청서입니다");
        }
        if (anotherSubmissionWinsNextAdd || hasUnderReview(newApplication.applicantStaffAccountId())) {
            anotherSubmissionWinsNextAdd = false;
            throw new ConflictException(OrganizerErrorCode.APPLICATION_ALREADY_UNDER_REVIEW, "동시 신청 경합");
        }
        long id = applications.size() + 1;
        OrganizerApplication saved = OrganizerApplication.restore(id, newApplication.organizerId(),
                newApplication.applicantStaffAccountId(), newApplication.form(), newApplication.submittedAt(),
                newApplication.status(), null, null);
        applications.put(id, saved);
        return saved;
    }

    @Override
    public Optional<OrganizerApplication> findById(long id) {
        return Optional.ofNullable(applications.get(id));
    }

    @Override
    public void saveReview(OrganizerApplication reviewedApplication) {
        throw new UnsupportedOperationException("주최자 신청 제출·주최 홈에서는 심사 결과를 저장하지 않습니다");
    }

    private boolean hasUnderReview(long applicantStaffAccountId) {
        return applications.values().stream().anyMatch(application ->
                application.applicantStaffAccountId() == applicantStaffAccountId
                        && application.status() == OrganizerApplicationStatus.UNDER_REVIEW);
    }
}
