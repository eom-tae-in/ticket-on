package com.ticketon.application.organizer.service;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 서비스 단위 테스트에서 트랜잭션의 끝을 흉내 낸다 — 트랜잭션 동기화를 켜고 일을 한 뒤, 커밋됐거나 되돌려졌다고 등록된 뒷정리에 알린다.
 * 파일 저장소처럼 트랜잭션에 묶이지 않는 것을 트랜잭션이 끝난 뒤 정리하는 서비스(로고 올리기 · 지우기)를 DB 없이 본다.
 */
final class SimulatedTransaction {

    private SimulatedTransaction() {
    }

    /** 일을 하고 커밋된 것으로 끝낸다. 일이 예외를 던지면 되돌려진 것으로 끝내고 그 예외를 그대로 던진다. */
    static <T> T run(Supplier<T> work) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            T result;
            try {
                result = work.get();
            } catch (RuntimeException failure) {
                complete(TransactionSynchronization.STATUS_ROLLED_BACK);
                throw failure;
            }
            complete(TransactionSynchronization.STATUS_COMMITTED);
            return result;
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static void complete(int status) {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
    }
}
