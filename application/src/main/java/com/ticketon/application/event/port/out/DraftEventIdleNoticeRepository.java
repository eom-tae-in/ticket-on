package com.ticketon.application.event.port.out;

import com.ticketon.application.event.query.IdleDraftEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 작성 중 행사 30일 미수정 알림 저장소 — 나가는 포트. 알릴 행사를 찾고, 어느 미수정 구간에 알렸는지 남긴다(docs/04 "작성 중 행사 관리", docs/12
 * "주최자 대상 — 작성 중 행사 30일 미수정").
 * <p>
 * <b>미수정 구간은 행사의 마지막 수정 시각으로 가린다</b>(도메인 {@code DraftEventNudges}). 알림 기록은 (행사, 그 구간의 마지막 수정 시각)마다
 * 하나뿐이다 — 정해진 시각 작업이 다시 돌거나 두 벌이 동시에 돌아도 저장소가 한 번만 받는다.
 * <p>
 * 대상은 <b>활동 중인 주최자의 지우지 않은 작성 중 행사</b>다. 닫힘 · 영구 정지된 주최자는 운영진이 전원 빠져 받을 사람이 없고, 지운 행사는 없는
 * 행사다(docs/01 "작성 중 · 삭제된 행사 — 없는 행사").
 */
public interface DraftEventIdleNoticeRepository {

    /**
     * 알릴 때가 됐을 수 있는 행사 id — 대상 행사 가운데 마지막 수정이 그 상한 이전(같음 포함)이고, 그 마지막 수정 시각의 구간에 아직 알리지 않은
     * 것. 행사 id 차례. 잠그지 않고 읽으므로, 알리기 전에 {@link #lockDraftEvent}로 다시 읽는다.
     *
     * @param idleCutoff 미수정 알림을 보낼 마지막 수정 시각의 상한(도메인 {@code DraftEventNudges#idleNoticeCutoff})
     */
    List<Long> findNoticeCandidates(Instant idleCutoff);

    /**
     * 대상 행사를 읽고, 이 트랜잭션이 끝날 때까지 그 행사 줄을 FOR KEY SHARE로 잡는다. 같은 행사의 위저드 저장(행사 줄 FOR UPDATE)이 진행 중이면
     * 그 저장이 끝날 때까지 기다렸다가 바뀐 마지막 수정 시각을 읽는다 — 막 고친 행사에 묵은 구간의 알림을 보내지 않는다.
     * <p>
     * <b>잠금 순서 — 행사 줄만 잡는다</b>(organizer 서비스의 {@code StaffAccountLocks} 표). 운영진 계정 줄 · 주최자 줄을 잡지 않고, 이 줄을 쥔 채 그
     * 줄들을 기다리지도 않는다.
     *
     * @return 활동 중인 주최자의 지우지 않은 작성 중 행사가 아니면(그사이 판매를 시작했거나 지웠거나 주최자가 닫혔으면) 비어 있다
     */
    Optional<IdleDraftEvent> lockDraftEvent(long eventId);

    /**
     * 그 행사의 그 미수정 구간에 알렸다고 남긴다.
     *
     * @param lastModifiedAt 알린 구간의 마지막 수정 시각
     * @param notifiedAt     알린 시각 — 구간이 시작된 뒤다
     * @return 새로 남겼는지 — 이미 그 구간에 알린 기록이 있으면 아무것도 바꾸지 않고 false. 다른 트랜잭션이 같은 구간을 막 남기는 중이면 그
     *         트랜잭션이 끝날 때까지 기다렸다가 판단한다
     */
    boolean recordNotice(long eventId, Instant lastModifiedAt, Instant notifiedAt);
}
