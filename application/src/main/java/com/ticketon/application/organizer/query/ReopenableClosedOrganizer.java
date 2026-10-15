package com.ticketon.application.organizer.query;

import java.util.Objects;

/**
 * 한 사람이 다시 열기를 신청할 수 있는 닫힌 주최자 하나(docs/01 "닫힌 주최자의 소유자였다면 '다시 열기 신청'도 보입니다").
 * 누가 신청할 수 있는지와 다시 열기 신청은 #134가 만든다.
 *
 * @param organizerId   닫힌 주최자 id
 * @param organizerName 닫힌 주최자명
 */
public record ReopenableClosedOrganizer(long organizerId, String organizerName) {

    public ReopenableClosedOrganizer {
        Objects.requireNonNull(organizerName, "organizerName");
    }
}
