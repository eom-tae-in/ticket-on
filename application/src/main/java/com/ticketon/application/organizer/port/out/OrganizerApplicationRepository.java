package com.ticketon.application.organizer.port.out;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.ApplicantReviewState;
import com.ticketon.domain.organizer.OrganizerApplication;
import java.util.Optional;

/**
 * 주최자 신청서 저장소 — 나가는 포트.
 * <p>
 * 신청서는 지우지 않는다 — 반려된 신청서도 사유와 함께 남고, 다시 신청하면 새 신청서가 된다(docs/01 "반려").
 * 심사 중인 신청은 한 사람에게 하나뿐이다. 새 신청을 낼 수 있는지는 저장소가 확인해 규칙({@link OrganizerApplication#requireCanSubmit})에
 * 넘기고, 확인과 저장 사이에 같은 사람의 다른 신청이 먼저 저장될 수 있으므로 저장할 때 다시 막는다({@link #add}).
 * <p>
 * 담당자 연락처 · 사업자 정보 · 정산 계좌는 심사와 판매자 정보에만 쓰는 민감한 값이다. 구현은 이 값을 로그에 남기지 않는다.
 */
public interface OrganizerApplicationRepository {

    /** 그 사람에게 지금 심사 중인 신청이 있는지. 반려·승인된 신청은 세지 않는다. */
    ApplicantReviewState reviewStateOf(long applicantStaffAccountId);

    /**
     * 막 낸 신청서를 저장하고, id가 붙은 신청서를 돌려준다. 홈페이지·SNS 링크는 적은 순서대로 남는다.
     *
     * @param newApplication {@link OrganizerApplication#submit}으로 만든, id가 없는 신청서
     * @throws ConflictException 확인한 뒤 같은 사람의 다른 신청이 먼저 심사 중으로 저장됐으면
     *                           ({@code APPLICATION_ALREADY_UNDER_REVIEW}) — 같은 사람이 거의 동시에 두 번 낸 경합
     */
    OrganizerApplication add(OrganizerApplication newApplication);

    Optional<OrganizerApplication> findById(long id);

    /**
     * 승인·반려한 신청서의 상태 · 처리 일시 · 반려 사유를 저장한다.
     * <p>
     * <b>표에서 아직 심사 중일 때만 바꾼다.</b> 두 관리자가 같은 신청을 거의 동시에 승인·반려하면 둘 다 심사 중으로 읽고 규칙을 통과할 수 있어,
     * 마지막 판정은 이 조건부 저장이 한다. 진 쪽은 충돌이 되고 그 트랜잭션은 되돌려진다 — 함께 바꾼 주최자·소속·조작 기록도 남지 않고 메일도
     * 나가지 않는다.
     *
     * @param reviewedApplication {@link OrganizerApplication#approve} 또는 {@link OrganizerApplication#reject}로 처리한, 저장된
     *                            신청서
     * @throws ConflictException        표의 신청서가 이미 심사 중이 아니면({@code APPLICATION_ALREADY_REVIEWED}) — 다른 관리자가 먼저 처리했다
     * @throws IllegalArgumentException 저장 전 신청서이거나 아직 심사 중인 신청서면
     */
    void saveReview(OrganizerApplication reviewedApplication);
}
