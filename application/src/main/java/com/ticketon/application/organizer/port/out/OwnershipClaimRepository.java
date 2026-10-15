package com.ticketon.application.organizer.port.out;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OwnershipClaim;
import java.util.Optional;

/**
 * 소유자 이전 신청 저장소 — 나가는 포트. 신청은 지우지 않는다 — 반려된 신청도 사유와 함께 남고, 승인한 신청은 소유자 변경 이력이 가리킨다.
 * <p>
 * 한 사람은 한 주최자에 심사 중인 신청을 하나만 둔다. 새 신청을 낼 수 있는지는 저장소가 확인해 규칙({@link OwnershipClaim#submit})에 넘기고,
 * 확인과 저장 사이에 같은 사람의 다른 신청이 먼저 저장될 수 있으므로 저장할 때 다시 막는다({@link #add}). 심사 중인 신청은 관리자 처리 목록에
 * '소유자 이전 신청'으로 쌓인다(처리 목록 조회가 이 표를 읽는다).
 */
public interface OwnershipClaimRepository {

    /** 그 사람이 그 주최자에 지금 심사 중인 신청을 냈는지. 승인·반려된 신청은 세지 않는다. */
    boolean hasClaimUnderReview(long organizerId, long claimantStaffAccountId);

    /**
     * 막 낸 신청을 저장하고, id가 붙은 신청을 돌려준다.
     *
     * @param newClaim {@link OwnershipClaim#submit}으로 만든, id가 없는 신청
     * @throws ConflictException 확인한 뒤 같은 사람의 다른 신청이 먼저 심사 중으로 저장됐으면({@code OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW})
     *                           — 같은 사람이 거의 동시에 두 번 낸 경합
     */
    OwnershipClaim add(OwnershipClaim newClaim);

    Optional<OwnershipClaim> findById(long id);

    /**
     * 승인·반려한 신청의 상태 · 처리 일시 · 반려 사유를 저장한다.
     * <p>
     * <b>표에서 아직 심사 중일 때만 바꾼다.</b> 두 관리자가 같은 신청을 거의 동시에 승인·반려하면 둘 다 심사 중으로 읽고 규칙을 통과할 수 있어,
     * 마지막 판정은 이 조건부 저장이 한다. 진 쪽은 충돌이 되고 그 트랜잭션은 되돌려진다 — 함께 바꾼 소속 · 이력 · 조작 기록도 남지 않고 메일도
     * 나가지 않는다.
     *
     * @param reviewedClaim {@link OwnershipClaim#approve} 또는 {@link OwnershipClaim#reject}로 처리한, 저장된 신청
     * @throws ConflictException        표의 신청이 이미 심사 중이 아니면({@code OWNERSHIP_CLAIM_ALREADY_REVIEWED}) — 다른 관리자가 먼저 처리했다
     * @throws IllegalArgumentException 저장 전 신청이거나 아직 심사 중인 신청이면
     */
    void saveReview(OwnershipClaim reviewedClaim);
}
