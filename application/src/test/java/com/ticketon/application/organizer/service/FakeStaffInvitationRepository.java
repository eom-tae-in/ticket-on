package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.out.StaffInvitationRepository;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffInvitation;
import com.ticketon.domain.organizer.StaffInvitationStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 메모리 운영자 초대 저장소 — 저장 순서대로 1부터 id를 붙이고, 표의 약속을 그대로 지킨다: 한 주최자 · 한 이메일(대소문자 무시)에 답을 기다리는
 * 초대 하나, 답은 아직 기다릴 때만, 다시 보내기는 아직 기다리고 유효 기간이 지나 있을 때만. 읽을 때마다 새 객체를 돌려준다 — 저장하지 않은
 * 변경은 남지 않는다.
 */
final class FakeStaffInvitationRepository implements StaffInvitationRepository {

    private final Map<Long, StaffInvitation> invitations = new LinkedHashMap<>();
    private final List<Long> lockedReads = new ArrayList<>();
    private long nextId = 1;

    List<StaffInvitation> savedInvitations() {
        return new ArrayList<>(invitations.values().stream().map(FakeStaffInvitationRepository::copyOf).toList());
    }

    @Override
    public StaffInvitation add(StaffInvitation newInvitation) {
        if (newInvitation.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 초대입니다");
        }
        if (findPending(newInvitation.organizerId(), newInvitation.inviteeEmail()).isPresent()) {
            throw new ConflictException(OrganizerErrorCode.STAFF_INVITATION_ALREADY_PENDING, "동시 초대 경합(흉내)");
        }
        long id = nextId++;
        StaffInvitation saved = withId(newInvitation, id);
        invitations.put(id, saved);
        return copyOf(saved);
    }

    @Override
    public Optional<StaffInvitation> findById(long id) {
        return Optional.ofNullable(invitations.get(id)).map(FakeStaffInvitationRepository::copyOf);
    }

    /**
     * 메모리 저장소에는 다른 트랜잭션이 없어 잠글 것이 없다 — 잠금을 기다린 뒤 커밋된 값을 읽는 것과 같게, 지금 저장된 값을 돌려준다. 잠금을
     * 거쳐 읽었는지는 {@link #lockedReads}로 본다.
     */
    @Override
    public Optional<StaffInvitation> findByIdForUpdate(long id) {
        lockedReads.add(id);
        return findById(id);
    }

    /** 잠그고 읽은 초대 id — 읽은 순서대로. */
    List<Long> lockedReads() {
        return List.copyOf(lockedReads);
    }

    @Override
    public Optional<StaffInvitation> findPending(long organizerId, EmailAddress inviteeEmail) {
        return invitations.values().stream()
                .filter(invitation -> invitation.organizerId() == organizerId)
                .filter(invitation -> invitation.status() == StaffInvitationStatus.PENDING)
                .filter(invitation -> lowercase(invitation.inviteeEmail()).equals(lowercase(inviteeEmail)))
                .findFirst()
                .map(FakeStaffInvitationRepository::copyOf);
    }

    @Override
    public void saveReinvitation(StaffInvitation reinvitedInvitation) {
        long id = reinvitedInvitation.id().orElseThrow();
        StaffInvitation stored = invitations.get(id);
        if (stored.status() != StaffInvitationStatus.PENDING) {
            throw new ConflictException(OrganizerErrorCode.STAFF_INVITATION_ALREADY_ANSWERED, "다시 보내기 경합(흉내)");
        }
        if (stored.expiresAt().isAfter(reinvitedInvitation.invitedAt())) {
            throw new ConflictException(OrganizerErrorCode.STAFF_INVITATION_ALREADY_PENDING, "다시 보내기 경합(흉내)");
        }
        invitations.put(id, copyOf(reinvitedInvitation));
    }

    @Override
    public void saveAnswer(StaffInvitation answeredInvitation) {
        long id = answeredInvitation.id().orElseThrow();
        if (invitations.get(id).status() != StaffInvitationStatus.PENDING) {
            throw new ConflictException(OrganizerErrorCode.STAFF_INVITATION_ALREADY_ANSWERED, "수락 경합(흉내)");
        }
        invitations.put(id, copyOf(answeredInvitation));
    }

    private static StaffInvitation withId(StaffInvitation invitation, long id) {
        return StaffInvitation.restore(id, invitation.organizerId(), invitation.inviteeEmail(),
                invitation.invitedByStaffAccountId(), invitation.invitedAt(), invitation.expiresAt(), invitation.status(),
                invitation.answeredAt().orElse(null), answeredByOrNull(invitation));
    }

    private static StaffInvitation copyOf(StaffInvitation invitation) {
        return withId(invitation, invitation.id().orElseThrow());
    }

    private static Long answeredByOrNull(StaffInvitation invitation) {
        return invitation.answeredByStaffAccountId().isPresent() ? invitation.answeredByStaffAccountId().getAsLong() : null;
    }

    private static String lowercase(EmailAddress email) {
        return email.value().toLowerCase(Locale.ROOT);
    }
}
