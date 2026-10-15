package com.ticketon.domain.organizer;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 주최자명 변경 이력 한 줄 — 어느 주최자의 이름을 언제, 무엇에서 무엇으로, 누가 바꿨는지({@link OrganizerNameChange})와, 관리자가 사후
 * 확인했는지.
 * <p>
 * 주최자명은 소유자가 바로 바꾸고 이력을 남긴다(docs/01 "공개 페이지 — 주최자명 변경"). 이력은 "샀을 때 무슨 이름이었나"를 확인할 근거다 —
 * 행사는 주최자를 가리키기만 하므로 이름을 바꾸면 지난 행사 페이지에도 새 이름이 뜬다(docs/01 "주최자명을 소유자가 바로 바꿀 수 있게 한 이유").
 * <p>
 * <b>관리자 알림</b> — 유명한 단체와 헷갈리게 하는 이름으로 바꾸면 관리자가 바로 알고 신고·정지로 처리한다(docs/01). 관리자는 알림 대신 처리
 * 목록으로 받는다(docs/12 "관리자 대상 — 주최자명 변경 (사후 확인용. 사칭이면 신고·정지로 처리)"). 관리자를 거치지 않고 이미 바뀐 일이라 관리자는
 * 승인하지 않고 <b>사후 확인</b>만 한다({@link #confirm}). 확인하면 처리 목록에서 빠진다. 확인은 한 번뿐이다. 소유자 변경의 사후 확인
 * ({@link OwnershipChange})과 같은 방식이다.
 * <p>
 * 이력은 지우지 않는다 — 문서에 정한 보관 기간이 없다(docs/99 C "보관 기간"에 없음). 시각은 인자로 받는다.
 */
public final class OrganizerNameChangeEntry {

    private final long id;
    private final long organizerId;
    private final OrganizerNameChange change;
    private Instant confirmedAt;
    private AccountRef confirmedBy;

    private OrganizerNameChangeEntry(long id, long organizerId, OrganizerNameChange change, Instant confirmedAt,
            AccountRef confirmedBy) {
        this.id = id;
        this.organizerId = organizerId;
        this.change = change;
        this.confirmedAt = confirmedAt;
        this.confirmedBy = confirmedBy;
    }

    /**
     * 저장소에서 읽은 이력을 되살린다.
     *
     * @param organizerId 이름을 바꾼 주최자 id
     * @param confirmedAt 관리자가 확인한 일시. 확인 전이면 null.
     * @param confirmedBy 확인한 관리자. 확인 전이면 null.
     * @throws IllegalArgumentException 1보다 작은 id, 확인 일시와 확인한 관리자 중 하나만 있음, 관리자가 아닌 주체, 바꾼 일시보다 앞선 확인
     */
    public static OrganizerNameChangeEntry restore(long id, long organizerId, OrganizerNameChange change,
            Instant confirmedAt, AccountRef confirmedBy) {
        if (id < 1 || organizerId < 1) {
            throw new IllegalArgumentException("주최자명 변경 id와 주최자 id는 1 이상이어야 합니다: " + id + ", " + organizerId);
        }
        Objects.requireNonNull(change, "change");
        if ((confirmedAt == null) != (confirmedBy == null)) {
            throw new IllegalArgumentException("확인 일시와 확인한 관리자는 함께 있거나 함께 없어야 합니다");
        }
        if (confirmedAt != null) {
            requireConfirmable(change, confirmedBy, confirmedAt);
        }
        return new OrganizerNameChangeEntry(id, organizerId, change, confirmedAt, confirmedBy);
    }

    /**
     * 관리자가 사후 확인한다. 확인하면 처리 목록에서 빠진다. 이름은 되돌리지 않는다 — 사칭이면 신고·정지 같은 다른 조치로 처리한다.
     *
     * @param admin 확인하는 관리자
     * @param now   확인 일시
     * @throws RuleViolationException   이미 확인했으면({@link OrganizerErrorCode#ORGANIZER_NAME_CHANGE_ALREADY_CONFIRMED})
     * @throws IllegalArgumentException 관리자가 아닌 주체이거나, 바꾼 일시보다 앞선 시각이면
     */
    public void confirm(AccountRef admin, Instant now) {
        Objects.requireNonNull(admin, "admin");
        Objects.requireNonNull(now, "now");
        if (isConfirmed()) {
            throw new RuleViolationException(OrganizerErrorCode.ORGANIZER_NAME_CHANGE_ALREADY_CONFIRMED,
                    "주최자명 변경 " + id + ", 확인한 관리자 " + confirmedBy.principalName());
        }
        requireConfirmable(change, admin, now);
        confirmedAt = now;
        confirmedBy = admin;
    }

    public long id() {
        return id;
    }

    /** 이름을 바꾼 주최자 id — 이력은 주최자를 가리킨다(docs/14 "무엇이 무엇을 가리키나"). */
    public long organizerId() {
        return organizerId;
    }

    /** 바꾼 한 건 — 바꾸기 전 이름 · 바꾼 이름 · 바꾼 사람 · 바꾼 일시. */
    public OrganizerNameChange change() {
        return change;
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

    private static void requireConfirmable(OrganizerNameChange change, AccountRef admin, Instant confirmedAt) {
        if (admin.kind() != AccountKind.ADMIN) {
            throw new IllegalArgumentException("주최자명 변경은 관리자가 확인합니다: " + admin.principalName());
        }
        if (confirmedAt.isBefore(change.changedAt())) {
            throw new IllegalArgumentException(
                    "바꾼 일시(" + change.changedAt() + ")보다 앞서 확인할 수 없습니다: " + confirmedAt);
        }
    }
}
