package com.ticketon.application.organizer.query;

import java.util.Objects;

/**
 * 한 사람이 받은 운영자 초대 하나(docs/01 "운영자 초대"). 초대 표와 수락·거절은 #25가 만든다.
 *
 * @param organizerId   초대한 주최자 id
 * @param organizerName 초대한 주최자명
 */
public record ReceivedOrganizerInvitation(long organizerId, String organizerName) {

    public ReceivedOrganizerInvitation {
        Objects.requireNonNull(organizerName, "organizerName");
    }
}
