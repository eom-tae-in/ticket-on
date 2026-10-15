package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.out.OrganizerLogoStoragePort;
import com.ticketon.domain.organizer.OrganizerLogo;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 로고 파일 뒷정리 — 파일 저장소는 트랜잭션에 묶이지 않으므로, 주최자 저장이 커밋됐는지 되돌려졌는지를 보고 남은 파일을 지운다.
 * <ul>
 *   <li><b>바꾸기 전 · 지운 로고의 파일</b>은 커밋된 뒤에 지운다 — 먼저 지웠다가 저장이 되돌려지면 주최자가 없는 파일을 가리킨다.</li>
 *   <li><b>새로 올린 파일</b>은 되돌려지면 지운다 — 아무도 가리키지 않는 파일이 남지 않게.</li>
 * </ul>
 * 트랜잭션이 끝난 뒤(afterCompletion)에 지운다. 지우다 실패해도 이미 끝난 요청의 결과는 바뀌지 않는다 — Spring이 실패를 오류 로그로 남기고,
 * 아무도 가리키지 않는 파일 하나가 저장소에 남는다. 트랜잭션 안에서만 부른다(밖에서 부르면 IllegalStateException).
 */
final class LogoFileCleanup {

    private LogoFileCleanup() {
    }

    /** 이 트랜잭션이 커밋되면 그 로고 파일을 지운다. */
    static void deleteAfterCommit(OrganizerLogoStoragePort logoStorage, OrganizerLogo logo) {
        deleteWhen(TransactionSynchronization.STATUS_COMMITTED, logoStorage, logo);
    }

    /** 이 트랜잭션이 되돌려지면 그 로고 파일을 지운다. */
    static void deleteOnRollback(OrganizerLogoStoragePort logoStorage, OrganizerLogo logo) {
        deleteWhen(TransactionSynchronization.STATUS_ROLLED_BACK, logoStorage, logo);
    }

    private static void deleteWhen(int completionStatus, OrganizerLogoStoragePort logoStorage, OrganizerLogo logo) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == completionStatus) {
                    logoStorage.delete(logo);
                }
            }
        });
    }
}
