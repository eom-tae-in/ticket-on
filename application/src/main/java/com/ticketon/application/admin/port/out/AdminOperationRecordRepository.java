package com.ticketon.application.admin.port.out;

import com.ticketon.domain.admin.AdminOperationRecord;

/**
 * 관리자 조작 기록 저장소 — 나가는 포트.
 * <p>
 * 관리자의 조작마다 한 줄씩 남긴다(docs/01 "관리자 2등급 — 모든 조작에 '누가 언제 무엇을'을 기록"). 고치거나 지우지 않는다.
 * 조작과 같은 트랜잭션에서 남긴다 — 조작이 되돌려지면 기록도 함께 되돌려진다.
 */
public interface AdminOperationRecordRepository {

    void add(AdminOperationRecord record);
}
