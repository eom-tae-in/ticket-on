package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 소유자 넘기기 — 들어오는 포트. 대표가 바뀌면 소유자가 그 주최자의 운영자에게 소유자를 넘긴다. 관리자를 거치지 않는다
 * (docs/01 "소유자 넘기기", docs/11 "대표가 바뀔 때").
 * <ul>
 *   <li>받을 수 있는 사람은 이미 그 주최자의 운영자인 사람뿐이다. 바깥 사람이면 먼저 운영자로 초대한다.</li>
 *   <li>넘긴 사람은 운영자로 남는다. 소유자는 넘기기 전에도 뒤에도 1명이다.</li>
 *   <li>넘기면 운영진 전원에게 메일([주최])이 가고, 관리자 처리 목록에 '소유자 변경'이 쌓인다 — 운영자로 들어온 사람이 소유자 자리를
 *       넘겨받아 정산 계좌를 바꾸는 가로채기를 바로 알아채게 하는 장치다(docs/01 "가로채기를 막는 장치", docs/12).</li>
 * </ul>
 * 소유자 변경 이력과 운영진 조작 기록에 남는다.
 */
public interface TransferOwnershipUseCase {

    /**
     * @throws PermissionDeniedException 넘기는 사람이 그 주최자의 소유자가 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#OWNER_ONLY_ACTION},
     *                                   {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws RuleViolationException    받을 사람이 그 주최자의 운영자가 아니면 — 소유자 자신, 바깥 사람, 이미 나간 사람, 운영진 로그인을
     *                                   탈퇴한 사람({@link OrganizerErrorCode#OWNERSHIP_RECEIVER_NOT_OPERATOR})
     * @throws ConflictException         그 사이 같은 소유자가 먼저 넘겼거나({@link OrganizerErrorCode#OWNERSHIP_ALREADY_TRANSFERRED}),
     *                                   받을 사람이 먼저 나갔으면({@link OrganizerErrorCode#MEMBERSHIP_ALREADY_ENDED})
     */
    void transfer(TransferOwnershipCommand command);
}
