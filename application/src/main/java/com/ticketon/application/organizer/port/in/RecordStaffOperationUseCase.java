package com.ticketon.application.organizer.port.in;

/**
 * 운영진 조작 기록 — 들어오는 포트. 운영진의 조작을 마친 서비스가 부른다(docs/01 "권한 두 가지 — 누가 했는지 남깁니다").
 * <p>
 * <b>조작과 같은 트랜잭션에서만 남긴다.</b> 트랜잭션 밖에서 부르면 예외(Spring의 IllegalTransactionStateException)로 막는다 — 조작은
 * 됐는데 기록이 빠지거나, 조작은 되돌려졌는데 기록만 남는 일이 없게. 컨트롤러는 트랜잭션을 열지 않으므로 바깥에서 조작 없이 기록만 남기는
 * 길도 열리지 않는다. 기록 한 줄을 어떻게 만드는지는 도메인({@link com.ticketon.domain.organizer.StaffOperationRecord})이 정한다.
 */
public interface RecordStaffOperationUseCase {

    /**
     * @throws IllegalArgumentException 대상 id가 1보다 작거나, 다른 주최자를 대상으로 한 주최자 조작이거나, 요약이 비었으면 — 부르는 쪽의
     *                                  잘못이다
     */
    void record(RecordStaffOperationCommand command);
}
