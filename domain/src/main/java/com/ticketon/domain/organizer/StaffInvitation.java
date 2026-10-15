package com.ticketon.domain.organizer;

import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.policy.ProposedPolicy;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 운영자 초대 한 건 — 소유자가 이메일로 보내고, 그 이메일의 운영진 로그인으로 수락하면 운영자가 된다(docs/01 "운영자 초대").
 * <pre>
 * 소유자가 이메일로 초대
 *   → 받은 사람이 주최자 쪽 로그인을 만들거나 기존 로그인으로 수락
 *   → 운영자로 소속
 * </pre>
 * 지켜지는 것:
 * <ul>
 *   <li><b>소유자만 보낸다</b>({@link StaffAction#INVITE_STAFF}, docs/01 "권한 두 가지 — 운영진 초대·내보내기").</li>
 *   <li><b>이미 그 주최자의 운영진인 사람에게는 보내지 않는다.</b> 한 사람은 한 주최자에 한 역할로만 소속된다. 다른 주최자의 운영진에게는
 *       보낸다 — 한 사람이 여러 주최자에 소속될 수 있다.</li>
 *   <li><b>받는 사람은 이메일로 정한다.</b> 초대받은 이메일과 같은 이메일(대소문자 무시 — 운영진 로그인 이메일이 하나뿐인 기준과 같다)의
 *       운영진 로그인만 답한다({@link #isAddressedTo}). 운영진 로그인은 이메일 인증을 마쳐야 로그인되므로, 답하는 사람은 그 메일함의 주인이다.
 *       아직 주최자 쪽 로그인이 없는 사람에게도 보낸다 — 받은 사람이 그 이메일로 로그인을 만들어 답한다.</li>
 *   <li><b>유효 기간</b> — 보낸 때부터 {@link ProposedPolicy#STAFF_INVITATION_VALIDITY}(확인 필요 #25). 끝나는 일시 그 순간부터 답할 수
 *       없다. 만료는 상태로 저장하지 않고 시각으로 계산한다({@link #isExpiredAt}).</li>
 *   <li><b>한 번만 답한다.</b> 수락하거나 거절한 초대에는 다시 답할 수 없다.</li>
 *   <li><b>운영자는 심사를 받지 않는다.</b> 수락하면 바로 운영자 소속이 시작된다({@link OrganizerStaff#addOperator}).</li>
 * </ul>
 * 한 주최자가 한 이메일로 답을 기다리는 초대는 하나뿐이다. 그 초대가 아직 유효하면 같은 이메일로 다시 보내지 않고, 유효 기간이 지났으면
 * 그 초대를 지금부터 다시 연다({@link #reinvite}).
 * <p>
 * 영구 정지·차단된 사람의 수락을 막는 일(docs/01 "정지 4단계")은 영구 정지 기능(#124)이 더한다. 시각은 인자로 받는다.
 * 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 초대는 id가 없다.
 */
public final class StaffInvitation {

    private final Long id;
    private final long organizerId;
    private final EmailAddress inviteeEmail;
    private long invitedByStaffAccountId;
    private Instant invitedAt;
    private Instant expiresAt;
    private StaffInvitationStatus status;
    private Instant answeredAt;
    private Long answeredByStaffAccountId;

    private StaffInvitation(Long id, long organizerId, EmailAddress inviteeEmail, long invitedByStaffAccountId,
            Instant invitedAt, Instant expiresAt, StaffInvitationStatus status, Instant answeredAt,
            Long answeredByStaffAccountId) {
        if (id != null && id < 1) {
            throw new IllegalArgumentException("초대 id는 1 이상이어야 합니다: " + id);
        }
        if (organizerId < 1) {
            throw new IllegalArgumentException("주최자 id는 1 이상이어야 합니다: " + organizerId);
        }
        if (invitedByStaffAccountId < 1) {
            throw new IllegalArgumentException("초대한 운영진 계정 id는 1 이상이어야 합니다: " + invitedByStaffAccountId);
        }
        Objects.requireNonNull(inviteeEmail, "inviteeEmail");
        Objects.requireNonNull(invitedAt, "invitedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(status, "status");
        if (!expiresAt.isAfter(invitedAt)) {
            throw new IllegalArgumentException("유효 기간이 끝나는 일시(" + expiresAt + ")는 보낸 일시(" + invitedAt + ") 뒤여야 합니다");
        }
        boolean answered = answeredAt != null && answeredByStaffAccountId != null;
        boolean unanswered = answeredAt == null && answeredByStaffAccountId == null;
        if (status == StaffInvitationStatus.PENDING ? !unanswered : !answered) {
            throw new IllegalArgumentException("상태(" + status + ")와 답한 일시·사람이 맞지 않습니다");
        }
        this.id = id;
        this.organizerId = organizerId;
        this.inviteeEmail = inviteeEmail;
        this.invitedByStaffAccountId = invitedByStaffAccountId;
        this.invitedAt = invitedAt;
        this.expiresAt = expiresAt;
        this.status = status;
        this.answeredAt = answeredAt;
        this.answeredByStaffAccountId = answeredByStaffAccountId;
    }

    /**
     * 그 이메일로 운영자 초대를 보낸다. 유효 기간은 지금부터 {@link ProposedPolicy#STAFF_INVITATION_VALIDITY}다.
     *
     * @param staff                   초대하는 주최자의 운영진 명단
     * @param invitedByStaffAccountId 초대하는 사람 — 그 주최자의 소유자
     * @param inviteeEmail            초대받는 이메일
     * @param inviteeStaffAccountId   그 이메일로 가입한 운영진 로그인의 id. 아직 주최자 쪽 로그인이 없으면 비어 있다.
     * @throws PermissionDeniedException 초대하는 사람이 그 주최자의 소유자가 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#OWNER_ONLY_ACTION})
     * @throws RuleViolationException    그 이메일의 사람이 이미 그 주최자의 운영진이면({@link OrganizerErrorCode#ALREADY_ORGANIZER_STAFF})
     */
    public static StaffInvitation issue(OrganizerStaff staff, long invitedByStaffAccountId, EmailAddress inviteeEmail,
            OptionalLong inviteeStaffAccountId, Instant now) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(inviteeEmail, "inviteeEmail");
        Objects.requireNonNull(now, "now");
        requireInvitable(staff, invitedByStaffAccountId, inviteeStaffAccountId);
        return new StaffInvitation(null, staff.organizerId(), inviteeEmail, invitedByStaffAccountId, now,
                now.plus(ProposedPolicy.STAFF_INVITATION_VALIDITY), StaffInvitationStatus.PENDING, null, null);
    }

    /**
     * 저장소에서 읽은 값으로 초대를 되살린다. 보내는 규칙은 다시 보지 않는다.
     *
     * @param answeredAt               답한 일시. 답을 기다리면 null.
     * @param answeredByStaffAccountId 답한 운영진 계정 id. 답을 기다리면 null.
     * @throws IllegalArgumentException 상태와 답한 일시·사람이 맞지 않거나, 유효 기간이 보낸 일시보다 앞서면
     */
    public static StaffInvitation restore(long id, long organizerId, EmailAddress inviteeEmail,
            long invitedByStaffAccountId, Instant invitedAt, Instant expiresAt, StaffInvitationStatus status,
            Instant answeredAt, Long answeredByStaffAccountId) {
        return new StaffInvitation(id, organizerId, inviteeEmail, invitedByStaffAccountId, invitedAt, expiresAt, status,
                answeredAt, answeredByStaffAccountId);
    }

    /**
     * 답을 기다리던 이 초대를 같은 이메일로 다시 보낸다 — 유효 기간이 지났을 때만. 보낸 사람·보낸 일시가 바뀌고 유효 기간이 지금부터 다시
     * 시작한다. 아직 유효하면 받은 사람이 이미 답할 수 있으므로 다시 보내지 않는다.
     *
     * @throws PermissionDeniedException 다시 보내는 사람이 그 주최자의 소유자가 아니면
     * @throws RuleViolationException    아직 유효 기간이 남았으면({@link OrganizerErrorCode#STAFF_INVITATION_ALREADY_PENDING}), 그 이메일의
     *                                   사람이 이미 그 주최자의 운영진이면({@link OrganizerErrorCode#ALREADY_ORGANIZER_STAFF})
     * @throws IllegalArgumentException  다른 주최자의 명단이면
     * @throws IllegalStateException     이미 답한 초대면 — 답을 기다리는 초대만 다시 보낸다
     */
    public void reinvite(OrganizerStaff staff, long invitedByStaffAccountId, OptionalLong inviteeStaffAccountId,
            Instant now) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(now, "now");
        requireSameOrganizer(staff.organizerId());
        if (status != StaffInvitationStatus.PENDING) {
            throw new IllegalStateException("이미 답한 초대는 다시 보내지 않습니다: 초대 " + id + ", 상태 " + status);
        }
        requireInvitable(staff, invitedByStaffAccountId, inviteeStaffAccountId);
        if (!isExpiredAt(now)) {
            throw new RuleViolationException(OrganizerErrorCode.STAFF_INVITATION_ALREADY_PENDING,
                    "주최자 " + organizerId + ", 초대 " + id + ", 유효 기간 끝 " + expiresAt);
        }
        this.invitedByStaffAccountId = invitedByStaffAccountId;
        this.invitedAt = now;
        this.expiresAt = now.plus(ProposedPolicy.STAFF_INVITATION_VALIDITY);
    }

    /**
     * 초대를 수락한다 — 그 이메일의 사람이 그 주최자의 운영자가 된다. 심사를 받지 않는다(docs/01 "운영자 초대").
     * <p>
     * 답했는지 → 유효 기간 → 주최자 상태 → 이미 운영진인지 순서로 보고, 처음 걸린 것으로 막는다.
     *
     * @param acceptorStaffAccountId 수락하는 사람의 운영진 계정 id
     * @param acceptorEmail          수락하는 사람의 운영진 로그인 이메일 — 초대받은 이메일이어야 한다. 부르는 쪽이
     *                               {@link #isAddressedTo}로 먼저 거른다.
     * @param organizer              초대한 주최자
     * @param staff                  초대한 주최자의 운영진 명단 — 운영자 소속이 여기에 더해진다
     * @return 막 시작한 운영자 소속
     * @throws RuleViolationException   이미 답했거나({@link OrganizerErrorCode#STAFF_INVITATION_ALREADY_ANSWERED}), 유효 기간이
     *                                  지났거나({@link OrganizerErrorCode#STAFF_INVITATION_EXPIRED}), 주최자가 활동 중이 아니거나
     *                                  ({@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE}), 이미 그 주최자의 운영진이면
     *                                  ({@link OrganizerErrorCode#ALREADY_ORGANIZER_STAFF})
     * @throws IllegalArgumentException 초대받은 이메일이 아니거나, 다른 주최자의 것이면
     */
    public StaffMembership accept(long acceptorStaffAccountId, EmailAddress acceptorEmail, Organizer organizer,
            OrganizerStaff staff, Instant now) {
        Objects.requireNonNull(organizer, "organizer");
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(now, "now");
        requireSameOrganizer(organizer.id().orElseThrow(() -> new IllegalArgumentException("저장 전 주최자입니다")));
        requireSameOrganizer(staff.organizerId());
        requireAddressedTo(acceptorEmail);
        requireAwaitingAnswer(now);
        if (!organizer.isActive()) {
            throw new RuleViolationException(OrganizerErrorCode.ORGANIZER_NOT_ACTIVE,
                    "주최자 " + organizerId + ", 상태: " + organizer.status() + ", 초대 " + id);
        }
        StaffMembership joined = staff.addOperator(acceptorStaffAccountId, now);
        answer(StaffInvitationStatus.ACCEPTED, acceptorStaffAccountId, now);
        return joined;
    }

    /**
     * 초대를 거절한다. 그 주최자에는 아무것도 바뀌지 않고, 소유자는 같은 이메일로 다시 초대할 수 있다.
     *
     * @param staffAccountId 거절하는 사람의 운영진 계정 id
     * @param email          거절하는 사람의 운영진 로그인 이메일 — 초대받은 이메일이어야 한다. 부르는 쪽이 {@link #isAddressedTo}로
     *                       먼저 거른다.
     * @throws RuleViolationException   이미 답했거나({@link OrganizerErrorCode#STAFF_INVITATION_ALREADY_ANSWERED}), 유효 기간이
     *                                  지났으면({@link OrganizerErrorCode#STAFF_INVITATION_EXPIRED})
     * @throws IllegalArgumentException 초대받은 이메일이 아니면
     */
    public void decline(long staffAccountId, EmailAddress email, Instant now) {
        Objects.requireNonNull(now, "now");
        requireAddressedTo(email);
        requireAwaitingAnswer(now);
        answer(StaffInvitationStatus.DECLINED, staffAccountId, now);
    }

    /** 그 이메일로 온 초대인지 — 대소문자를 가리지 않는다. */
    public boolean isAddressedTo(EmailAddress email) {
        Objects.requireNonNull(email, "email");
        return lowercase(inviteeEmail).equals(lowercase(email));
    }

    /** 그 순간에 유효 기간이 지났는지. 끝나는 일시 그 순간부터 지난 것이다. 답한 초대에도 시각만으로 답한다. */
    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(expiresAt);
    }

    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public long organizerId() {
        return organizerId;
    }

    public EmailAddress inviteeEmail() {
        return inviteeEmail;
    }

    /** 초대한 사람 — 다시 보냈으면 다시 보낸 사람. */
    public long invitedByStaffAccountId() {
        return invitedByStaffAccountId;
    }

    /** 보낸 일시 — 다시 보냈으면 다시 보낸 일시. 유효 기간이 여기서 시작한다. */
    public Instant invitedAt() {
        return invitedAt;
    }

    /** 이 순간부터 답할 수 없다. */
    public Instant expiresAt() {
        return expiresAt;
    }

    public StaffInvitationStatus status() {
        return status;
    }

    /** 답한 일시. 답을 기다리면 비어 있다. */
    public Optional<Instant> answeredAt() {
        return Optional.ofNullable(answeredAt);
    }

    /** 답한 사람의 운영진 계정 id. 답을 기다리면 비어 있다. */
    public OptionalLong answeredByStaffAccountId() {
        return answeredByStaffAccountId == null ? OptionalLong.empty() : OptionalLong.of(answeredByStaffAccountId);
    }

    private static void requireInvitable(OrganizerStaff staff, long invitedByStaffAccountId,
            OptionalLong inviteeStaffAccountId) {
        Objects.requireNonNull(inviteeStaffAccountId, "inviteeStaffAccountId");
        staff.requirePermission(invitedByStaffAccountId, StaffAction.INVITE_STAFF);
        if (inviteeStaffAccountId.isPresent() && staff.roleOf(inviteeStaffAccountId.getAsLong()).isPresent()) {
            throw new RuleViolationException(OrganizerErrorCode.ALREADY_ORGANIZER_STAFF,
                    "주최자 " + staff.organizerId() + ", 운영진 " + inviteeStaffAccountId.getAsLong());
        }
    }

    private void requireSameOrganizer(long otherOrganizerId) {
        if (otherOrganizerId != organizerId) {
            throw new IllegalArgumentException(
                    "주최자 " + organizerId + "의 초대를 다른 주최자(" + otherOrganizerId + ")로 처리하려 했습니다");
        }
    }

    private void requireAddressedTo(EmailAddress email) {
        if (!isAddressedTo(email)) {
            throw new IllegalArgumentException("초대받은 이메일의 운영진 로그인이 아닙니다: 초대 " + id);
        }
    }

    private void requireAwaitingAnswer(Instant now) {
        if (status != StaffInvitationStatus.PENDING) {
            throw new RuleViolationException(OrganizerErrorCode.STAFF_INVITATION_ALREADY_ANSWERED,
                    "초대 " + id + ", 상태 " + status);
        }
        if (isExpiredAt(now)) {
            throw new RuleViolationException(OrganizerErrorCode.STAFF_INVITATION_EXPIRED,
                    "초대 " + id + ", 유효 기간 끝 " + expiresAt);
        }
    }

    private void answer(StaffInvitationStatus answer, long staffAccountId, Instant now) {
        if (staffAccountId < 1) {
            throw new IllegalArgumentException("운영진 계정 id는 1 이상이어야 합니다: " + staffAccountId);
        }
        this.status = answer;
        this.answeredAt = now;
        this.answeredByStaffAccountId = staffAccountId;
    }

    private static String lowercase(EmailAddress email) {
        return email.value().toLowerCase(Locale.ROOT);
    }
}
