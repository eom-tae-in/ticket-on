package com.ticketon.application.organizer.port.out;

import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.StaffInvitation;
import java.util.Optional;

/**
 * 운영자 초대 저장소 — 나가는 포트.
 * <p>
 * 한 주최자가 한 이메일로 답을 기다리는 초대는 하나뿐이다(대소문자 무시). 초대는 지우지 않는다 — 답하면 답한 일시와 사람이 남는다.
 * 보내기 · 다시 보내기 · 답하기는 모두 "읽어 확인 → 저장" 두 단계라 저장할 때 표에서 다시 막는다({@link #add}, {@link #saveReinvitation},
 * {@link #saveAnswer}).
 */
public interface StaffInvitationRepository {

    /**
     * 막 보낸 초대를 저장하고, id가 붙은 초대를 돌려준다.
     *
     * @param newInvitation {@link StaffInvitation#issue}로 만든, id가 없는 초대
     * @throws ConflictException 확인한 뒤 같은 주최자 · 같은 이메일로 답을 기다리는 초대가 먼저 저장됐으면
     *                           ({@code STAFF_INVITATION_ALREADY_PENDING}) — 동시 초대 경합
     */
    StaffInvitation add(StaffInvitation newInvitation);

    Optional<StaffInvitation> findById(long id);

    /** 그 주최자가 그 이메일로 보내 답을 기다리는 초대 — 유효 기간이 지났어도 찾는다. 이메일의 대소문자는 가리지 않는다. 없으면 비어 있다. */
    Optional<StaffInvitation> findPending(long organizerId, EmailAddress inviteeEmail);

    /**
     * 다시 보낸 초대의 보낸 사람 · 보낸 일시 · 유효 기간을 저장한다. 표에서 아직 답을 기다리고 유효 기간이 지나 있을 때만 바꾼다.
     *
     * @param reinvitedInvitation {@link StaffInvitation#reinvite}로 다시 연 저장된 초대
     * @throws ConflictException 그 사이 다른 요청이 먼저 다시 보냈거나({@code STAFF_INVITATION_ALREADY_PENDING}), 받은 사람이 먼저
     *                           답했으면({@code STAFF_INVITATION_ALREADY_ANSWERED})
     */
    void saveReinvitation(StaffInvitation reinvitedInvitation);

    /**
     * 수락·거절한 초대의 상태와 답한 일시 · 사람을 저장한다. 표에서 아직 답을 기다릴 때만 바꾼다.
     *
     * @param answeredInvitation {@link StaffInvitation#accept} 또는 {@link StaffInvitation#decline}으로 답한 저장된 초대
     * @throws ConflictException 그 사이 다른 요청이 먼저 답했으면({@code STAFF_INVITATION_ALREADY_ANSWERED}) — 같은 초대를 거의 동시에
     *                           두 번 수락한 경합
     */
    void saveAnswer(StaffInvitation answeredInvitation);
}
