package com.ticketon.domain.organizer;

import java.time.Instant;
import java.util.Objects;

/**
 * 소유자 넘기기 한 건 — 어느 주최자에서 누가 누구에게 언제 넘겼는지.
 * <p>
 * 넘기면 운영자 전원과 관리자에게 알린다 — 운영자로 들어온 사람이 소유자 자리를 넘겨받아 정산 계좌를 바꾸는 가로채기를 바로 알아채게
 * 하기 위해서다(docs/01 "소유자 넘기기", "가로채기를 막는 장치"). 알림과 이력 저장은 유스케이스가 이 값으로 한다.
 *
 * @param organizerId                 주최자 id
 * @param previousOwnerStaffAccountId 넘긴 사람 — 운영자로 남는다
 * @param newOwnerStaffAccountId      받은 사람 — 넘기기 전에 그 주최자의 운영자였다
 * @param transferredAt               넘긴 일시
 */
public record OwnershipTransfer(long organizerId, long previousOwnerStaffAccountId, long newOwnerStaffAccountId,
        Instant transferredAt) {

    public OwnershipTransfer {
        if (previousOwnerStaffAccountId == newOwnerStaffAccountId) {
            throw new IllegalArgumentException("자기 자신에게 소유자를 넘길 수 없습니다: " + newOwnerStaffAccountId);
        }
        Objects.requireNonNull(transferredAt, "transferredAt");
    }
}
