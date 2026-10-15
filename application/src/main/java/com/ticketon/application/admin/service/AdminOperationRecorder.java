package com.ticketon.application.admin.service;

import com.ticketon.application.admin.port.out.AdminOperationRecordRepository;
import com.ticketon.domain.admin.AdminOperationRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 조작 기록기 — 관리자 기능의 서비스들이 조작을 마칠 때 공통으로 부른다(docs/01 "관리자 2등급 — 모든 조작에 '누가 언제 무엇을'을
 * 기록").
 * <p>
 * <b>조작과 같은 트랜잭션에서만 남긴다</b>({@link Propagation#MANDATORY}). 트랜잭션 밖에서 부르면 막는다 — 조작은 됐는데 기록이 빠지거나,
 * 조작은 되돌려졌는데 기록만 남는 일이 없게. 기록 한 줄을 어떻게 만드는지는 도메인({@link AdminOperationRecord})이 정한다.
 * <p>
 * 들어오는 포트가 아니다 — 바깥(컨트롤러)에서 조작 없이 기록만 남기는 길을 열지 않는다.
 */
@Component
public class AdminOperationRecorder {

    private final AdminOperationRecordRepository recordRepository;

    public AdminOperationRecorder(AdminOperationRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AdminOperationRecord record) {
        recordRepository.add(record);
    }
}
