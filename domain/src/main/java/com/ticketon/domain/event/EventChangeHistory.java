package com.ticketon.domain.event;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.organizer.StaffRole;
import com.ticketon.domain.policy.RecordRetentionPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 제목 · 소개 · 포스터 · 회차 출연의 변경 이력 규칙 — 무엇을 언제 쌓고, 언제 지우는가(docs/03 "제목 · 소개 · 포스터는 변경 이력을 남깁니다",
 * "보관은 행사가 끝난 날부터 6개월", docs/02 "출연 (선택)").
 * <p>
 * 예매가 열린 뒤에도 이 칸들은 자유롭게 바꾸지만 <b>덮어쓰면 안 된다</b>. 주요 출연자 교체는 환불 사유인데(소비자분쟁해결기준) 지금 버전만 남아
 * 있으면 관객이 무엇을 보고 샀는지 확인할 방법이 없다. 주최자가 [출연자 변경 알림]을 보내지 않아 관객이 신고하면 관리자가 이 이력으로 확인한다.
 * 그래서 버전마다 유효 기간과 함께 쌓아({@link EventChangeHistoryVersion}) 예매 시각과 대조한다.
 * <table>
 *   <caption>언제 무엇을 쌓나</caption>
 *   <tr><th>때</th><th>쌓는 것</th><th></th></tr>
 *   <tr><td>판매를 시작해 판매 대기로 넘어간 순간(공개된 순간)</td><td>그때의 제목 · 소개 · 포스터와 출연이 적힌 회차의 출연 — 첫 버전</td>
 *       <td>{@link #firstVersions}</td></tr>
 *   <tr><td>그 뒤 바꿀 때마다</td><td>바뀐 칸마다 새 버전</td><td>{@link #basicInformationVersions}, {@link #posterVersion},
 *       {@link #performersVersion}</td></tr>
 *   <tr><td>작성 중에 바꿀 때</td><td>쌓지 않는다</td><td>{@link #isRecorded}</td></tr>
 * </table>
 * <b>작성 중의 변경은 쌓지 않는다</b>(확인 필요 #46 — 문서는 예매가 열린 뒤의 변경을 말하고 작성 중을 따로 적지 않았다). 공개 전의 내용은 관객이 본
 * 적이 없어 "보고 샀다"는 다툼이 생기지 않고, 공개된 순간의 내용을 첫 버전으로 둔다. 블록 이름은 대상이 아니다({@link EventChangeHistoryTarget}).
 * <p>
 * <b>운영자가 제목을 바꾸면 소유자에게 알린다</b>({@link #notifiesOwner}) — 승인이 아니라 알림이다. 소유자 모르게 바뀌는 일을 막되 승인으로 막지는
 * 않는다(docs/03 "제목은 운영진 누구나, 몇 번이든 고칠 수 있습니다", docs/99 D "운영자의 제목 변경").
 * <p>
 * <b>보관은 행사가 끝난 날부터 6개월</b>({@link RecordRetentionPolicy#CHANGE_HISTORY_RETENTION_AFTER_EVENT_END}, {@link #isPurgeDue}) — 전자상거래법
 * 시행령의 표시 · 광고 기록이다. 바꾼 날부터 세면 몇 달 전에 예매한 공연은 공연 전에 기록이 지워진다(docs/03 "6개월을 바꾼 날부터 세면 구멍이
 * 생깁니다", docs/99 D "변경 이력 보관 기준점").
 */
public final class EventChangeHistory {

    private EventChangeHistory() {
    }

    /**
     * 이 행사의 변경을 이력에 쌓는지 — 판매를 시작한 뒤(작성 중이 아님)만이다. 판매를 시작한 행사는 다시 작성 중으로 돌아가지 않는다.
     */
    public static boolean isRecorded(Event event) {
        Objects.requireNonNull(event, "event");
        return event.status().hasStartedSales();
    }

    /**
     * 판매를 시작해 판매 대기로 넘어간 순간(공개된 순간)의 첫 버전들 — 제목 · 소개 · 포스터, 그리고 출연이 적힌 회차마다 그 출연. 판매를 시작하는
     * 흐름이 행사를 판매 대기로 바꾼 뒤(곧바로 판매 중이 됐어도) 부른다.
     * <p>
     * 출연이 비어 있는 회차는 첫 버전을 쌓지 않는다 — 비어 있는 출연은 보인 것이 없다. 그 회차에 나중에 출연을 적으면 바꾸기 전이 빈 새 버전이
     * 쌓여, 그 전에는 출연이 보이지 않았다는 것이 드러난다. 판매를 시작한 뒤 더한 회차도 같다.
     *
     * @param schedule 이 행사의 일정
     * @return 대상 칸 차례(제목 · 소개 · 포스터 · 회차 출연), 회차 출연은 일정의 회차 차례
     * @throws IllegalStateException    판매를 시작하지 않았거나 포스터가 없으면(판매 시작 직전 검증이 막는다), 저장 전 행사 · 회차면
     * @throws IllegalArgumentException 다른 행사의 일정이면
     */
    public static List<EventChangeHistoryVersion> firstVersions(Event event, EventSchedule schedule) {
        Objects.requireNonNull(schedule, "schedule");
        long eventId = savedIdOf(event);
        if (schedule.eventId() != eventId) {
            throw new IllegalArgumentException("다른 행사의 일정입니다: 행사 " + eventId + ", 일정의 행사 " + schedule.eventId());
        }
        if (!isRecorded(event)) {
            throw new IllegalStateException("판매를 시작하지 않은 행사는 첫 버전을 쌓지 않습니다: 행사 " + eventId);
        }
        EventPoster poster = event.poster()
                .orElseThrow(() -> new IllegalStateException("포스터 없이 판매를 시작한 행사입니다: 행사 " + eventId));
        EventBasicInformation basicInformation = event.basicInformation();
        List<EventChangeHistoryVersion> versions = new ArrayList<>();
        versions.add(EventChangeHistoryVersion.first(EventChangeHistoryTarget.TITLE, basicInformation.title().value()));
        versions.add(EventChangeHistoryVersion.first(EventChangeHistoryTarget.INTRODUCTION, basicInformation.introduction()));
        versions.add(EventChangeHistoryVersion.first(EventChangeHistoryTarget.POSTER, poster.fileKey()));
        for (EventSession session : schedule.sessions()) {
            long sessionId = session.id().orElseThrow(() -> new IllegalStateException("저장 전 회차입니다: 행사 " + eventId));
            session.performers().ifPresent(performers ->
                    versions.add(EventChangeHistoryVersion.firstOfSession(sessionId, performers.value())));
        }
        return List.copyOf(versions);
    }

    /**
     * 기본 정보를 바꾼 뒤 쌓을 새 버전 — 제목 · 소개 중 바뀐 것마다. 장소 · 유형 · 카테고리 · 상세 안내 등은 대상이 아니다. 작성 중이면 없다.
     *
     * @param event  기본 정보를 바꾼 뒤의 행사
     * @param before 바꾸기 전 기본 정보
     * @return 제목 · 소개 차례
     */
    public static List<EventChangeHistoryVersion> basicInformationVersions(Event event, EventBasicInformation before) {
        Objects.requireNonNull(before, "before");
        if (!isRecorded(event)) {
            return List.of();
        }
        EventBasicInformation after = event.basicInformation();
        List<EventChangeHistoryVersion> versions = new ArrayList<>();
        if (!before.title().equals(after.title())) {
            versions.add(new EventChangeHistoryVersion(EventChangeHistoryTarget.TITLE, null, before.title().value(),
                    after.title().value()));
        }
        if (!before.introduction().equals(after.introduction())) {
            versions.add(new EventChangeHistoryVersion(EventChangeHistoryTarget.INTRODUCTION, null, before.introduction(),
                    after.introduction()));
        }
        return List.copyOf(versions);
    }

    /**
     * 포스터를 바꾼 뒤 쌓을 새 버전. 작성 중이면 없다.
     * <p>
     * 이 버전이 있으면 <b>바꾸기 전 포스터의 파일을 지우지 않는다</b> — 이력의 근거라 보관 기간 동안 남기고, 이력을 지울 때 함께 지운다. 작성 중에
     * 바꾼 포스터는 이력이 없어 바로 지운다.
     *
     * @param event  포스터를 바꾼 뒤의 행사
     * @param before 바꾸기 전 포스터. 없었으면 빈 값
     * @throws IllegalStateException 판매를 시작한 행사인데 포스터가 없으면
     */
    public static Optional<EventChangeHistoryVersion> posterVersion(Event event, Optional<EventPoster> before) {
        Objects.requireNonNull(before, "before");
        if (!isRecorded(event)) {
            return Optional.empty();
        }
        EventPoster after = event.poster()
                .orElseThrow(() -> new IllegalStateException("포스터가 없는 행사입니다: 행사 " + savedIdOf(event)));
        if (before.equals(Optional.of(after))) {
            return Optional.empty();
        }
        return Optional.of(new EventChangeHistoryVersion(EventChangeHistoryTarget.POSTER, null,
                before.map(EventPoster::fileKey).orElse(null), after.fileKey()));
    }

    /**
     * 회차 출연을 바꾼 뒤 쌓을 새 버전. 작성 중이면 없다. 출연 칸을 고치는 것만으로는 출연자 변경 알림이 되지 않는다(docs/02) — 이력만 쌓는다.
     *
     * @param event     출연을 바꾼 행사
     * @param schedule  출연을 바꾼 뒤의 일정
     * @param sessionId 출연을 바꾼 회차
     * @param before    바꾸기 전 출연. 비어 있었으면 빈 값
     * @throws com.ticketon.domain.error.NotFoundException 일정에 없는 회차면({@link EventErrorCode#SESSION_NOT_FOUND})
     */
    public static Optional<EventChangeHistoryVersion> performersVersion(Event event, EventSchedule schedule, long sessionId,
            Optional<SessionPerformers> before) {
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(before, "before");
        if (!isRecorded(event)) {
            return Optional.empty();
        }
        Optional<SessionPerformers> after = schedule.session(sessionId).performers();
        if (after.equals(before)) {
            return Optional.empty();
        }
        return Optional.of(new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, sessionId,
                before.map(SessionPerformers::value).orElse(null), after.map(SessionPerformers::value).orElse(null)));
    }

    /**
     * 이 버전을 쌓을 때 소유자에게 '운영자가 행사 제목을 바꿈'을 알리는지 — <b>운영자가 제목을 바꿨을 때</b>(docs/12 "주최자 대상 — 운영자가 행사
     * 제목을 바꿈 (소유자에게)"). 소유자가 직접 바꾸면 알리지 않는다. 첫 버전은 바꾼 것이 아니라 알리지 않는다. 버전을 쌓지 않는 작성 중의 변경도
     * 알리지 않는다(확인 필요 #46 — 공개 전 제목은 관객에게 보인 적이 없다).
     *
     * @param changerRole 바꾼 운영진의 그때 역할
     */
    public static boolean notifiesOwner(EventChangeHistoryVersion version, StaffRole changerRole) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(changerRole, "changerRole");
        return version.target() == EventChangeHistoryTarget.TITLE && version.previousContent() != null
                && changerRole == StaffRole.OPERATOR;
    }

    /**
     * 이력을 보관하는 마지막 날(한국 날짜) — 행사가 끝난 날부터 {@link RecordRetentionPolicy#CHANGE_HISTORY_RETENTION_AFTER_EVENT_END}(6개월).
     * 10/14에 끝난 행사는 다음 해 4/14까지 보관하고 4/15부터 지운다. 달의 끝을 넘는 날은 그 달의 마지막 날이다(8/31에 끝나면 2/28 또는 2/29까지).
     *
     * @param eventEndsAt 행사 종료 시각 — 취소되지 않은 마지막 회차의 끝({@link EventSchedule#eventEndsAt})
     */
    public static LocalDate retainedThrough(Instant eventEndsAt) {
        Objects.requireNonNull(eventEndsAt, "eventEndsAt");
        return KoreanTime.toDate(eventEndsAt).plus(RecordRetentionPolicy.CHANGE_HISTORY_RETENTION_AFTER_EVENT_END);
    }

    /**
     * 이 행사의 이력을 지울 때인지 — 오늘(한국 날짜)이 보관 마지막 날({@link #retainedThrough})을 지났다. 행사가 끝난 날은 행사 상태가 아니라 일정으로
     * 본다 — 취소되지 않은 마지막 회차가 끝난 날이다(docs/04 "'마지막 회차'는 취소되지 않은 회차 중 마지막").
     * <p>
     * <b>행사가 끝난 날을 알 수 없으면 지우지 않는다</b> — 회차가 없거나 모두 취소됐으면(취소됨), 마지막 회차의 종료 시각이 비어 있으면(행사 종료를 판정할
     * 수 없음) 보관을 이어 간다(확인 필요 #46 — 전부 취소된 행사의 보관 기준점은 문서에 없어, 법정 기간보다 일찍 지우지 않는 쪽으로 정했다).
     *
     * @param schedule 이 행사의 일정
     * @param now      지금
     */
    public static boolean isPurgeDue(EventSchedule schedule, Instant now) {
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(now, "now");
        return schedule.eventEndsAt()
                .map(eventEndsAt -> KoreanTime.toDate(now).isAfter(retainedThrough(eventEndsAt)))
                .orElse(false);
    }

    /**
     * 이력과 함께 지울 포스터 파일 — 지운 포스터 버전의 바꾸기 전 포스터들. 판매를 시작한 뒤 바꾼 포스터의 이전 파일은 이력의 근거라 보관 기간 동안
     * 남겨 두었다({@link #posterVersion}). 지금 포스터는 들지 않는다 — 포스터는 바꿀 때마다 새 열쇠를 받아, 한 번 바뀐(바꾸기 전이 된) 포스터가 다시
     * 지금 포스터가 되는 일이 없다.
     *
     * @param deletedVersions 지운 이력의 버전
     * @return 겹치지 않게, 지운 차례
     */
    public static List<EventPoster> replacedPosters(List<EventChangeHistoryVersion> deletedVersions) {
        Objects.requireNonNull(deletedVersions, "deletedVersions");
        return deletedVersions.stream()
                .filter(version -> version.target() == EventChangeHistoryTarget.POSTER && version.previousContent() != null)
                .map(version -> new EventPoster(version.previousContent()))
                .distinct()
                .toList();
    }

    private static long savedIdOf(Event event) {
        Objects.requireNonNull(event, "event");
        return event.id().orElseThrow(() -> new IllegalStateException("저장 전 행사입니다"));
    }
}
