package com.ticketon.application.organizer.port.in;

/**
 * 주최자 이력의 운영진 조작 기록 조회 입력.
 *
 * @param organizerId    주최자 id (1 이상)
 * @param beforeRecordId 이어 보기 — 앞서 받은 결과의 {@link StaffOperationHistoryResult#nextBeforeRecordId}. 처음부터 보면 null.
 * @param limit          한 번에 많아야 이만큼 (1 이상). 화면이 정한다.
 */
public record GetStaffOperationHistoryQuery(long organizerId, Long beforeRecordId, int limit) {

    public GetStaffOperationHistoryQuery {
        if (organizerId < 1) {
            throw new IllegalArgumentException("주최자 id는 1 이상이어야 합니다: " + organizerId);
        }
        if (beforeRecordId != null && beforeRecordId < 1) {
            throw new IllegalArgumentException("이어 볼 기록 id는 1 이상이어야 합니다: " + beforeRecordId);
        }
        if (limit < 1) {
            throw new IllegalArgumentException("한 번에 볼 기록 수는 1 이상이어야 합니다: " + limit);
        }
    }
}
