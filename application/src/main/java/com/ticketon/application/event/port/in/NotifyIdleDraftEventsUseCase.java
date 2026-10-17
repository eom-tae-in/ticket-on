package com.ticketon.application.event.port.in;

/**
 * 작성 중 행사 30일 미수정 알림 — 들어오는 포트. 정해진 시각 작업이 매일 부른다(docs/04 "작성 중 행사 관리", docs/12 "주최자 대상 — 작성 중 행사
 * 30일 미수정").
 * <ul>
 *   <li>대상 — 활동 중인 주최자의 지우지 않은 작성 중 행사 가운데 마지막 수정 뒤로 30일이 지난 것.</li>
 *   <li>받는 사람 — 그 주최자의 소유자와 운영자 전원, 이메일([주최]).</li>
 *   <li><b>같은 미수정 구간에는 한 번만</b> — 다시 불러도, 두 벌이 동시에 돌아도 같은 구간에는 다시 보내지 않는다. 고치면 새 구간이 시작되고 다시
 *       30일이 지나면 또 보낸다.</li>
 *   <li>자동 삭제는 하지 않는다 — 알리기만 한다(docs/99 B "작성 중 행사 자동 삭제").</li>
 * </ul>
 * 지금 시각은 서비스의 시계로 정한다.
 */
public interface NotifyIdleDraftEventsUseCase {

    /**
     * 행사마다 따로 처리한다 — 한 행사에서 실패해도 나머지 행사는 처리하고, 실패한 행사는 기록이 남지 않아 다음에 다시 시도한다.
     *
     * @throws IllegalStateException 처리하지 못한 행사가 있으면 — 나머지를 모두 처리한 뒤 실패한 행사 id와 그 원인을 담아 던진다
     */
    NotifyIdleDraftEventsResult notifyIdleDraftEvents();
}
