package com.ticketon.application.organizer.port.out;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerChangeRequest;
import com.ticketon.domain.organizer.OrganizerChangeRequestKind;
import java.util.List;
import java.util.Optional;

/**
 * 정산 계좌 · 사업자 정보 변경 요청 저장소 — 나가는 포트. 요청은 지우지 않는다 — 반려된 요청도 사유와 함께 남는다.
 * <p>
 * 한 주최자에 같은 종류의 확인 중인 요청은 하나뿐이다. 새 요청을 낼 수 있는지는 저장소가 확인해 규칙({@link OrganizerChangeRequest#request})에
 * 넘기고, 확인과 저장 사이에 다른 요청이 먼저 저장될 수 있으므로 저장할 때 다시 막는다({@link #add}). 확인 중인 요청은 관리자 처리 목록에
 * '정산 계좌 · 사업자 정보 변경 확인'으로 쌓인다(처리 목록 조회가 이 표를 읽는다).
 */
public interface OrganizerChangeRequestRepository {

    /** 그 주최자에 그 종류의 요청이 지금 확인 중인지. 승인·반려된 요청은 세지 않는다. */
    boolean hasUnderReview(long organizerId, OrganizerChangeRequestKind kind);

    /**
     * 막 낸 요청을 저장하고, id가 붙은 요청을 돌려준다.
     *
     * @param newRequest {@link OrganizerChangeRequest#request}로 만든, id가 없는 요청
     * @throws ConflictException 확인한 뒤 같은 주최자 · 같은 종류의 다른 요청이 먼저 확인 중으로 저장됐으면
     *                           ({@code ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW}) — 거의 동시에 두 번 낸 경합
     */
    OrganizerChangeRequest add(OrganizerChangeRequest newRequest);

    Optional<OrganizerChangeRequest> findById(long id);

    /** 그 주최자의 지금 확인 중인 요청 — 종류마다 많아야 하나, 요청한 순서. 없으면 빈 목록. */
    List<OrganizerChangeRequest> findUnderReviewOf(long organizerId);

    /**
     * 승인·반려한 요청의 상태 · 처리 일시 · 처리한 관리자 · 반려 사유를 저장한다.
     * <p>
     * <b>표에서 아직 확인 중일 때만 바꾼다.</b> 두 관리자가 같은 요청을 거의 동시에 승인·반려하면 둘 다 확인 중으로 읽고 규칙을 통과할 수 있어,
     * 마지막 판정은 이 조건부 저장이 한다. 진 쪽은 충돌이 되고 그 트랜잭션은 되돌려진다 — 함께 바꾼 주최자 값 · 조작 기록도 남지 않고 메일도
     * 나가지 않는다.
     *
     * @param reviewedRequest {@link OrganizerChangeRequest#approve} 또는 {@link OrganizerChangeRequest#reject}로 처리한, 저장된 요청
     * @throws ConflictException        표의 요청이 이미 확인 중이 아니면({@code ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED}) — 다른 관리자가
     *                                  먼저 처리했다
     * @throws IllegalArgumentException 저장 전 요청이거나 아직 확인 중인 요청이면
     */
    void saveReview(OrganizerChangeRequest reviewedRequest);
}
