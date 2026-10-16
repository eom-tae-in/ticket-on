package com.ticketon.domain.event;

import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 행사 — 주최자가 여는 하나의 공연 · 전시 · 페스티벌. 주최자에 속한다(docs/00 "용어", docs/14 "무엇이 무엇을 가리키나").
 * <p>
 * 지금은 위저드 1단계의 기본 정보, 5단계의 공개 수준 · 예매 설정 · 취소 규정, 공개 주소를 갖는다.
 * <ul>
 *   <li><b>기본 정보</b>({@link EventBasicInformation}) — 제목 · 유형 · 카테고리 · 소개 · 장소 등. 행사를 만들 때 함께 받는다 — 위저드는
 *       단계를 넘길 때마다 저장하므로(docs/03 "임시저장은 자동으로") 1단계를 마쳐야 처음 저장된다.</li>
 *   <li><b>포스터</b>({@link EventPoster}) — 1단계의 필수 항목이지만 행사를 만든 뒤 따로 올린다. 그래서 작성 중에는 없을 수 있고, 판매를
 *       시작하기 전에 갖춰야 한다({@link #isBasicInformationComplete}).</li>
 *   <li><b>공개 수준</b>({@link EventVisibilitySetting}) — 공개로 시작한다(docs/04 "행사 복제 — 공개 수준은 기본값 공개로 시작").</li>
 *   <li><b>예매 설정</b>({@link BookingSettings}) — 오픈 시각 · 예매 종료 · 검표 시작 · 매수 제한 · 회차 선택 제약 · 재입장 · 연령 제한.
 *       처음 값으로 시작한다 — 위저드 5단계는 전부 처음 값이 있어 건너뛸 수 있다(docs/03 "행사 만들기 위저드").</li>
 *   <li><b>취소 규정</b>({@link CancellationPolicy}) — 소비자분쟁해결기준 그대로 시작하고, 관객에게 더 유리한 쪽으로만 조정한다(docs/08).</li>
 *   <li><b>공개 주소</b>({@link EventPublicAddress}) — 만들 때 발급하고 바뀌지 않는다. 공개 수준을 바꿔도 같은 링크다.</li>
 * </ul>
 * 화면마다 부르는 이름이 다르다 — 관객 화면은 유형 이름, 주최자 화면은 "행사"({@link #nameOn}).
 * <p>
 * 위저드 2단계의 회차 방식 · 날짜 · 회차는 행사가 아니라 행사의 일정({@link EventSchedule})이 따로 갖는다 — 날짜와 회차가 많고 따로 고친다.
 * <p>
 * <b>행사 상태</b>({@link EventStatus}) — 작성 중으로 시작한다. 사람이 누르는 전환(판매 시작 · 정산 완료 · 남은 회차 전부 취소)은 의도가 드러나는
 * 메서드로, 시간이 정하는 전환(오픈 · 판매 종료 · 행사 종료)은 {@link #advanceByTime}으로 한다. 시간이 정하는 전환은 일정이 알려 주는 마지막 회차의
 * 예매 종료 · 종료 시각({@link EventSchedule#salesEndsAt}, {@link EventSchedule#eventEndsAt})과 이 행사의 오픈 시각을 본다. <b>겹치면 시간이
 * 이긴다</b> — 상태를 바꾸는 메서드는 먼저 지금 시각을 반영한 뒤 판단한다(docs/04 "시간이 운영 판단을 이깁니다").
 * <p>
 * 언제 무엇을 고칠 수 있는지(상태별 수정 범위, 판매 중 취소 규정 잠금, 장소 변경의 관리자 승인)는 상태가 정하지만, 제목 · 소개 · 포스터의 변경
 * 이력은 아직 보지 않는다(docs/03 "변경 이력", #46).
 * <p>
 * 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 행사는 id가 없다.
 */
public final class Event {

    private final Long id;
    private final long organizerId;
    private final EventPublicAddress publicAddress;
    private EventBasicInformation basicInformation;
    private EventPoster poster;
    private EventVisibilitySetting visibility;
    private BookingSettings bookingSettings;
    private CancellationPolicy cancellationPolicy;
    private EventStatus status;

    private Event(Long id, long organizerId, EventPublicAddress publicAddress, EventBasicInformation basicInformation,
            EventPoster poster, EventVisibilitySetting visibility, BookingSettings bookingSettings,
            CancellationPolicy cancellationPolicy, EventStatus status) {
        if (organizerId < 1) {
            throw new IllegalArgumentException("주최자 id는 1 이상이어야 합니다: " + organizerId);
        }
        this.id = id;
        this.organizerId = organizerId;
        this.publicAddress = Objects.requireNonNull(publicAddress, "publicAddress");
        this.basicInformation = Objects.requireNonNull(basicInformation, "basicInformation");
        this.poster = poster;
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.bookingSettings = Objects.requireNonNull(bookingSettings, "bookingSettings");
        this.cancellationPolicy = Objects.requireNonNull(cancellationPolicy, "cancellationPolicy");
        this.status = Objects.requireNonNull(status, "status");
    }

    /**
     * 위저드 1단계를 마친 새 행사를 만든다. 작성 중이고, 포스터는 아직 없고, 공개 수준은 공개, 예매 설정은 처음 값({@link BookingSettings#defaults}),
     * 취소 규정은 기준표 그대로({@link CancellationPolicy#standard})다.
     *
     * @param organizerId      행사를 여는 주최자의 id
     * @param publicAddress    새로 발급한 공개 주소 — 서비스가 TokenGenerator로 만든 값으로 발급한다({@link EventPublicAddress#issue})
     * @param basicInformation 1단계에서 적은 기본 정보
     */
    public static Event create(long organizerId, EventPublicAddress publicAddress, EventBasicInformation basicInformation) {
        return new Event(null, organizerId, publicAddress, basicInformation, null, EventVisibilitySetting.publicByDefault(),
                BookingSettings.defaults(), CancellationPolicy.standard(), EventStatus.DRAFT);
    }

    /**
     * 저장소에서 읽은 값으로 행사를 되살린다.
     *
     * @param poster 포스터. 아직 올리지 않았으면 null
     * @param status 저장된 행사 상태 — 시간이 지나 바뀌었어야 할 상태라도 그대로 되살린다. 지금 시각은 {@link #advanceByTime}이 반영한다
     */
    public static Event restore(long id, long organizerId, EventPublicAddress publicAddress,
            EventBasicInformation basicInformation, EventPoster poster, EventVisibilitySetting visibility,
            BookingSettings bookingSettings, CancellationPolicy cancellationPolicy, EventStatus status) {
        if (id < 1) {
            throw new IllegalArgumentException("행사 id는 1 이상이어야 합니다: " + id);
        }
        return new Event(id, organizerId, publicAddress, basicInformation, poster, visibility, bookingSettings,
                cancellationPolicy, status);
    }

    /**
     * 기본 정보를 바꾼다(위저드 1단계를 다시 저장).
     *
     * @return 바뀌었는지 — 지금과 같으면 아무것도 바꾸지 않고 false
     */
    public boolean changeBasicInformation(EventBasicInformation newBasicInformation) {
        Objects.requireNonNull(newBasicInformation, "newBasicInformation");
        if (newBasicInformation.equals(basicInformation)) {
            return false;
        }
        basicInformation = newBasicInformation;
        return true;
    }

    /**
     * 포스터를 새로 올린 것으로 바꾼다(처음 올리기 포함). 바꾸기 전 포스터의 파일을 정리하는 일은 유스케이스가 한다.
     *
     * @return 바꾸기 전 포스터. 없었으면 빈 값
     */
    public Optional<EventPoster> changePoster(EventPoster newPoster) {
        Objects.requireNonNull(newPoster, "newPoster");
        Optional<EventPoster> previousPoster = Optional.ofNullable(poster);
        poster = newPoster;
        return previousPoster;
    }

    /**
     * 공개 수준을 바꾼다. 암호 보호로 두면서 새 암호를 적지 않으면 걸려 있던 암호를 그대로 쓴다({@link EventVisibilitySetting#change}).
     * 공개 주소는 그대로다.
     *
     * @param newPasswordHash 새로 정한 암호의 해시 — 암호 보호로 두면서 암호를 새로 적었을 때만. 아니면 null
     * @return 바뀌었는지 — 지금과 같으면 false
     * @throws com.ticketon.domain.error.RuleViolationException 암호 보호인데 암호가 없으면({@link EventErrorCode#EVENT_PASSWORD_REQUIRED})
     * @throws IllegalArgumentException                         암호 보호가 아닌데 새 암호 해시를 넘기면
     */
    public boolean changeVisibility(EventVisibility newVisibility, EventPasswordHash newPasswordHash) {
        EventVisibilitySetting changed = visibility.change(newVisibility, newPasswordHash);
        if (changed.equals(visibility)) {
            return false;
        }
        visibility = changed;
        return true;
    }

    /**
     * 예매 설정을 바꾼다(위저드 5단계를 다시 저장).
     *
     * @return 바뀌었는지 — 지금과 같으면 아무것도 바꾸지 않고 false
     */
    public boolean changeBookingSettings(BookingSettings newBookingSettings) {
        Objects.requireNonNull(newBookingSettings, "newBookingSettings");
        if (newBookingSettings.equals(bookingSettings)) {
            return false;
        }
        bookingSettings = newBookingSettings;
        return true;
    }

    /**
     * 취소 규정을 바꾼다(위저드 5단계의 취소 규정 조정). 기준보다 불리한 규정은 만들 때 이미 막혔다({@link CancellationPolicy#of}).
     * 기준표로 되돌리려면 {@link CancellationPolicy#standard}를 넘긴다.
     *
     * @return 바뀌었는지 — 지금과 같으면 false
     */
    public boolean changeCancellationPolicy(CancellationPolicy newCancellationPolicy) {
        Objects.requireNonNull(newCancellationPolicy, "newCancellationPolicy");
        if (newCancellationPolicy.equals(cancellationPolicy)) {
            return false;
        }
        cancellationPolicy = newCancellationPolicy;
        return true;
    }

    /**
     * 판매를 시작한다(위저드 7단계의 "판매 시작") — 작성 중에서 판매 대기로 넘어가고, 곧바로 지금 시각을 반영한다. 오픈 시각을 비워 두었거나 이미
     * 지났으면 그대로 판매 중이 된다(docs/04 "판매 대기 → 판매 중 — 자동(오픈 시각 도달)", {@link BookingSettings}).
     * <p>
     * 판매 시작 직전 검증(빠진 항목 · 오픈 시각이 예매 종료보다 앞서는지 등)은 부르는 쪽이 먼저 한다(docs/03 "판매 시작 직전에 검증", #58).
     *
     * @param schedule        이 행사의 일정 — 오픈 뒤 판매 종료 · 행사 종료 시각을 읽는다
     * @param newSalesBlocked 주최자가 신규 개설 차단(정지 1단계 이상) 중인지 — 정지 1단계는 작성 중 행사의 판매 시작을 막는다(docs/01 "정지
     *                        4단계"). 정지 단계는 주최자의 정지 기록으로 계산하므로 정지 기능(#122)이 계산해 넘긴다
     * @param now             지금 시각
     * @throws RuleViolationException 이미 판매를 시작했으면({@link EventErrorCode#SALES_ALREADY_STARTED}), 주최자가 신규 개설 차단 중이면
     *                                ({@link EventErrorCode#SALES_START_BLOCKED_BY_SUSPENSION})
     */
    public void startSales(EventSchedule schedule, boolean newSalesBlocked, Instant now) {
        requireOwnSchedule(schedule);
        Objects.requireNonNull(now, "now");
        if (status != EventStatus.DRAFT) {
            throw new RuleViolationException(EventErrorCode.SALES_ALREADY_STARTED, detail());
        }
        if (newSalesBlocked) {
            throw new RuleViolationException(EventErrorCode.SALES_START_BLOCKED_BY_SUSPENSION, detail());
        }
        status = EventStatus.AWAITING_OPEN;
        advanceByTime(schedule, now);
    }

    /**
     * 지금 시각이 정하는 상태 — 저장된 상태에 시간을 반영해 계산한다. 이 행사는 바꾸지 않는다(바꾸려면 {@link #advanceByTime}).
     * <p>
     * 시간이 움직이는 상태({@link EventStatus#movesWithTime})만 시간을 본다. 오픈 시각 · 마지막 회차의 예매 종료 · 행사 종료 시각을 한 번에 보고
     * 가장 늦게 도달한 단계로 간다 — 오픈 전에 예매 종료까지 지났으면 판매 중을 건너뛰고 판매 종료다.
     * <ul>
     *   <li>행사 종료 시각({@link EventSchedule#eventEndsAt})이 지났으면 행사 종료. 마지막 회차의 종료 시각이 비어 있으면 행사 종료를 판정할 수 없어
     *       판매 종료에 머문다(docs/04 "회차 종료 시각이 없으면 행사 종료를 판정할 수 없습니다").</li>
     *   <li>판매 종료 시각({@link EventSchedule#salesEndsAt})이 지났으면 판매 종료 — 판매 중단 중이어도 그렇다(docs/04 "시간이 운영 판단을
     *       이깁니다").</li>
     *   <li>판매 대기는 오픈 시각이 지났으면(비워 두었으면 곧바로) 판매 중.</li>
     * </ul>
     * 시각은 그 순간부터 지난 것으로 본다. <b>뒤로 가지 않는다</b> — 판매 종료 뒤 일정이 바뀌어 판매 종료 시각이 늦어져도 판매 중으로 되돌리지
     * 않는다. 마지막 회차가 없으면(회차가 없거나 모두 취소) 판매 종료 · 행사 종료 시각이 없어 시간으로는 넘어가지 않는다.
     */
    public EventStatus statusAt(EventSchedule schedule, Instant now) {
        requireOwnSchedule(schedule);
        Objects.requireNonNull(now, "now");
        if (!status.movesWithTime()) {
            return status;
        }
        if (hasPassed(schedule.eventEndsAt(), now)) {
            return EventStatus.ENDED;
        }
        if (hasPassed(schedule.salesEndsAt(bookingSettings), now)) {
            return EventStatus.SALES_CLOSED;
        }
        if (status == EventStatus.AWAITING_OPEN && isBookingOpenAt(now)) {
            return EventStatus.ON_SALE;
        }
        return status;
    }

    /**
     * 지금 시각을 상태에 반영한다({@link #statusAt}) — 정해진 시각에 도는 상태 전환 작업이 부르고(#59), 상태를 바꾸는 메서드도 판단하기 전에
     * 부른다.
     *
     * @return 상태가 바뀌었는지
     */
    public boolean advanceByTime(EventSchedule schedule, Instant now) {
        EventStatus advanced = statusAt(schedule, now);
        if (advanced == status) {
            return false;
        }
        status = advanced;
        return true;
    }

    /**
     * 정산 완료로 넘긴다(관리자). 행사 종료 또는 취소됨에서만 한다 — 취소된 행사도 정산을 거친다(docs/04 "취소된 행사도 정산을 거칩니다").
     * 먼저 지금 시각을 반영한다. 확인이 끝나지 않은 불가항력 배상이 있으면 정산 완료를 누를 수 없다는 규칙은 정산(#114)이 본다.
     *
     * @throws RuleViolationException 행사 종료 · 취소됨이 아니면({@link EventErrorCode#SETTLEMENT_NOT_AVAILABLE})
     */
    public void completeSettlement(EventSchedule schedule, Instant now) {
        advanceByTime(schedule, now);
        if (status != EventStatus.ENDED && status != EventStatus.CANCELLED) {
            throw new RuleViolationException(EventErrorCode.SETTLEMENT_NOT_AVAILABLE, detail());
        }
        status = EventStatus.SETTLED;
    }

    /**
     * 회차 취소를 행사 상태에 반영한다 — 회차 취소 · 행사 취소 · 정지 3단계가 회차를 취소한 뒤(같은 트랜잭션에서) 부른다(#98, #99, #123).
     * <ul>
     *   <li><b>회차가 모두 취소됐으면 취소됨</b> — 공연한 회차 없이 전부 취소했다(docs/04 "남은 회차가 없어지면 — 아직 열린 회차가 없음").</li>
     *   <li>취소되지 않은 회차가 남았으면 지금 시각을 반영한다. 남은 회차가 모두 이미 공연한 회차라면 그 마지막 회차가 끝났으므로 <b>행사 종료</b>가
     *       된다(docs/04 "이미 열린 회차가 있음 → 행사 종료"). 공연한 회차가 아직 진행 중이면 판매 종료에 머물다 그 회차가 끝날 때 행사 종료가
     *       된다 — 행사 종료는 마지막 회차가 끝난 때다. 시작하지 않은 회차가 남았으면 그대로이되, 마지막 회차가 취소됐으면 판매 종료 시각이 앞당겨진다.</li>
     * </ul>
     * 회차는 지우지 않고 취소 시각으로 남으므로 일정의 회차가 모두 취소됐는지로 "공연한 회차가 없음"을 안다. 지운 회차는 일정에 남지 않는다.
     *
     * @return 상태가 바뀌었는지
     * @throws RuleViolationException 회차를 취소할 수 있는 상태(판매 대기 · 판매 중 · 판매 중단 · 판매 종료)가 아니면
     *                                ({@link EventErrorCode#EVENT_NOT_CANCELLABLE})
     */
    public boolean reflectSessionCancellation(EventSchedule schedule, Instant now) {
        requireOwnSchedule(schedule);
        Objects.requireNonNull(now, "now");
        if (!status.movesWithTime()) {
            throw new RuleViolationException(EventErrorCode.EVENT_NOT_CANCELLABLE, detail());
        }
        if (allSessionsCancelled(schedule)) {
            status = EventStatus.CANCELLED;
            return true;
        }
        return advanceByTime(schedule, now);
    }

    /**
     * 위저드 1단계의 필수 항목이 모두 찼는지. 기본 정보는 저장할 때 이미 필수 항목을 확인했으므로 따로 올리는 포스터만 빠질 수 있다.
     * 판매를 시작하기 전에 확인한다(docs/03 "판매 시작 직전에 검증").
     */
    public boolean isBasicInformationComplete() {
        return poster != null;
    }

    /** 그 화면에서 이 행사를 부르는 이름 — 관객 화면은 유형 이름("공연" · "전시" · "페스티벌"), 주최자 화면은 "행사"(docs/00 "용어"). */
    public String nameOn(ScreenSide screenSide) {
        return basicInformation.type().nameOn(screenSide);
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public long organizerId() {
        return organizerId;
    }

    public EventPublicAddress publicAddress() {
        return publicAddress;
    }

    public EventBasicInformation basicInformation() {
        return basicInformation;
    }

    /** 아직 올리지 않았으면 비어 있다. */
    public Optional<EventPoster> poster() {
        return Optional.ofNullable(poster);
    }

    public EventVisibilitySetting visibility() {
        return visibility;
    }

    public BookingSettings bookingSettings() {
        return bookingSettings;
    }

    public CancellationPolicy cancellationPolicy() {
        return cancellationPolicy;
    }

    /** 저장된 행사 상태. 시간이 지나 바뀌었어야 할 상태일 수 있다 — 지금 시각이 정하는 상태는 {@link #statusAt}. */
    public EventStatus status() {
        return status;
    }

    private boolean isBookingOpenAt(Instant now) {
        Instant opensAt = bookingSettings.bookingOpensAt();
        return opensAt == null || !now.isBefore(opensAt);
    }

    private static boolean hasPassed(Optional<Instant> moment, Instant now) {
        return moment.filter(instant -> !now.isBefore(instant)).isPresent();
    }

    private static boolean allSessionsCancelled(EventSchedule schedule) {
        List<EventSession> sessions = schedule.sessions();
        return !sessions.isEmpty() && sessions.stream().allMatch(EventSession::isCancelled);
    }

    private void requireOwnSchedule(EventSchedule schedule) {
        Objects.requireNonNull(schedule, "schedule");
        if (id == null) {
            throw new IllegalStateException("저장 전 행사는 일정이 없습니다");
        }
        if (schedule.eventId() != id) {
            throw new IllegalArgumentException("행사 " + id + "에 다른 행사(" + schedule.eventId() + ")의 일정을 넘겼습니다");
        }
    }

    private String detail() {
        return "행사 " + id + ", 상태: " + status;
    }
}
