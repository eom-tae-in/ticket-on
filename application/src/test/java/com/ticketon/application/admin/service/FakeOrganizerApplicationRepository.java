package com.ticketon.application.admin.service;

import com.ticketon.application.organizer.port.out.OrganizerApplicationRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.ApplicantReviewState;
import com.ticketon.domain.organizer.OrganizerApplication;
import com.ticketon.domain.organizer.OrganizerApplicationForm;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.RejectionReason;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 메모리 주최자 신청서 저장소 — 표처럼 저장된 신청서를 읽을 때마다 새 객체로 되살린다. 심사 결과 저장은 저장된 신청서가 아직 심사 중일 때만
 * 바꾼다(아니면 충돌). 새 신청은 신청서 제출(#20)이 하므로 여기서는 받지 않는다.
 */
final class FakeOrganizerApplicationRepository implements OrganizerApplicationRepository {

    private final Map<Long, OrganizerApplication> applications = new LinkedHashMap<>();
    private boolean anotherReviewWinsNextSave;
    private long nextId = 1;

    /** 심사 중인 신청서를 표에 바로 넣는다. */
    long store(long organizerId, long applicantStaffAccountId, OrganizerApplicationForm form, Instant submittedAt) {
        long id = nextId++;
        applications.put(id, OrganizerApplication.restore(id, organizerId, applicantStaffAccountId, form, submittedAt,
                OrganizerApplicationStatus.UNDER_REVIEW, null, null));
        return id;
    }

    /** 승인된 신청서를 표에 바로 넣는다 — 소유자 이전 신청 심사가 주최자 신청 때 받은 연락처를 볼 때 쓴다. */
    long storeApproved(long organizerId, long applicantStaffAccountId, OrganizerApplicationForm form, Instant submittedAt,
            Instant reviewedAt) {
        long id = nextId++;
        applications.put(id, OrganizerApplication.restore(id, organizerId, applicantStaffAccountId, form, submittedAt,
                OrganizerApplicationStatus.APPROVED, reviewedAt, null));
        return id;
    }

    /** 다른 관리자가 이미 반려한 것처럼 표를 바꿔 둔다. */
    void rejectInTable(long applicationId, String reason, Instant reviewedAt) {
        OrganizerApplication application = findById(applicationId).orElseThrow();
        application.reject(RejectionReason.of(reason), reviewedAt);
        applications.put(applicationId, application);
    }

    /** 다음 심사 결과 저장 직전에 다른 관리자가 먼저 처리한 것처럼 — 읽고 규칙은 통과했지만 저장에서 진다. */
    void letAnotherReviewWinNextSave() {
        anotherReviewWinsNextSave = true;
    }

    @Override
    public ApplicantReviewState reviewStateOf(long applicantStaffAccountId) {
        throw new UnsupportedOperationException("심사 화면·승인·반려에서는 새 신청을 받지 않습니다");
    }

    @Override
    public OrganizerApplication add(OrganizerApplication newApplication) {
        throw new UnsupportedOperationException("심사 화면·승인·반려에서는 새 신청을 받지 않습니다");
    }

    @Override
    public Optional<OrganizerApplication> findById(long id) {
        return Optional.ofNullable(applications.get(id)).map(FakeOrganizerApplicationRepository::copyOf);
    }

    /** 처리 일시가 늦은 것, 같으면 id가 큰 것 — 표의 정렬과 같다. */
    @Override
    public Optional<OrganizerApplication> findLatestApprovedOf(long organizerId) {
        return applications.values().stream()
                .filter(application -> application.organizerId() == organizerId
                        && application.status() == OrganizerApplicationStatus.APPROVED)
                .max(Comparator.comparing((OrganizerApplication application) -> application.reviewedAt().orElseThrow())
                        .thenComparingLong(application -> application.id().orElseThrow()))
                .map(FakeOrganizerApplicationRepository::copyOf);
    }

    @Override
    public void saveReview(OrganizerApplication reviewedApplication) {
        long id = reviewedApplication.id().orElseThrow();
        if (anotherReviewWinsNextSave || applications.get(id).status() != OrganizerApplicationStatus.UNDER_REVIEW) {
            anotherReviewWinsNextSave = false;
            throw new ConflictException(OrganizerErrorCode.APPLICATION_ALREADY_REVIEWED, "심사 경합");
        }
        applications.put(id, copyOf(reviewedApplication));
    }

    private static OrganizerApplication copyOf(OrganizerApplication application) {
        return OrganizerApplication.restore(application.id().orElseThrow(), application.organizerId(),
                application.applicantStaffAccountId(), application.form(), application.submittedAt(), application.status(),
                application.reviewedAt().orElse(null), application.rejectionReason().orElse(null));
    }
}
