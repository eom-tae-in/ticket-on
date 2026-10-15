package com.ticketon.domain.organizer;

/**
 * 운영자 초대의 상태 — 답을 기다림 / 수락 / 거절({@link StaffInvitation}).
 * <p>
 * <b>"만료"는 상태가 아니다.</b> 답을 기다리는 초대가 유효 기간을 넘겼는지는 그때의 시각으로 계산한다({@link StaffInvitation#isExpiredAt}) —
 * 시각이 지나는 것만으로 저장된 값이 바뀌지 않게 한다(docs/14 "저장하는 것과 계산하는 것"과 같은 까닭).
 * <p>
 * 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum StaffInvitationStatus {

    /** 받은 사람의 답을 기다린다. 유효 기간이 지났으면 더는 답할 수 없다. */
    PENDING,

    /** 받은 사람이 수락해 운영자가 됐다. */
    ACCEPTED,

    /** 받은 사람이 거절했다. */
    DECLINED
}
