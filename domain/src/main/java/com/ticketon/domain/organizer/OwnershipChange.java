package com.ticketon.domain.organizer;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 소유자 변경 이력 한 건 — 어느 주최자에서 누가 누구에게 언제 소유자를 넘겼는지({@link OwnershipTransfer})와, 관리자가 사후 확인했는지.
 * <p>
 * <b>소유자가 바뀌면 관리자에게도 알린다.</b> 관리자는 알림 대신 처리 목록으로 받는다(docs/12 "관리자 대상", "소유자가 바뀌면 관리자에게도
 * 알립니다"). 운영자로 들어온 사람이 소유자 자리를 넘겨받아 정산 계좌를 바꾸는 가로채기를 관리자가 바로 알아채게 하려는 장치다
 * (docs/01 "가로채기를 막는 장치").
 * <p>
 * 소유자 넘기기는 관리자를 거치지 않고 이미 끝난 일이라(docs/01 "소유자 넘기기") 관리자는 승인하지 않고 <b>사후 확인</b>만 한다
 * ({@link #confirm}). 확인하면 처리 목록에서 빠진다. 확인은 한 번뿐이다. 문제가 있으면 신고·정지 같은 다른 조치로 처리한다.
 * <p>
 * 이력은 지우지 않는다 — 관리자 주최자 상세의 "소유자 변경 이력"이 된다(docs/13 "5. 플랫폼 관리자 — 주최자 목록"). 시각은 인자로 받는다.
 */
public final class OwnershipChange {

    private final long id;
    private final OwnershipTransfer transfer;
    private Instant confirmedAt;
    private AccountRef confirmedBy;

    private OwnershipChange(long id, OwnershipTransfer transfer, Instant confirmedAt, AccountRef confirmedBy) {
        this.id = id;
        this.transfer = transfer;
        this.confirmedAt = confirmedAt;
        this.confirmedBy = confirmedBy;
    }

    /**
     * 저장소에서 읽은 값으로 되살린다.
     *
     * @param confirmedAt 관리자가 확인한 일시. 확인 전이면 null.
     * @param confirmedBy 확인한 관리자. 확인 전이면 null.
     * @throws IllegalArgumentException 1보다 작은 id, 확인 일시와 확인한 관리자 중 하나만 있음, 관리자가 아닌 주체, 넘긴 일시보다 앞선 확인
     */
    public static OwnershipChange restore(long id, OwnershipTransfer transfer, Instant confirmedAt, AccountRef confirmedBy) {
        if (id < 1) {
            throw new IllegalArgumentException("소유자 변경 id는 1 이상이어야 합니다: " + id);
        }
        Objects.requireNonNull(transfer, "transfer");
        if ((confirmedAt == null) != (confirmedBy == null)) {
            throw new IllegalArgumentException("확인 일시와 확인한 관리자는 함께 있거나 함께 없어야 합니다");
        }
        if (confirmedAt != null) {
            requireConfirmable(transfer, confirmedBy, confirmedAt);
        }
        return new OwnershipChange(id, transfer, confirmedAt, confirmedBy);
    }

    /**
     * 관리자가 사후 확인한다. 확인하면 처리 목록에서 빠진다.
     *
     * @param admin 확인하는 관리자
     * @param now   확인 일시
     * @throws RuleViolationException   이미 확인했으면({@link OrganizerErrorCode#OWNERSHIP_CHANGE_ALREADY_CONFIRMED})
     * @throws IllegalArgumentException 관리자가 아닌 주체이거나, 넘긴 일시보다 앞선 시각이면
     */
    public void confirm(AccountRef admin, Instant now) {
        Objects.requireNonNull(admin, "admin");
        Objects.requireNonNull(now, "now");
        if (isConfirmed()) {
            throw new RuleViolationException(OrganizerErrorCode.OWNERSHIP_CHANGE_ALREADY_CONFIRMED,
                    "소유자 변경 " + id + ", 확인한 관리자 " + confirmedBy.principalName());
        }
        requireConfirmable(transfer, admin, now);
        confirmedAt = now;
        confirmedBy = admin;
    }

    public long id() {
        return id;
    }

    /** 넘기기 한 건 — 주최자 · 넘긴 사람 · 받은 사람 · 넘긴 일시. */
    public OwnershipTransfer transfer() {
        return transfer;
    }

    /** 관리자가 확인했는지. 확인 전이면 처리 목록에 남아 있다. */
    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    /** 관리자가 확인한 일시. 확인 전이면 비어 있다. */
    public Optional<Instant> confirmedAt() {
        return Optional.ofNullable(confirmedAt);
    }

    /** 확인한 관리자. 확인 전이면 비어 있다. */
    public Optional<AccountRef> confirmedBy() {
        return Optional.ofNullable(confirmedBy);
    }

    private static void requireConfirmable(OwnershipTransfer transfer, AccountRef admin, Instant confirmedAt) {
        if (admin.kind() != AccountKind.ADMIN) {
            throw new IllegalArgumentException("소유자 변경은 관리자가 확인합니다: " + admin.principalName());
        }
        if (confirmedAt.isBefore(transfer.transferredAt())) {
            throw new IllegalArgumentException(
                    "넘긴 일시(" + transfer.transferredAt() + ")보다 앞서 확인할 수 없습니다: " + confirmedAt);
        }
    }
}
