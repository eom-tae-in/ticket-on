package com.ticketon.domain.event;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.policy.TimePolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 작성 중 행사를 정리하게 이끄는 유도 장치의 규칙(docs/04 "작성 중 행사 관리").
 * <p>
 * <b>작성 중 행사는 개수 제한도 자동 삭제도 없다</b> — 여러 행사를 동시에 준비하는 건 정상이고, 작업물을 시스템이 지우면 주최자가 크게 화를 낸다
 * (docs/99 B "작성 중 행사 개수 제한", "작성 중 행사 자동 삭제"). 막는 장치가 없으니 유도 장치가 일한다.
 * <table>
 *   <caption>시점과 처리 (docs/04)</caption>
 *   <tr><th>시점</th><th>처리</th><th></th></tr>
 *   <tr><td>30일 미수정({@link TimePolicy#DRAFT_EVENT_IDLE_NOTICE})</td><td>알림</td><td>{@link #isIdleNoticeDue}</td></tr>
 *   <tr><td>회차 날짜가 이미 지남</td><td>목록에서 접어둠 + 정리 안내</td><td>{@link #isFolded}</td></tr>
 *   <tr><td>그 이후</td><td>그대로 보관</td><td>지우는 규칙이 없다</td></tr>
 * </table>
 * 진행률 · 마지막 수정일 · 멈춘 단계로 가는 버튼은 행사의 작성 진행 단계({@link Event#wizardProgress}, 이어서 할 단계는
 * {@link EventWizardStep#next})와 마지막 수정 시각({@link Event#lastModifiedAt})이 보인다.
 */
public final class DraftEventNudges {

    private DraftEventNudges() {
    }

    /**
     * 미수정 알림을 보낼 마지막 수정 시각의 상한 — 마지막 수정이 이 순간이거나 그 전이면 {@link TimePolicy#DRAFT_EVENT_IDLE_NOTICE}(30일) 동안
     * 수정이 없었다. 지금(한국 시각)에서 30일을 달력으로 거슬러 올라간 순간이다 — 9/14 15:00에 마지막으로 고쳤으면 10/14 15:00부터 알릴 때다.
     */
    public static Instant idleNoticeCutoff(Instant now) {
        Objects.requireNonNull(now, "now");
        return KoreanTime.toInstant(KoreanTime.toDateTime(now).minus(TimePolicy.DRAFT_EVENT_IDLE_NOTICE));
    }

    /**
     * 30일 미수정 알림을 보낼 때인지 — 마지막 수정 뒤로 30일이 꼬박 지났다(docs/04, docs/12 "주최자 대상 — 작성 중 행사 30일 미수정"). 29일
     * 23시간 59분은 아직 아니다.
     * <p>
     * <b>같은 미수정 구간에는 한 번만 알린다</b> — 구간은 마지막 수정 시각으로 가린다. 알린 뒤에도 고치지 않으면 마지막 수정 시각이 그대로라 같은
     * 구간이고, 고치면 마지막 수정 시각이 바뀌어 새 구간이 시작되며 다시 30일이 지나면 또 알린다. 어느 구간에 알렸는지는 알림을 보내는 쪽이 마지막
     * 수정 시각과 함께 남긴다.
     *
     * @param lastModifiedAt 작성 중 행사의 마지막 수정 시각({@link Event#lastModifiedAt})
     * @param now            지금
     */
    public static boolean isIdleNoticeDue(Instant lastModifiedAt, Instant now) {
        Objects.requireNonNull(lastModifiedAt, "lastModifiedAt");
        return !lastModifiedAt.isAfter(idleNoticeCutoff(now));
    }

    /**
     * 작성 중 목록에서 접어 둘지 — 일정의 날짜가 모두 지났다(docs/04 "회차 날짜가 이미 지남 — 목록에서 접어둠 + 정리 안내"). 그 날짜로는 판매를
     * 시작할 수 없으니 눈앞에서 치우고 정리를 안내하되, 지우지는 않는다(그 이후에도 그대로 보관).
     * <ul>
     *   <li><b>모든 날짜가 오늘(한국 날짜) 전일 때만 접는다</b> — 마지막 날짜가 지났을 때다. 앞 날짜만 지나고 남은 날짜가 있으면 아직 그 날짜로
     *       준비할 수 있어 접지 않는다. 오늘인 날짜는 지나지 않은 것으로 본다(확인 필요 #45 — 문서가 첫 날짜인지 마지막 날짜인지 정하지 않아, 덜
     *       치우는 쪽으로 정했다).</li>
     *   <li><b>날짜가 하나도 없으면 접지 않는다</b> — 아직 2단계(회차)를 저장하지 않은 행사는 지난 날짜가 없다.</li>
     * </ul>
     *
     * @param lastScheduleDate 일정의 마지막 날짜(지우지 않은 날짜 중 가장 늦은 날). 날짜가 하나도 없으면 null
     * @param now              지금
     */
    public static boolean isFolded(LocalDate lastScheduleDate, Instant now) {
        Objects.requireNonNull(now, "now");
        return lastScheduleDate != null && lastScheduleDate.isBefore(KoreanTime.toDate(now));
    }
}
