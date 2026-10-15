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

    /**
     * 그 초대를 읽고, 부른 트랜잭션이 끝날 때까지 그 줄을 잠근다 — 받은 초대에 답하는(수락 · 거절) 흐름이 맨 먼저 부른다.
     * <p>
     * 다른 트랜잭션이 그 줄을 잠그고 있으면 그쪽이 끝날 때까지 기다렸다가 끝난 뒤의 값(예: 이미 수락됨)을 돌려준다. 같은 초대에 거의 동시에
     * 답하면 늦은 쪽은 늘 먼저 답한 결과를 보고 "이미 답한 초대"로 멈춘다 — 먼저 수락한 쪽이 만든 소속을 보고 다른 이유로 멈추는 일이 없다.
     *
     * @return 없는 초대면 비어 있다
     */
    Optional<StaffInvitation> findByIdForUpdate(long id);

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
