package com.ticketon.application.organizer.port.out;

import com.ticketon.domain.organizer.StaffOperationRecord;

/**
 * 운영진 조작 기록 저장소 — 나가는 포트.
 * <p>
 * 운영진의 조작마다 한 줄씩 남긴다(docs/01 "권한 두 가지 — 누가 했는지 남깁니다"). 고치거나 지우지 않는다.
 * 조작과 같은 트랜잭션에서 남긴다 — 조작이 되돌려지면 기록도 함께 되돌려진다.
 */
public interface StaffOperationRecordRepository {

    void add(StaffOperationRecord record);
}
