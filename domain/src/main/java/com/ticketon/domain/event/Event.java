package com.ticketon.domain.event;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.StaffAction;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * 행사 — 주최자가 여는 하나의 공연 · 전시 · 페스티벌. 주최자에 속한다(docs/00 "용어", docs/14 "무엇이 무엇을 가리키나").
 * <p>
 * 위저드 1단계의 기본 정보, 5단계의 공개 수준 · 예매 설정 · 취소 규정, 공개 주소와 행사 상태 · 판매 중단 이력을 갖는다.
 * <ul>
 *   <li><b>기본 정보</b>({@link EventBasicInformation}) — 제목 · 유형 · 카테고리 · 소개 · 장소 등. 행사를 만들 때 함께 받는다 — 위저드는
 *       단계를 넘길 때마다 저장하므로(docs/03 "임시저장은 자동으로") 1단계를 마쳐야 처음 저장된다.</li>
 *   <li><b>포스터</b>({@link EventPoster}) — 1단계의 필수 항목이지만 행사를 만든 뒤 따로 올린다({@link #changePoster}). 그래서 작성 중에는
 *       없을 수 있고, 판매를 시작하기 전에 갖춰야 한다({@link #isBasicInformationComplete}).</li>
 *   <li><b>공개 수준</b>({@link EventVisibilitySetting}) — 공개로 시작한다(docs/04 "행사 복제 — 공개 수준은 기본값 공개로 시작").</li>
 *   <li><b>예매 설정</b>({@link BookingSettings}) — 오픈 시각 · 예매 종료 · 검표 시작 · 매수 제한 · 회차 선택 제약 · 재입장 · 연령 제한.
 *       처음 값으로 시작한다 — 위저드 5단계는 전부 처음 값이 있어 건너뛸 수 있다(docs/03 "행사 만들기 위저드").</li>
 *   <li><b>취소 규정</b>({@link CancellationPolicy}) — 소비자분쟁해결기준 그대로 시작하고, 관객에게 더 유리한 쪽으로만 조정한다(docs/08).</li>
 *   <li><b>공개 주소</b>({@link EventPublicAddress}) — 만들 때 발급하고 바뀌지 않는다. 공개 수준을 바꿔도 같은 링크다.</li>
 *   <li><b>작성 진행 단계</b>({@link EventWizardStep}) · <b>마지막 수정 시각</b> — 위저드는 단계를 넘길 때마다 자동으로 저장하고, 나갔다
 *       들어오면 이어서 한다(docs/03 "임시저장은 자동으로"). 작성 중 행사 목록이 진행률 · 마지막 수정일 · 멈춘 단계로 가는 버튼을 이것으로
 *       보인다(docs/04 "작성 중 행사 관리").</li>
 * </ul>
 * 화면마다 부르는 이름이 다르다 — 관객 화면은 유형 이름, 주최자 화면은 "행사"({@link #nameOn}).
 * <p>
 * 위저드 2단계의 회차 방식 · 날짜 · 회차는 행사가 아니라 행사의 일정({@link EventSchedule})이 따로 갖는다 — 날짜와 회차가 많고 따로 고친다.
 * 위저드 3단계의 구역 · 블록 · 좌석은 도면({@link FloorPlan})이, 6단계의 입구는 입구 목록({@link EventEntrances})이 따로 갖는다. 행사는 그
 * 단계를 저장할 때 수정 범위를 확인하고 진행 단계 · 마지막 수정 시각을 맡는다({@link #addTimedSession}, {@link #addZone},
 * {@link #addEntrance} 등).
 * <p>
 * <b>행사 상태</b>({@link EventStatus}) — 작성 중으로 시작한다. 사람이 누르는 전환(판매 시작 · 정산 완료 · 남은 회차 전부 취소)은 의도가 드러나는
 * 메서드로, 시간이 정하는 전환(오픈 · 판매 종료 · 행사 종료)은 {@link #advanceByTime}으로 한다. 시간이 정하는 전환은 일정이 알려 주는 마지막 회차의
 * 예매 종료 · 종료 시각({@link EventSchedule#salesEndsAt}, {@link EventSchedule#eventEndsAt})과 이 행사의 오픈 시각을 본다. <b>겹치면 시간이
 * 이긴다</b> — 상태를 바꾸는 메서드는 먼저 지금 시각을 반영한 뒤 판단한다(docs/04 "시간이 운영 판단을 이깁니다").
 * <p>
 * <b>판매 중단</b> — 주최자가 멈추거나({@link #pauseSales}) 관리자 조치가 멈춘다({@link #holdSales}). 관리자 조치가 걸려 있는 동안 주최자는 재개할 수
 * 없고, 조치가 풀려도 판매는 저절로 열리지 않는다({@link #releaseSalesHold}, {@link #resumeSales}). 누가 · 언제 · 왜 멈췄는지는 상태가 덮여도
 * 이력으로 남는다({@link #salesPauses}).
 * <p>
 * <b>언제 무엇을 고칠 수 있는지</b>는 상태가 정한다 — 상태별 수정 범위 표({@link EventEditScope}, {@link EventEditTarget})로 판정하고
 * ({@link #requireEditable}), 회차 시간은 그 회차 시작 1주일 전 잠금까지 본다({@link #requireSessionTimeEditable}). '예매가 열린 뒤'의 범위는
 * 상태 이름이 아니라 <b>예매가 한 번이라도 열렸는지</b>로 정한다({@link #hasBookingOpened}) — 판매 대기에서 관리자 조치로 멈춘 판매 중단은 예매가
 * 열린 적이 없어 판매 대기와 같다. 이 판정은 저장된 상태로 하므로, 시간이 움직이는 상태의 행사는 먼저 {@link #advanceByTime}으로 지금 시각을
 * 반영한다. 위저드 단계 저장({@link #saveBasicInformationStep}, {@link #saveBookingStep}, 구역 · 입구 저장)은 바뀐 항목마다, 포스터
 * 바꾸기({@link #changePoster})는 포스터에 이 판정을 건다. 낱낱의 수정 메서드(기본 정보 · 공개 수준 · 예매 설정 · 취소 규정)에 이 판정을 거는 일과 제목 · 소개 · 포스터의 변경
 * 이력은 수정 유스케이스가 한다(#65, #46).
 * <p>
 * <b>삭제</b>는 상태가 아니라 팔린 표나 초대권이 있었는지로 정한다({@link #requireDeletable}). 누가 지울 수 있는지는 판매를 시작했는지로 갈린다
 * ({@link #deleteAction}).
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
    private Instant bookingOpenedAt;
    private final List<SalesPause> salesPauses;
    private EventWizardStep wizardProgress;
    private Instant lastModifiedAt;

    private Event(Long id, long organizerId, EventPublicAddress publicAddress, EventBasicInformation basicInformation,
            EventPoster poster, EventVisibilitySetting visibility, BookingSettings bookingSettings,
            CancellationPolicy cancellationPolicy, EventStatus status, Instant bookingOpenedAt, List<SalesPause> salesPauses,
            EventWizardStep wizardProgress, Instant lastModifiedAt) {
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
        this.bookingOpenedAt = bookingOpenedAt;
        this.salesPauses = new ArrayList<>(salesPauses);
        this.wizardProgress = Objects.requireNonNull(wizardProgress, "wizardProgress");
        this.lastModifiedAt = Objects.requireNonNull(lastModifiedAt, "lastModifiedAt");
        requireConsistentBookingOpening();
        requireConsistentSalesPauses();
    }

    /**
     * 위저드 1단계를 마친 새 행사를 만든다 — 빈 초안은 없다. 1단계를 넘길 때 처음 저장되므로(docs/03 "임시저장은 자동으로") 기본 정보와 함께
     * 만든다. 작성 중이고, 포스터는 아직 없고, 공개 수준은 공개, 예매 설정은 처음 값({@link BookingSettings#defaults}), 취소 규정은 기준표
     * 그대로({@link CancellationPolicy#standard})다. 진행 단계는 1단계이고, 마지막 수정 시각은 만든 시각이다.
     *
     * @param organizerId      행사를 여는 주최자의 id
     * @param publicAddress    새로 발급한 공개 주소 — 서비스가 TokenGenerator로 만든 값으로 발급한다({@link EventPublicAddress#issue})
     * @param basicInformation 1단계에서 적은 기본 정보
     * @param now              만드는 시각
     */
    public static Event create(long organizerId, EventPublicAddress publicAddress, EventBasicInformation basicInformation,
            Instant now) {
        return new Event(null, organizerId, publicAddress, basicInformation, null, EventVisibilitySetting.publicByDefault(),
                BookingSettings.defaults(), CancellationPolicy.standard(), EventStatus.DRAFT, null, List.of(),
                EventWizardStep.BASIC_INFORMATION, now);
    }

    /**
     * 저장소에서 읽은 값으로 행사를 되살린다.
     *
     * @param poster      포스터. 아직 올리지 않았으면 null
     * @param status          저장된 행사 상태 — 시간이 지나 바뀌었어야 할 상태라도 그대로 되살린다. 지금 시각은 {@link #advanceByTime}이 반영한다
     * @param bookingOpenedAt 처음 판매 중이 된 시각({@link #bookingOpenedAt}). 예매가 열린 적이 없으면 null
     * @param salesPauses     판매 중단 이력 — 멈춘 순서대로
     * @param wizardProgress  작성 진행 단계 — 저장한 가장 뒤의 위저드 단계({@link #wizardProgress})
     * @param lastModifiedAt  마지막 수정 시각({@link #lastModifiedAt})
     * @throws IllegalArgumentException 작성 중 · 판매 대기인데 예매가 열린 시각이 있거나 판매 중인데 없으면, 작성 중인데 판매 중단 이력이 있거나,
     *                                  같은 까닭의 끝나지 않은 중단이 둘이면
     */
    public static Event restore(long id, long organizerId, EventPublicAddress publicAddress,
            EventBasicInformation basicInformation, EventPoster poster, EventVisibilitySetting visibility,
            BookingSettings bookingSettings, CancellationPolicy cancellationPolicy, EventStatus status, Instant bookingOpenedAt,
            List<SalesPause> salesPauses, EventWizardStep wizardProgress, Instant lastModifiedAt) {
        if (id < 1) {
            throw new IllegalArgumentException("행사 id는 1 이상이어야 합니다: " + id);
        }
        Objects.requireNonNull(salesPauses, "salesPauses");
        return new Event(id, organizerId, publicAddress, basicInformation, poster, visibility, bookingSettings,
                cancellationPolicy, status, bookingOpenedAt, salesPauses, wizardProgress, lastModifiedAt);
    }

    /**
     * 위저드 1단계(기본 정보)를 저장한다 — 단계를 넘길 때마다 부르는 자동 임시저장이다(docs/03 "임시저장은 자동으로").
     * <ol>
     *   <li><b>바뀐 항목마다 지금 상태의 수정 범위를 확인한다</b>({@link EventBasicInformation#changedEditTargets}, {@link #requireEditable}).
     *       위저드는 단계의 칸을 모두 다시 보내므로 바뀌지 않은 항목은 보지 않는다 — 예매가 열린 뒤 제목만 고치면서 장소를 그대로 보내도 막지
     *       않는다. 하나라도 막히면 아무것도 바꾸지 않는다.</li>
     *   <li>기본 정보를 바꾼다.</li>
     *   <li>작성 중이면 진행 단계를 이 단계까지로 올린다 — 앞 단계를 다시 저장해도 내려가지 않는다.</li>
     *   <li>무엇이든 바뀌었으면 마지막 수정 시각을 지금으로 한다.</li>
     * </ol>
     * <b>같은 값을 몇 번 저장해도 안전하다</b> — 바뀐 것이 없으면 아무것도 바꾸지 않고 false다. {@link #requireEditable}처럼 저장된 상태로
     * 판정하므로, 시간이 움직이는 상태의 행사는 먼저 {@link #advanceByTime}으로 지금 시각을 반영한다.
     *
     * @return 바뀐 것(기본 정보 · 진행 단계)이 있는지
     * @throws RuleViolationException 바뀐 항목을 지금 상태에서 고칠 수 없으면({@link #requireEditable}과 같은 오류)
     */
    public boolean saveBasicInformationStep(EventBasicInformation newBasicInformation, Instant now) {
        Objects.requireNonNull(newBasicInformation, "newBasicInformation");
        Objects.requireNonNull(now, "now");
        Set<EventEditTarget> changedTargets = basicInformation.changedEditTargets(newBasicInformation);
        changedTargets.forEach(this::requireEditable);
        basicInformation = newBasicInformation;
        return completeWizardStep(EventWizardStep.BASIC_INFORMATION, !changedTargets.isEmpty(), now);
    }

    /**
     * 위저드 5단계(공개 수준 · 예매 설정 · 취소 규정)를 저장한다 — 단계를 넘길 때마다 부르는 자동 임시저장이다. 5단계는 전부 처음 값이 있어
     * 건너뛸 수 있다 — 건너뛰면 행사를 만들 때의 처음 값 그대로다({@link #create}).
     * <ol>
     *   <li>공개 수준을 바꾼 설정을 만든다({@link EventVisibilitySetting#change}) — 암호 보호로 두면서 새 암호를 적지 않으면 걸려 있던 암호를
     *       그대로 쓴다.</li>
     *   <li><b>바뀐 항목마다 지금 상태의 수정 범위를 확인한다</b> — 공개 수준({@link EventEditTarget#VISIBILITY}), 예매 종료 · 그 밖의 예매
     *       설정({@link BookingSettings#changedEditTargets}), 취소 규정({@link EventEditTarget#CANCELLATION_POLICY}). 바뀌지 않은 항목은 보지
     *       않는다 — 예매가 열린 뒤 예매 종료만 고치면서 취소 규정을 그대로 보내도 막지 않는다. 하나라도 막히면 아무것도 바꾸지 않는다.</li>
     *   <li>셋을 바꾸고, 작성 중이면 진행 단계를 이 단계까지로 올리고, 무엇이든 바뀌었으면 마지막 수정 시각을 지금으로 한다.</li>
     * </ol>
     * 같은 값을 몇 번 저장해도 안전하다 — 바뀐 것이 없으면 아무것도 바꾸지 않고 false다. {@link #requireEditable}처럼 저장된 상태로 판정한다.
     *
     * @param newPasswordHash 새로 정한 암호의 해시 — 암호 보호로 두면서 암호를 새로 적었을 때만. 아니면 null
     * @return 바뀐 것(공개 수준 · 예매 설정 · 취소 규정 · 진행 단계)이 있는지
     * @throws RuleViolationException   암호 보호인데 새 암호도 걸려 있던 암호도 없으면({@link EventErrorCode#EVENT_PASSWORD_REQUIRED}), 바뀐
     *                                  항목을 지금 상태에서 고칠 수 없으면({@link #requireEditable}과 같은 오류)
     * @throws IllegalArgumentException 암호 보호가 아닌데 새 암호 해시를 넘기면
     */
    public boolean saveBookingStep(EventVisibility newVisibility, EventPasswordHash newPasswordHash,
            BookingSettings newBookingSettings, CancellationPolicy newCancellationPolicy, Instant now) {
        Objects.requireNonNull(newBookingSettings, "newBookingSettings");
        Objects.requireNonNull(newCancellationPolicy, "newCancellationPolicy");
        Objects.requireNonNull(now, "now");
        EventVisibilitySetting newVisibilitySetting = visibility.change(newVisibility, newPasswordHash);
        Set<EventEditTarget> changedTargets = EnumSet.noneOf(EventEditTarget.class);
        if (!newVisibilitySetting.equals(visibility)) {
            changedTargets.add(EventEditTarget.VISIBILITY);
        }
        changedTargets.addAll(bookingSettings.changedEditTargets(newBookingSettings));
        if (!newCancellationPolicy.equals(cancellationPolicy)) {
            changedTargets.add(EventEditTarget.CANCELLATION_POLICY);
        }
        changedTargets.forEach(this::requireEditable);
        visibility = newVisibilitySetting;
        bookingSettings = newBookingSettings;
        cancellationPolicy = newCancellationPolicy;
        return completeWizardStep(EventWizardStep.BOOKING_SETTINGS, !changedTargets.isEmpty(), now);
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
     * 포스터를 새로 올린 것으로 바꾼다(처음 올리기 포함) — 위저드 1단계의 항목이다(docs/03 "행사 만들기 위저드").
     * <ol>
     *   <li><b>지금 상태의 수정 범위를 확인한다</b>({@link EventEditTarget#TITLE_POSTER_DESCRIPTION}, {@link #requireEditable}) — 예매가 열린 뒤에도
     *       자유롭게 바꾸고, 판매가 끝나면 고정이다(docs/04 "제목 · 소개 · 포스터를 얼리는 이유"). 막히면 아무것도 바꾸지 않는다.</li>
     *   <li>포스터를 바꾼다. 바꾸기 전 포스터의 파일을 정리하는 일은 유스케이스가 한다.</li>
     *   <li>1단계를 저장한 것으로 다룬다 — 작성 중이면 진행 단계를 1단계까지로 올리고(행사가 있으면 이미 1단계 이상이라 그대로다), 마지막 수정 시각을
     *       지금으로 한다. 새로 올린 포스터는 늘 새 열쇠라 늘 바뀐 것이다.</li>
     * </ol>
     * {@link #requireEditable}처럼 저장된 상태로 판정하므로, 시간이 움직이는 상태의 행사는 먼저 {@link #advanceByTime}으로 지금 시각을 반영한다.
     * 제목 · 소개 · 포스터의 변경 이력은 변경 이력 기능이 남긴다(#46).
     *
     * @param newPoster 새로 올린 포스터 — 새로 붙인 열쇠({@link EventPoster#issue})
     * @param now       올린 시각
     * @return 바꾸기 전 포스터. 없었으면 빈 값
     * @throws RuleViolationException   지금 상태에서 포스터를 고칠 수 없으면({@link #requireEditable}과 같은 오류)
     * @throws IllegalArgumentException 지금 포스터와 같은 열쇠면 — 새로 올린 포스터는 새 열쇠를 받는다. 같은 열쇠를 받으면 바꾸기 전 포스터의
     *                                  파일을 정리하다 지금 포스터의 파일을 지운다
     */
    public Optional<EventPoster> changePoster(EventPoster newPoster, Instant now) {
        Objects.requireNonNull(newPoster, "newPoster");
        Objects.requireNonNull(now, "now");
        if (newPoster.equals(poster)) {
            throw new IllegalArgumentException("지금 포스터와 같은 열쇠입니다 — 새로 올린 포스터는 새 열쇠를 받습니다: 행사 " + id);
        }
        requireEditable(EventEditTarget.TITLE_POSTER_DESCRIPTION);
        Optional<EventPoster> previousPoster = Optional.ofNullable(poster);
        poster = newPoster;
        completeWizardStep(EventWizardStep.BASIC_INFORMATION, true, now);
        return previousPoster;
    }

    /**
     * 위저드 2단계에서 회차 방식을 처음 고른다 — 이 행사의 일정이 생긴다(docs/02 "회차형과 종일형 — 회차 방식", docs/03 "기본 정보 — 회차 방식은
     * 회차를 만드는 2단계에서"). 종일형은 행사 단위의 기본 운영 시간을 함께 넣는다 — 30일짜리 전시에 30번 입력하지 않게 한 번 넣고 다른 날만
     * 덮어쓴다(docs/02 "날짜마다 운영 시간이 다를 수 있습니다").
     * <ol>
     *   <li><b>지금 상태의 수정 범위를 확인한다</b> — 회차 일시({@link EventEditTarget#SESSION_DATE_TIME}).</li>
     *   <li>일정을 만들고, 작성 중이면 진행 단계를 2단계까지로 올리고, 마지막 수정 시각을 지금으로 한다. 만들면 늘 바뀐 것이다.</li>
     * </ol>
     * 이미 일정이 있으면 {@link #changeSessionMode}를 쓴다. {@link #requireEditable}처럼 저장된 상태로 판정한다.
     *
     * @param defaultOperatingHours 종일형의 기본 운영 시간. 회차형이면 null
     * @return 새 일정 — 날짜가 아직 없다
     * @throws RuleViolationException   지금 상태에서 회차 일시를 고칠 수 없으면({@link #requireEditable}과 같은 오류)
     * @throws IllegalArgumentException 종일형인데 기본 운영 시간이 없거나 회차형인데 있으면
     * @throws IllegalStateException    저장 전 행사면
     */
    public EventSchedule startSchedule(SessionMode mode, OperatingHours defaultOperatingHours, Instant now) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(now, "now");
        requireSaved("일정");
        requireOperatingHoursMatchMode(mode, defaultOperatingHours);
        requireEditable(EventEditTarget.SESSION_DATE_TIME);
        EventSchedule schedule = mode == SessionMode.TIMED ? EventSchedule.timed(id) : EventSchedule.allDay(id, defaultOperatingHours);
        completeWizardStep(EventWizardStep.SCHEDULE, true, now);
        return schedule;
    }

    /**
     * 위저드 2단계에서 회차 방식 · 기본 운영 시간을 다시 저장한다 — 화면은 회차 방식과 (종일형이면) 기본 운영 시간을 함께 보낸다.
     * <ul>
     *   <li><b>회차 방식이 바뀌면</b> 회차 일시({@link EventEditTarget#SESSION_DATE_TIME})의 수정 범위를 확인하고 바꾼다. 날짜가 있으면 바꿀 수
     *       없다({@link EventSchedule#changeToTimed}, {@link EventSchedule#changeToAllDay}).</li>
     *   <li><b>종일형 그대로 기본 운영 시간이 바뀌면</b> 덮어쓰지 않은 날짜의 회차 시간이 모두 따라 바뀌므로, 그 회차마다 주최자가 혼자 시간을
     *       바꿀 수 있는지 확인한다 — 예매가 열린 뒤면 그 회차 시작 1주일 전 잠금까지 본다({@link #requireSessionTimeEditable}). 따라 바뀌는 회차가
     *       없으면 회차 일시의 수정 범위만 본다.</li>
     * </ul>
     * 작성 중이면 진행 단계를 2단계까지로 올리고, 무엇이든 바뀌었으면 마지막 수정 시각을 지금으로 한다. 같은 값을 몇 번 저장해도 안전하다 — 바뀐 것이
     * 없으면 수정 범위를 보지 않고 일정도 그대로다. 하나라도 막히면 아무것도 바꾸지 않는다.
     *
     * @param defaultOperatingHours 종일형의 기본 운영 시간. 회차형이면 null
     * @return 바뀐 것(회차 방식 · 기본 운영 시간 · 진행 단계)이 있는지
     * @throws RuleViolationException   바뀌는데 지금 상태에서 고칠 수 없으면({@link #requireEditable}과 같은 오류,
     *                                  {@link EventErrorCode#SESSION_TIME_EDIT_LOCKED}), 날짜가 있는데 회차 방식을 바꾸면
     *                                  ({@link EventErrorCode#SESSION_MODE_LOCKED_BY_DATES})
     * @throws IllegalArgumentException 종일형인데 기본 운영 시간이 없거나 회차형인데 있으면, 다른 행사의 일정이면
     */
    public boolean changeSessionMode(EventSchedule schedule, SessionMode mode, OperatingHours defaultOperatingHours, Instant now) {
        requireOwnSchedule(schedule);
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(now, "now");
        requireOperatingHoursMatchMode(mode, defaultOperatingHours);
        boolean changed = false;
        if (mode != schedule.mode()) {
            requireEditable(EventEditTarget.SESSION_DATE_TIME);
            changed = mode == SessionMode.TIMED ? schedule.changeToTimed() : schedule.changeToAllDay(defaultOperatingHours);
        } else if (mode == SessionMode.ALL_DAY && !schedule.defaultOperatingHours().orElseThrow().equals(defaultOperatingHours)) {
            requireSessionTimesEditable(schedule, sessionsFollowingDefaultOperatingHours(schedule), now);
            changed = schedule.changeDefaultOperatingHours(defaultOperatingHours);
        }
        return completeWizardStep(EventWizardStep.SCHEDULE, changed, now);
    }

    /**
     * 위저드 2단계에서 회차형 회차를 더한다 — 그 날짜가 없으면 날짜도 생긴다({@link EventSchedule#addSession}). 출연은 없고 가격 조정은 없음으로
     * 시작한다.
     * <ol>
     *   <li><b>지금 상태의 수정 범위를 확인한다</b> — 회차 추가({@link EventEditTarget#SESSION_ADDITION}). 예매가 열린 뒤에도 더한다 — 매진되면 회차를
     *       더 여는 게 자연스럽다(docs/04 "회차 추가는 허용합니다"). 판매가 끝나면 막힌다.</li>
     *   <li>회차를 더하고, 작성 중이면 진행 단계를 2단계까지로 올리고, 마지막 수정 시각을 지금으로 한다. 더하면 늘 바뀐 것이다.</li>
     * </ol>
     *
     * @return 더한 회차(아직 id 없음)
     * @throws RuleViolationException   지금 상태에서 회차를 더할 수 없으면({@link #requireEditable}과 같은 오류), 종일형이면
     *                                  ({@link EventErrorCode#ALL_DAY_SINGLE_SESSION_PER_DATE}), 같은 날짜에 같은 시각에 시작하는 회차가 있으면
     *                                  ({@link EventErrorCode#SESSION_START_TIME_TAKEN})
     * @throws IllegalArgumentException 다른 행사의 일정이면
     */
    public EventSession addTimedSession(EventSchedule schedule, LocalDate date, TimedSessionTime time, Instant now) {
        requireOwnSchedule(schedule);
        Objects.requireNonNull(now, "now");
        requireEditable(EventEditTarget.SESSION_ADDITION);
        EventSession added = schedule.addSession(date, time);
        completeWizardStep(EventWizardStep.SCHEDULE, true, now);
        return added;
    }

    /**
     * 위저드 2단계에서 종일형 날짜를 더한다 — 날짜와 그 날짜의 회차 하나가 함께 생기고, 운영 시간은 기본 운영 시간을 따른다
     * ({@link EventSchedule#addDate}). 수정 범위 · 진행 단계는 {@link #addTimedSession}과 같다.
     *
     * @return 그 날짜의 회차(아직 id 없음)
     * @throws RuleViolationException   지금 상태에서 회차를 더할 수 없으면({@link #requireEditable}과 같은 오류), 회차형이면
     *                                  ({@link EventErrorCode#TIMED_DATE_NEEDS_SESSION}), 이미 있는 날짜면
     *                                  ({@link EventErrorCode#SCHEDULE_DATE_ALREADY_EXISTS})
     * @throws IllegalArgumentException 다른 행사의 일정이면
     */
    public EventSession addAllDayDate(EventSchedule schedule, LocalDate date, Instant now) {
        requireOwnSchedule(schedule);
        Objects.requireNonNull(now, "now");
        requireEditable(EventEditTarget.SESSION_ADDITION);
        if (schedule.mode() == SessionMode.TIMED) {
            throw new RuleViolationException(EventErrorCode.TIMED_DATE_NEEDS_SESSION, detail() + ", 날짜 " + date);
        }
        EventSession added = schedule.addDate(date);
        completeWizardStep(EventWizardStep.SCHEDULE, true, now);
        return added;
    }

    /**
     * 위저드 2단계에서 회차의 일시를 바꾼다 — 화면은 날짜와 (회차형이면) 시작 · 종료 시각을 함께 보낸다. 회차 일시는 날짜까지다(docs/04 "예매가 열린 뒤
     * (판매 중) 수정 범위 — 회차 일시"). 회차는 그대로라 회차를 가리키는 재고 · 티켓도 그대로 따라간다.
     * <ul>
     *   <li><b>회차형</b> — 시작 시각은 필수다. 다른 날짜로 옮기면 그 날짜가 생기고 비게 된 날짜는 빠진다
     *       ({@link EventSchedule#changeTimedSessionDateTime}).</li>
     *   <li><b>종일형</b> — 날짜만 바꾼다. 회차의 시간은 그날의 운영 시간이라 시작 · 종료 시각을 받지 않는다
     *       ({@link EventSchedule#changeAllDaySessionDate}). 운영 시간은 {@link #changeDateOperatingHours}로 바꾼다.</li>
     * </ul>
     * 바뀌면 주최자가 혼자 회차 시간을 바꿀 수 있는지 확인한다 — 회차 일시의 수정 범위와, 예매가 열린 뒤면 그 회차 시작 1주일 전 잠금
     * ({@link #requireSessionTimeEditable}). <b>날짜 · 시작은 그대로 두고 비어 있던 종료 시각만 채우면</b> 시간 수정이 잠긴 뒤에도 받는다 — 잠기는
     * 시점에 종료 시각 입력을 요구하므로 입력할 길이 열려 있어야 한다({@link EventEditTarget#MISSING_SESSION_END_TIME},
     * {@link EventSchedule#enterSessionEndTime}).
     * <p>
     * 작성 중이면 진행 단계를 2단계까지로 올리고, 무엇이든 바뀌었으면 마지막 수정 시각을 지금으로 한다. 같은 값을 몇 번 저장해도 안전하다. 하나라도
     * 막히면 아무것도 바꾸지 않는다. 티켓이 있는 회차의 알림 · 공제 면제는 회차 시간 수정(#105)이 한다.
     *
     * @param newStartTime 회차형의 새 시작 시각. 종일형이면 null
     * @param newEndTime   회차형의 새 종료 시각. 비워 두거나 종일형이면 null
     * @return 바뀐 것(회차 일시 · 진행 단계)이 있는지
     * @throws com.ticketon.domain.error.NotFoundException 일정에 없는 회차면({@link EventErrorCode#SESSION_NOT_FOUND})
     * @throws RuleViolationException                      종일형인데 시작 · 종료 시각을 적었으면
     *                                                     ({@link EventErrorCode#ALL_DAY_SINGLE_SESSION_PER_DATE}), 회차형인데 시작 시각이
     *                                                     없으면({@link EventErrorCode#SESSION_START_TIME_REQUIRED}), 바뀌는데 지금 고칠 수
     *                                                     없으면({@link #requireSessionTimeEditable}과 같은 오류), 옮겨 갈 날짜 · 시각이
     *                                                     겹치면({@link EventErrorCode#SESSION_START_TIME_TAKEN},
     *                                                     {@link EventErrorCode#SCHEDULE_DATE_ALREADY_EXISTS})
     * @throws IllegalArgumentException                    종료 시각이 시작보다 늦지 않거나 분 단위가 아니면, 다른 행사의 일정이면
     */
    public boolean changeSessionDateTime(EventSchedule schedule, long sessionId, LocalDate newDate, LocalTime newStartTime,
            LocalTime newEndTime, Instant now) {
        requireOwnSchedule(schedule);
        Objects.requireNonNull(newDate, "newDate");
        Objects.requireNonNull(now, "now");
        EventSession session = schedule.session(sessionId);
        LocalDate currentDate = schedule.dateOf(sessionId).date();
        boolean changed = schedule.mode() == SessionMode.ALL_DAY
                ? changeAllDaySessionDate(schedule, session, currentDate, newDate, newStartTime, newEndTime, now)
                : changeTimedSessionDateTime(schedule, session, currentDate, newDate, newStartTime, newEndTime, now);
        return completeWizardStep(EventWizardStep.SCHEDULE, changed, now);
    }

    /**
     * 위저드 2단계에서 종일형 날짜 하나의 운영 시간을 덮어쓰거나, 덮어쓴 것을 지워 기본 운영 시간을 따르게 한다(docs/02 "날짜마다 운영 시간이 다를 수
     * 있습니다" — 주말은 늦게까지). 덮어쓴 값이 바뀌면 그 날짜의 회차 시간이 바뀌는 것이라 주최자가 혼자 시간을 바꿀 수 있는지 확인한다
     * ({@link #requireSessionTimeEditable}). 진행 단계 · 마지막 수정 시각은 {@link #changeSessionDateTime}과 같다.
     *
     * @param newOverride 그날만 쓸 운영 시간. 덮어쓴 것을 지우려면 null
     * @return 바뀐 것(그날의 운영 시간 · 진행 단계)이 있는지 — 덮어쓴 값이 지금과 같으면 수정 범위를 보지 않는다
     * @throws com.ticketon.domain.error.NotFoundException 일정에 없는 날짜면({@link EventErrorCode#SCHEDULE_DATE_NOT_FOUND})
     * @throws RuleViolationException                      회차형이면({@link EventErrorCode#OPERATING_HOURS_ONLY_FOR_ALL_DAY}), 바뀌는데 지금
     *                                                     고칠 수 없으면({@link #requireSessionTimeEditable}과 같은 오류)
     * @throws IllegalArgumentException                    다른 행사의 일정이면
     */
    public boolean changeDateOperatingHours(EventSchedule schedule, LocalDate date, OperatingHours newOverride, Instant now) {
        requireOwnSchedule(schedule);
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(now, "now");
        if (schedule.mode() != SessionMode.ALL_DAY) {
            throw new RuleViolationException(EventErrorCode.OPERATING_HOURS_ONLY_FOR_ALL_DAY, detail() + ", 날짜 " + date);
        }
        EventDate eventDate = schedule.requireDate(date);
        boolean changed = !eventDate.operatingHoursOverride().equals(Optional.ofNullable(newOverride));
        if (changed) {
            requireSessionTimesEditable(schedule, eventDate.sessions(), now);
            if (newOverride == null) {
                schedule.clearOperatingHoursOverride(date);
            } else {
                schedule.overrideOperatingHours(date, newOverride);
            }
        }
        return completeWizardStep(EventWizardStep.SCHEDULE, changed, now);
    }

    /**
     * 위저드 2단계에서 회차의 출연을 바꾼다(docs/02 "출연 (선택)"). 바뀔 때만 출연의 수정 범위({@link EventEditTarget#PERFORMERS})를 확인한다 — 제목
     * · 소개 · 포스터와 같아 예매가 열린 뒤에도 고친다. 출연 칸을 고치는 것만으로는 출연자 변경 알림이 되지 않는다. 변경 이력은 #46이 남긴다. 진행
     * 단계 · 마지막 수정 시각은 {@link #changeSessionDateTime}과 같다.
     *
     * @param newPerformers 새 출연. 지우려면 null
     * @return 바뀐 것(출연 · 진행 단계)이 있는지
     * @throws com.ticketon.domain.error.NotFoundException 일정에 없는 회차면({@link EventErrorCode#SESSION_NOT_FOUND})
     * @throws RuleViolationException                      바뀌는데 지금 상태에서 출연을 고칠 수 없으면({@link #requireEditable}과 같은 오류)
     * @throws IllegalArgumentException                    다른 행사의 일정이면
     */
    public boolean changeSessionPerformers(EventSchedule schedule, long sessionId, SessionPerformers newPerformers, Instant now) {
        requireOwnSchedule(schedule);
        Objects.requireNonNull(now, "now");
        boolean changed = !schedule.session(sessionId).performers().equals(Optional.ofNullable(newPerformers));
        if (changed) {
            requireEditable(EventEditTarget.PERFORMERS);
            schedule.changePerformers(sessionId, newPerformers);
        }
        return completeWizardStep(EventWizardStep.SCHEDULE, changed, now);
    }

    /**
     * 회차의 가격 조정을 바꾼다(docs/05 "회차별 가격 조정" — 위저드 화면에서는 4단계에 보이지만 회차에 붙는 값이라 회차와 함께 저장한다). 바뀔 때만
     * 가격의 수정 범위({@link EventEditTarget#PRICE})를 확인한다 — 예매가 열린 뒤에도 바꾸되 신규 예매부터 적용한다(가격 계산 #48). 확인 필요 (#41) —
     * 진행 단계는 회차를 저장하는 2단계로 다룬다 — 2단계만 마친 행사가 가격 조정을 저장해도 4단계까지 마친 것으로 보이지 않게 한다. 마지막 수정
     * 시각은 {@link #changeSessionDateTime}과 같다.
     *
     * @return 바뀐 것(가격 조정 · 진행 단계)이 있는지
     * @throws com.ticketon.domain.error.NotFoundException 일정에 없는 회차면({@link EventErrorCode#SESSION_NOT_FOUND})
     * @throws RuleViolationException                      바뀌는데 지금 상태에서 가격을 고칠 수 없으면({@link #requireEditable}과 같은 오류)
     * @throws IllegalArgumentException                    다른 행사의 일정이면
     */
    public boolean changeSessionPriceAdjustment(EventSchedule schedule, long sessionId, SessionPriceAdjustment newPriceAdjustment,
            Instant now) {
        requireOwnSchedule(schedule);
        Objects.requireNonNull(newPriceAdjustment, "newPriceAdjustment");
        Objects.requireNonNull(now, "now");
        boolean changed = !schedule.session(sessionId).priceAdjustment().equals(newPriceAdjustment);
        if (changed) {
            requireEditable(EventEditTarget.PRICE);
            schedule.changePriceAdjustment(sessionId, newPriceAdjustment);
        }
        return completeWizardStep(EventWizardStep.SCHEDULE, changed, now);
    }

    /**
     * 위저드 2단계에서 회차를 지운다 — 그 날짜의 마지막 회차였으면 날짜도 빠진다({@link EventSchedule#removeSession}). 지운 회차는 저장소가 지운 시각을
     * 적어 남긴다 — 취소 · 회수된 티켓은 원래 회차를 그대로 가리킨다(docs/04, docs/14).
     * <ol>
     *   <li><b>지금 상태의 수정 범위를 확인한다</b> — 회차 · 구역 삭제({@link EventEditTarget#SESSION_OR_ZONE_DELETION}). 티켓이 있는 회차를 지울 수
     *       없는 규칙은 #64가 더한다.</li>
     *   <li>회차를 지우고, 작성 중이면 진행 단계를 2단계까지로 올리고, 마지막 수정 시각을 지금으로 한다.</li>
     * </ol>
     *
     * @return 지운 회차
     * @throws com.ticketon.domain.error.NotFoundException 일정에 없는 회차면({@link EventErrorCode#SESSION_NOT_FOUND})
     * @throws RuleViolationException                      지금 상태에서 회차를 지울 수 없으면({@link #requireEditable}과 같은 오류)
     * @throws IllegalArgumentException                    다른 행사의 일정이면
     */
    public EventSession removeSession(EventSchedule schedule, long sessionId, Instant now) {
        requireOwnSchedule(schedule);
        Objects.requireNonNull(now, "now");
        EventSession session = schedule.session(sessionId);
        requireEditable(EventEditTarget.SESSION_OR_ZONE_DELETION);
        schedule.removeSession(sessionId);
        completeWizardStep(EventWizardStep.SCHEDULE, true, now);
        return session;
    }

    /**
     * 위저드 2단계에서 날짜를 그 아래 회차와 함께 지운다({@link EventSchedule#removeDate}). 수정 범위 · 진행 단계는 {@link #removeSession}과 같다.
     *
     * @return 함께 지운 회차 — 시작 시각 순
     * @throws com.ticketon.domain.error.NotFoundException 일정에 없는 날짜면({@link EventErrorCode#SCHEDULE_DATE_NOT_FOUND})
     * @throws RuleViolationException                      지금 상태에서 회차를 지울 수 없으면({@link #requireEditable}과 같은 오류)
     * @throws IllegalArgumentException                    다른 행사의 일정이면
     */
    public List<EventSession> removeScheduleDate(EventSchedule schedule, LocalDate date, Instant now) {
        requireOwnSchedule(schedule);
        Objects.requireNonNull(now, "now");
        List<EventSession> removed = List.copyOf(schedule.requireDate(date).sessions());
        requireEditable(EventEditTarget.SESSION_OR_ZONE_DELETION);
        schedule.removeDate(date);
        completeWizardStep(EventWizardStep.SCHEDULE, true, now);
        return removed;
    }

    /**
     * 위저드 3단계에서 이 행사의 도면에 구역을 더한다(docs/02 "설정 흐름" — 지정형은 블록 · 좌석이 이어지고 수량형은 정원 입력만).
     * <ol>
     *   <li><b>지금 상태의 수정 범위를 확인한다</b> — 도면({@link EventEditTarget#FLOOR_PLAN}). 판매가 끝나면 도면이 고정되어 막힌다.</li>
     *   <li>도면에 구역을 더한다({@link FloorPlan#addZone}) — 정원이 종류와 맞는지, 이름이 다른 구역 · 블록과 겹치지 않는지 본다.</li>
     *   <li>3단계를 저장한 것으로 다룬다 — 작성 중이면 진행 단계를 3단계까지로 올리고, 마지막 수정 시각을 지금으로 한다. 더하면 늘 바뀐 것이다.</li>
     * </ol>
     * {@link #requireEditable}처럼 저장된 상태로 판정한다. 하나라도 막히면 아무것도 바꾸지 않는다.
     *
     * @return 더한 구역(아직 id 없음)
     * @throws RuleViolationException   지금 상태에서 도면을 고칠 수 없으면({@link #requireEditable}과 같은 오류), 도면이 막으면
     *                                  ({@link FloorPlan#addZone}과 같은 오류)
     * @throws IllegalArgumentException 다른 행사의 도면이면
     */
    public Zone addZone(FloorPlan floorPlan, ZoneKind kind, ZoneSettings settings, Instant now) {
        requireOwnFloorPlan(floorPlan);
        Objects.requireNonNull(now, "now");
        requireEditable(EventEditTarget.FLOOR_PLAN);
        Zone added = floorPlan.addZone(kind, settings);
        completeWizardStep(EventWizardStep.SEATING, true, now);
        return added;
    }

    /**
     * 위저드 3단계에서 이 행사의 도면에 있는 구역 한 줄을 고친다 — 칸(이름 · 색 · 기본가 · 정원)을 모두 보낸다.
     * <ol>
     *   <li><b>바뀌는 항목마다 지금 상태의 수정 범위를 확인한다</b>({@link FloorPlan#changedEditTargets}) — 이름 · 색은 도면, 기본가는 가격, 정원은
     *       정원. 바뀌지 않은 칸은 보지 않는다 — 예매가 열린 뒤 기본가만 고치며 나머지를 그대로 보내도 막지 않는다.</li>
     *   <li>구역을 고친다({@link FloorPlan#changeZone}).</li>
     *   <li>작성 중이면 진행 단계를 3단계까지로 올리고, 무엇이든 바뀌었으면 마지막 수정 시각을 지금으로 한다.</li>
     * </ol>
     * 같은 값을 몇 번 저장해도 안전하다 — 바뀐 것이 없으면 구역은 그대로다. {@link #requireEditable}처럼 저장된 상태로 판정한다. 하나라도 막히면
     * 아무것도 바꾸지 않는다.
     *
     * @return 바뀐 것(구역 · 진행 단계)이 있는지
     * @throws com.ticketon.domain.error.NotFoundException 도면에 없는 구역이면({@link EventErrorCode#ZONE_NOT_FOUND})
     * @throws RuleViolationException                      바뀌는 항목을 지금 상태에서 고칠 수 없으면({@link #requireEditable}과 같은 오류),
     *                                                     도면이 막으면({@link FloorPlan#changeZone}과 같은 오류)
     * @throws IllegalArgumentException                    다른 행사의 도면이면
     */
    public boolean changeZone(FloorPlan floorPlan, long zoneId, ZoneSettings newSettings, Instant now) {
        requireOwnFloorPlan(floorPlan);
        Objects.requireNonNull(now, "now");
        Set<EventEditTarget> changedTargets = floorPlan.changedEditTargets(zoneId, newSettings);
        changedTargets.forEach(this::requireEditable);
        floorPlan.changeZone(zoneId, newSettings);
        return completeWizardStep(EventWizardStep.SEATING, !changedTargets.isEmpty(), now);
    }

    /**
     * 위저드 3단계에서 이 행사의 도면에 있는 구역을 지운다 — 그 구역에 배정했던 좌석은 배정이 풀린다({@link FloorPlan#removeZone}). 지운
     * 구역은 저장소가 지운 시각을 적어 남긴다 — 취소 · 회수된 티켓은 원래 구역을 그대로 가리킨다(docs/14).
     * <ol>
     *   <li><b>지금 상태의 수정 범위를 확인한다</b> — 회차 · 구역 삭제({@link EventEditTarget#SESSION_OR_ZONE_DELETION}). 티켓이 있는 구역을
     *       지울 수 없는 규칙은 #64가 더한다.</li>
     *   <li>구역을 지우고, 작성 중이면 진행 단계를 3단계까지로 올리고, 마지막 수정 시각을 지금으로 한다.</li>
     * </ol>
     *
     * @return 지운 구역 — 재고에 알리고 조작 기록을 남기는 데 쓴다
     * @throws com.ticketon.domain.error.NotFoundException 도면에 없는 구역이면({@link EventErrorCode#ZONE_NOT_FOUND})
     * @throws RuleViolationException                      지금 상태에서 구역을 지울 수 없으면({@link #requireEditable}과 같은 오류)
     * @throws IllegalArgumentException                    다른 행사의 도면이면
     */
    public Zone removeZone(FloorPlan floorPlan, long zoneId, Instant now) {
        requireOwnFloorPlan(floorPlan);
        Objects.requireNonNull(now, "now");
        Zone zone = floorPlan.zone(zoneId);
        requireEditable(EventEditTarget.SESSION_OR_ZONE_DELETION);
        floorPlan.removeZone(zoneId);
        completeWizardStep(EventWizardStep.SEATING, true, now);
        return zone;
    }

    /**
     * 위저드 3단계에서 이 행사의 도면에 블록을 얹는다 — 이름 + 행 × 열 덩어리를 얹으면 격자의 모든 자리에 좌석이 생긴다(docs/02 "에디터는 블록
     * 단위"). 이름을 비웠으면 "블록 N"을 붙인다({@link FloorPlan#addBlock}).
     * <ol>
     *   <li><b>지금 상태의 수정 범위를 확인한다</b> — 도면({@link EventEditTarget#FLOOR_PLAN}). 예매가 열린 뒤에도 빈 블록은 더한다(docs/04 "빈 블록
     *       추가도 허용합니다"). 판매가 끝나면 도면이 고정되어 막힌다.</li>
     *   <li>블록을 얹고, 작성 중이면 진행 단계를 3단계까지로 올리고, 마지막 수정 시각을 지금으로 한다. 얹으면 늘 바뀐 것이다.</li>
     * </ol>
     * {@link #requireEditable}처럼 저장된 상태로 판정한다. 하나라도 막히면 아무것도 바꾸지 않는다.
     *
     * @param requestedName 블록 이름. 비웠으면 null
     * @return 얹은 블록(블록 · 좌석 모두 아직 id 없음)
     * @throws RuleViolationException   지금 상태에서 도면을 고칠 수 없으면({@link #requireEditable}과 같은 오류), 이름이 다른 블록 · 구역과
     *                                  겹치면({@link EventErrorCode#BLOCK_NAME_ALREADY_TAKEN}, {@link EventErrorCode#BLOCK_NAME_TAKEN_BY_ZONE})
     * @throws IllegalArgumentException 다른 행사의 도면이면
     */
    public SeatBlock addBlock(FloorPlan floorPlan, BlockName requestedName, BlockSize size, CanvasPoint position, BlockAngle angle,
            Instant now) {
        requireOwnFloorPlan(floorPlan);
        Objects.requireNonNull(now, "now");
        requireEditable(EventEditTarget.FLOOR_PLAN);
        SeatBlock added = floorPlan.addBlock(requestedName, size, position, angle);
        completeWizardStep(EventWizardStep.SEATING, true, now);
        return added;
    }

    /**
     * 위저드 3단계에서 블록 이름을 바꾼다. 이미 팔린 표의 표기도 따라 바뀐다 — 이력은 남기지 않는다(docs/02 "판매 중에도 블록 이름을 고칠 수
     * 있습니다"). 좌석의 id와 번호는 그대로다.
     * <p>
     * 이름이 바뀔 때만 지금 상태의 수정 범위를 확인한다 — 블록 이름({@link EventEditTarget#BLOCK_NAME}). 예매가 열린 뒤에도 고치고, 판매가 끝나면
     * 막힌다. 지금과 같은 이름(비웠으면 붙을 "블록 N"이 지금 이름과 같을 때도)이면 수정 범위를 보지 않고 블록도 그대로다 — 같은 값을 몇 번 저장해도
     * 안전하다. 작성 중이면 진행 단계를 3단계까지로 올리고, 무엇이든 바뀌었으면 마지막 수정 시각을 지금으로 한다. 팔린 좌석이 있는 블록이면 "예매자에게
     * 알릴까요?" 창은 부르는 쪽이 띄운다(#110).
     *
     * @param requestedName 새 이름. 비웠으면 null — "블록 N"을 붙인다
     * @return 바뀐 것(블록 이름 · 진행 단계)이 있는지
     * @throws com.ticketon.domain.error.NotFoundException 도면에 없는 블록이면({@link EventErrorCode#BLOCK_NOT_FOUND})
     * @throws RuleViolationException                      지금 상태에서 블록 이름을 고칠 수 없으면({@link #requireEditable}과 같은 오류),
     *                                                     이름이 다른 블록 · 구역과 겹치면
     * @throws IllegalArgumentException                    다른 행사의 도면이면
     */
    public boolean renameBlock(FloorPlan floorPlan, long blockId, BlockName requestedName, Instant now) {
        requireOwnFloorPlan(floorPlan);
        Objects.requireNonNull(now, "now");
        boolean renamed = floorPlan.renameBlock(blockId, requestedName, () -> requireEditable(EventEditTarget.BLOCK_NAME));
        return completeWizardStep(EventWizardStep.SEATING, renamed, now);
    }

    /**
     * 위저드 3단계에서 블록을 옮긴다 — 좌석은 블록 안의 자리 · 번호 그대로 함께 옮겨진다. 위치가 바뀔 때만 도면의 수정 범위
     * ({@link EventEditTarget#FLOOR_PLAN})를 확인한다. 티켓이 걸린 좌석이 있는 블록을 옮길 수 없는 규칙은 #64가 더한다. 진행 단계 · 마지막 수정
     * 시각은 {@link #renameBlock}과 같다.
     *
     * @return 바뀐 것(블록 위치 · 진행 단계)이 있는지
     * @throws com.ticketon.domain.error.NotFoundException 도면에 없는 블록이면({@link EventErrorCode#BLOCK_NOT_FOUND})
     * @throws RuleViolationException                      위치가 바뀌는데 지금 상태에서 도면을 고칠 수 없으면
     * @throws IllegalArgumentException                    다른 행사의 도면이면
     */
    public boolean moveBlock(FloorPlan floorPlan, long blockId, CanvasPoint newPosition, Instant now) {
        requireOwnFloorPlan(floorPlan);
        Objects.requireNonNull(now, "now");
        boolean moved = floorPlan.moveBlock(blockId, newPosition, () -> requireEditable(EventEditTarget.FLOOR_PLAN));
        return completeWizardStep(EventWizardStep.SEATING, moved, now);
    }

    /**
     * 위저드 3단계에서 블록을 돌린다(부채꼴 객석) — 좌석은 블록 안의 자리 · 번호 그대로 함께 돌아간다. 각도가 바뀔 때만 도면의 수정 범위를
     * 확인한다. 나머지는 {@link #moveBlock}과 같다.
     *
     * @return 바뀐 것(블록 각도 · 진행 단계)이 있는지
     * @throws com.ticketon.domain.error.NotFoundException 도면에 없는 블록이면({@link EventErrorCode#BLOCK_NOT_FOUND})
     * @throws RuleViolationException                      각도가 바뀌는데 지금 상태에서 도면을 고칠 수 없으면
     * @throws IllegalArgumentException                    다른 행사의 도면이면
     */
    public boolean rotateBlock(FloorPlan floorPlan, long blockId, BlockAngle newAngle, Instant now) {
        requireOwnFloorPlan(floorPlan);
        Objects.requireNonNull(now, "now");
        boolean rotated = floorPlan.rotateBlock(blockId, newAngle, () -> requireEditable(EventEditTarget.FLOOR_PLAN));
        return completeWizardStep(EventWizardStep.SEATING, rotated, now);
    }

    /**
     * 위저드 3단계에서 블록을 그 안의 좌석과 함께 지운다. 지운 블록 · 좌석은 저장소가 지운 시각을 적어 남긴다 — 취소 · 회수된 티켓은 원래 좌석을
     * 그대로 가리킨다(docs/14). 도면의 수정 범위({@link EventEditTarget#FLOOR_PLAN})를 확인한다. 티켓이 걸린 좌석이 있는 블록을 지울 수 없는
     * 규칙은 #64가 더한다. 작성 중이면 진행 단계를 3단계까지로 올리고, 마지막 수정 시각을 지금으로 한다.
     *
     * @return 지운 블록(좌석 포함) — 재고에 알리고 조작 기록을 남기는 데 쓴다
     * @throws com.ticketon.domain.error.NotFoundException 도면에 없는 블록이면({@link EventErrorCode#BLOCK_NOT_FOUND})
     * @throws RuleViolationException                      지금 상태에서 도면을 고칠 수 없으면({@link #requireEditable}과 같은 오류)
     * @throws IllegalArgumentException                    다른 행사의 도면이면
     */
    public SeatBlock removeBlock(FloorPlan floorPlan, long blockId, Instant now) {
        requireOwnFloorPlan(floorPlan);
        Objects.requireNonNull(now, "now");
        floorPlan.block(blockId);
        requireEditable(EventEditTarget.FLOOR_PLAN);
        SeatBlock removed = floorPlan.removeBlock(blockId);
        completeWizardStep(EventWizardStep.SEATING, true, now);
        return removed;
    }

    /**
     * 위저드 3단계에서 좌석들을 지정형 구역에 배정한다(색칠). 하나라도 바뀔 때만 도면의 수정 범위({@link EventEditTarget#FLOOR_PLAN})를 확인한다
     * — 모두 이미 그 구역이면 보지 않는다. 묶음 안에 도면에 없는 좌석이 하나라도 있으면 아무것도 바꾸지 않는다. 진행 단계 · 마지막 수정 시각은
     * {@link #renameBlock}과 같다.
     *
     * @return 바뀐 것(좌석 · 진행 단계)이 있는지
     * @throws com.ticketon.domain.error.NotFoundException 도면에 없는 구역 · 좌석이면({@link EventErrorCode#ZONE_NOT_FOUND},
     *                                                     {@link EventErrorCode#SEAT_NOT_FOUND})
     * @throws RuleViolationException                      바뀌는데 지금 상태에서 도면을 고칠 수 없으면, 수량형 구역이면
     *                                                     ({@link EventErrorCode#QUANTITY_ZONE_HAS_NO_SEATS})
     * @throws IllegalArgumentException                    다른 행사의 도면이면
     */
    public boolean assignSeatsToZone(FloorPlan floorPlan, Set<Long> seatIds, long zoneId, Instant now) {
        requireOwnFloorPlan(floorPlan);
        Objects.requireNonNull(now, "now");
        boolean assigned = floorPlan.assignSeatsToZone(seatIds, zoneId, () -> requireEditable(EventEditTarget.FLOOR_PLAN));
        return completeWizardStep(EventWizardStep.SEATING, assigned, now);
    }

    /**
     * 위저드 3단계에서 좌석들의 구역 배정을 푼다(색 지우기). 나머지는 {@link #assignSeatsToZone}과 같다.
     *
     * @return 바뀐 것(좌석 · 진행 단계)이 있는지
     * @throws com.ticketon.domain.error.NotFoundException 도면에 없는 좌석이면({@link EventErrorCode#SEAT_NOT_FOUND})
     * @throws RuleViolationException                      바뀌는데 지금 상태에서 도면을 고칠 수 없으면
     * @throws IllegalArgumentException                    다른 행사의 도면이면
     */
    public boolean unassignSeats(FloorPlan floorPlan, Set<Long> seatIds, Instant now) {
        requireOwnFloorPlan(floorPlan);
        Objects.requireNonNull(now, "now");
        boolean unassigned = floorPlan.unassignSeats(seatIds, () -> requireEditable(EventEditTarget.FLOOR_PLAN));
        return completeWizardStep(EventWizardStep.SEATING, unassigned, now);
    }

    /**
     * 위저드 3단계에서 좌석들의 속성(일반 · 휠체어 · 시야제한)을 정한다 — 물리적 사실이라 도면에 붙는다(docs/02 "좌석 속성"). 나머지는
     * {@link #assignSeatsToZone}과 같다.
     *
     * @return 바뀐 것(좌석 · 진행 단계)이 있는지
     * @throws com.ticketon.domain.error.NotFoundException 도면에 없는 좌석이면({@link EventErrorCode#SEAT_NOT_FOUND})
     * @throws RuleViolationException                      바뀌는데 지금 상태에서 도면을 고칠 수 없으면
     * @throws IllegalArgumentException                    다른 행사의 도면이면
     */
    public boolean changeSeatAttribute(FloorPlan floorPlan, Set<Long> seatIds, SeatAttribute attribute, Instant now) {
        requireOwnFloorPlan(floorPlan);
        Objects.requireNonNull(now, "now");
        boolean changed = floorPlan.changeSeatAttribute(seatIds, attribute, () -> requireEditable(EventEditTarget.FLOOR_PLAN));
        return completeWizardStep(EventWizardStep.SEATING, changed, now);
    }

    /**
     * 위저드 3단계에서 좌석들을 끄거나(통로 · 기둥 같은 빈 자리) 다시 켠다(docs/02 "블록 안 개별 좌석 비활성화"). 번호는 다시 매기지 않고 구역 ·
     * 속성은 그대로 둔다. 티켓이 걸린 좌석을 끌 수 없는 규칙은 #64가 더한다. 나머지는 {@link #assignSeatsToZone}과 같다.
     *
     * @param active 켤지(true) 끌지(false)
     * @return 바뀐 것(좌석 · 진행 단계)이 있는지
     * @throws com.ticketon.domain.error.NotFoundException 도면에 없는 좌석이면({@link EventErrorCode#SEAT_NOT_FOUND})
     * @throws RuleViolationException                      바뀌는데 지금 상태에서 도면을 고칠 수 없으면
     * @throws IllegalArgumentException                    다른 행사의 도면이면
     */
    public boolean changeSeatActivation(FloorPlan floorPlan, Set<Long> seatIds, boolean active, Instant now) {
        requireOwnFloorPlan(floorPlan);
        Objects.requireNonNull(now, "now");
        boolean changed = floorPlan.changeSeatActivation(seatIds, active, () -> requireEditable(EventEditTarget.FLOOR_PLAN));
        return completeWizardStep(EventWizardStep.SEATING, changed, now);
    }

    /**
     * 위저드 3단계에서 무대 위치를 정한다(docs/02 "에디터는 블록 단위" — 무대 위치 지정). 위치가 바뀔 때만 도면의 수정 범위를 확인한다. 진행 단계 ·
     * 마지막 수정 시각은 {@link #renameBlock}과 같다.
     *
     * @return 바뀐 것(무대 위치 · 진행 단계)이 있는지
     * @throws RuleViolationException   위치가 바뀌는데 지금 상태에서 도면을 고칠 수 없으면({@link #requireEditable}과 같은 오류)
     * @throws IllegalArgumentException 다른 행사의 도면이면
     */
    public boolean placeStage(FloorPlan floorPlan, CanvasPoint position, Instant now) {
        requireOwnFloorPlan(floorPlan);
        Objects.requireNonNull(now, "now");
        boolean placed = floorPlan.placeStage(position, () -> requireEditable(EventEditTarget.FLOOR_PLAN));
        return completeWizardStep(EventWizardStep.SEATING, placed, now);
    }

    /**
     * 위저드 6단계에서 이 행사의 입구 목록에 입구를 더한다(docs/02 "입구 (게이트)"). 입구 목록은 판매가 끝난 뒤에도 행사가 끝날 때까지 고친다 —
     * 18:10에 정문 줄이 길어 "옆문"을 더한다(docs/04 "판매 종료는 공연이 끝난 때가 아닙니다", {@link EventEditTarget#ENTRANCES}).
     * <p>
     * 수정 범위를 확인하고, 입구를 더하고, 작성 중이면 진행 단계를 6단계까지로 올리고, 마지막 수정 시각을 지금으로 한다. {@link #requireEditable}처럼
     * 저장된 상태로 판정한다.
     *
     * @return 더한 입구(아직 id 없음)
     * @throws RuleViolationException   지난 행사면({@link EventErrorCode#EDIT_LOCKED_FOR_PAST_EVENT}), 같은 이름의 입구가 있으면
     *                                  ({@link EventErrorCode#ENTRANCE_NAME_ALREADY_TAKEN})
     * @throws IllegalArgumentException 다른 행사의 입구 목록이면
     */
    public Entrance addEntrance(EventEntrances entrances, EntranceName name, Instant now) {
        requireOwnEntrances(entrances);
        Objects.requireNonNull(now, "now");
        requireEditable(EventEditTarget.ENTRANCES);
        Entrance added = entrances.add(name);
        completeWizardStep(EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS, true, now);
        return added;
    }

    /**
     * 위저드 6단계에서 이 행사의 입구 이름을 바꾼다. 지금과 같은 이름이면 수정 범위를 보지 않고 입구도 그대로다 — 같은 값을 몇 번 저장해도 안전하다.
     * 작성 중이면 진행 단계를 6단계까지로 올리고, 무엇이든 바뀌었으면 마지막 수정 시각을 지금으로 한다.
     *
     * @return 바뀐 것(입구 이름 · 진행 단계)이 있는지
     * @throws com.ticketon.domain.error.NotFoundException 목록에 없는 입구면({@link EventErrorCode#ENTRANCE_NOT_FOUND})
     * @throws RuleViolationException                      지난 행사면({@link EventErrorCode#EDIT_LOCKED_FOR_PAST_EVENT}), 다른 입구가 쓰는
     *                                                     이름이면({@link EventErrorCode#ENTRANCE_NAME_ALREADY_TAKEN})
     * @throws IllegalArgumentException                    다른 행사의 입구 목록이면
     */
    public boolean renameEntrance(EventEntrances entrances, long entranceId, EntranceName newName, Instant now) {
        requireOwnEntrances(entrances);
        Objects.requireNonNull(newName, "newName");
        Objects.requireNonNull(now, "now");
        boolean renamed = !entrances.entrance(entranceId).name().equals(newName);
        if (renamed) {
            requireEditable(EventEditTarget.ENTRANCES);
            entrances.rename(entranceId, newName);
        }
        return completeWizardStep(EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS, renamed, now);
    }

    /**
     * 위저드 6단계에서 이 행사의 입구를 지운다. 지운 입구는 저장소가 지운 시각을 적어 남긴다 — 그 입구를 가리키는 입장 기록이 남는다(docs/14
     * "입장 기록 — 티켓 + 회차 + 입구"). 작성 중이면 진행 단계를 6단계까지로 올리고, 마지막 수정 시각을 지금으로 한다.
     *
     * @return 지운 입구 — 조작 기록을 남기는 데 쓴다
     * @throws com.ticketon.domain.error.NotFoundException 목록에 없는 입구면({@link EventErrorCode#ENTRANCE_NOT_FOUND})
     * @throws RuleViolationException                      지난 행사면({@link EventErrorCode#EDIT_LOCKED_FOR_PAST_EVENT})
     * @throws IllegalArgumentException                    다른 행사의 입구 목록이면
     */
    public Entrance removeEntrance(EventEntrances entrances, long entranceId, Instant now) {
        requireOwnEntrances(entrances);
        Objects.requireNonNull(now, "now");
        Entrance entrance = entrances.entrance(entranceId);
        requireEditable(EventEditTarget.ENTRANCES);
        entrances.remove(entranceId);
        completeWizardStep(EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS, true, now);
        return entrance;
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
        moveTo(advanced, now);
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
     * 주최자가 판매를 잠시 멈춘다 — 기존 예매는 유지되고 신규 판매만 멈춘다. 목록에서 숨기지 않는다(docs/04 "판매 중단"). 먼저 지금 시각을 반영한다.
     *
     * @param staffAccountId 멈춘 운영진의 계정 id
     * @param reason         한 줄 사유. 적지 않았으면 null
     * @throws RuleViolationException 이미 판매 중단이면({@link EventErrorCode#SALES_ALREADY_PAUSED}), 판매 중이 아니면
     *                                ({@link EventErrorCode#SALES_NOT_PAUSABLE})
     */
    public void pauseSales(long staffAccountId, SalesPauseReason reason, EventSchedule schedule, Instant now) {
        advanceByTime(schedule, now);
        if (status == EventStatus.SALES_PAUSED) {
            throw new RuleViolationException(EventErrorCode.SALES_ALREADY_PAUSED, detail());
        }
        if (status != EventStatus.ON_SALE) {
            throw new RuleViolationException(EventErrorCode.SALES_NOT_PAUSABLE, detail());
        }
        salesPauses.add(SalesPause.byOrganizer(staffAccountId, reason, now));
        status = EventStatus.SALES_PAUSED;
    }

    /**
     * 관리자 조치(정지 2단계 · 페이지 내리기)로 판매를 멈춘다 — 정지 기능(#122) · 페이지 내리기(#120)가 부른다. 먼저 지금 시각을 반영한다.
     * <ul>
     *   <li>판매 중이면 판매 중단이 된다.</li>
     *   <li>이미 판매 중단이면(주최자가 먼저 멈춰 뒀거나 다른 관리자 조치가 걸려 있으면) 상태는 그대로이고 이 조치가 더 걸린다 — 조치가 모두 풀려야
     *       주최자가 재개한다.</li>
     *   <li>확인 필요 (#38) — 판매 대기도 판매 중단이 된다. 정지 2단계의 대상은 "진행 중인 행사"(판매 대기 · 판매 중 · 판매 종료, docs/13)이고
     *       조치 중에 오픈 시각이 와서 판매가 열리면 안 되므로 오픈 전부터 멈춘다. 조치가 풀린 뒤 주최자가 재개하면 시각대로 판매 대기 또는 판매
     *       중이 된다. 페이지 내리기는 문서가 "판매 중이었다면 판매 중단"이라 하지만, 다시 올린 뒤 판매 재개는 주최자가 한다는 원칙과 맞춰 같게
     *       둔다. 이렇게 멈춘 동안은 예매가 열린 적이 없어 수정 범위가 판매 대기와 같다({@link #editScope}).</li>
     *   <li>작성 중이거나 판매가 끝난 뒤(판매 종료 · 지난 행사)에는 멈출 판매가 없어 아무것도 하지 않는다.</li>
     * </ul>
     *
     * @param cause          관리자 조치 — 정지 2단계 또는 페이지 내리기
     * @param adminAccountId 조치한 관리자의 계정 id
     * @return 조치가 걸렸는지 — 멈출 판매가 없거나 같은 조치가 이미 걸려 있으면 false
     * @throws IllegalArgumentException 관리자 조치가 아니면
     */
    public boolean holdSales(SalesPauseCause cause, long adminAccountId, EventSchedule schedule, Instant now) {
        Objects.requireNonNull(cause, "cause");
        requireAdminAction(cause);
        advanceByTime(schedule, now);
        switch (status) {
            case AWAITING_OPEN, ON_SALE -> status = EventStatus.SALES_PAUSED;
            case SALES_PAUSED -> {
                if (activeSalesHolds().contains(cause)) {
                    return false;
                }
            }
            case DRAFT, SALES_CLOSED, ENDED, SETTLED, CANCELLED -> {
                return false;
            }
        }
        salesPauses.add(SalesPause.byAdmin(cause, adminAccountId, now));
        return true;
    }

    /**
     * 관리자 조치를 푼다(정지 해제 · 페이지 다시 올리기). <b>판매는 저절로 열리지 않는다</b> — 상태는 판매 중단 그대로이고, 조치가 모두 풀리면
     * 주최자가 재개할 수 있다(docs/01 "해제해도 판매는 저절로 열리지 않습니다"). 판매 종료로 넘어간 뒤에 풀어도 이력의 끝난 시각은 남긴다.
     *
     * @return 걸려 있던 조치가 풀렸는지 — 걸려 있지 않았으면 false
     * @throws IllegalArgumentException 관리자 조치가 아니면
     */
    public boolean releaseSalesHold(SalesPauseCause cause, Instant now) {
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(now, "now");
        requireAdminAction(cause);
        return endActivePause(cause, now);
    }

    /**
     * 주최자가 멈춘 판매를 다시 연다. 먼저 지금 시각을 반영한다. 예매가 열린 적이 없고 오픈 시각 전이면(판매 대기에서 관리자 조치로 멈췄던 행사)
     * 판매 대기로, 아니면 판매 중으로 돌아간다. 주최자가 멈춘 중단이 있으면 그 이력이 이 시각에 끝난다.
     *
     * @throws RuleViolationException 판매가 끝난 뒤면({@link EventErrorCode#SALES_ALREADY_CLOSED}), 판매 중단이 아니면
     *                                ({@link EventErrorCode#SALES_NOT_PAUSED}), 관리자 조치가 걸려 있으면
     *                                ({@link EventErrorCode#SALES_HELD_BY_ADMIN})
     */
    public void resumeSales(EventSchedule schedule, Instant now) {
        advanceByTime(schedule, now);
        if (status.isSalesOver()) {
            throw new RuleViolationException(EventErrorCode.SALES_ALREADY_CLOSED, detail());
        }
        if (status != EventStatus.SALES_PAUSED) {
            throw new RuleViolationException(EventErrorCode.SALES_NOT_PAUSED, detail());
        }
        if (!activeSalesHolds().isEmpty()) {
            throw new RuleViolationException(EventErrorCode.SALES_HELD_BY_ADMIN, detail() + ", 조치: " + activeSalesHolds());
        }
        endActivePause(SalesPauseCause.ORGANIZER, now);
        moveTo(hasBookingOpened() || isBookingOpenAt(now) ? EventStatus.ON_SALE : EventStatus.AWAITING_OPEN, now);
    }

    /** 지금 주최자의 [재개] 버튼 — 지금 시각이 정하는 상태({@link #statusAt})로 본다. 판매가 끝났으면 버튼이 사라진다. */
    public SalesResumeOption salesResumeOption(EventSchedule schedule, Instant now) {
        if (statusAt(schedule, now) != EventStatus.SALES_PAUSED) {
            return SalesResumeOption.NOT_PAUSED;
        }
        return activeSalesHolds().isEmpty() ? SalesResumeOption.AVAILABLE : SalesResumeOption.HELD_BY_ADMIN;
    }

    /**
     * 지금 저장된 상태에서 그 항목을 고칠 수 있는지 — 상태별 수정 범위 표로 판정한다({@link #editScope}, {@link EventEditScope#availabilityOf}).
     * <p>
     * 저장된 상태로 판정한다. 시간이 움직이는 상태({@link EventStatus#movesWithTime})의 행사는 판정 전에 {@link #advanceByTime}으로 지금 시각을
     * 반영한다 — 그런 행사는 판매를 시작할 때 회차가 있었으므로 늘 일정이 있다. 일정이 아직 없을 수 있는 작성 중 행사는 시간이 움직이지 않아 그대로
     * 부른다.
     */
    public EventEditAvailability editAvailability(EventEditTarget target) {
        return editScope().availabilityOf(target);
    }

    /**
     * 그 항목을 주최자가 지금 고칠 수 있는지 확인한다({@link #editAvailability}와 같은 판정). 항목 규칙이 붙으면
     * ({@link EventEditAvailability#ALLOWED_WITH_RULES}) 막지 않고 그대로 돌려준다 — 그 규칙은 그 항목을 고치는 곳이 본다.
     *
     * @return 고칠 수 있음 또는 항목 규칙을 지켜 고칠 수 있음
     * @throws RuleViolationException 예매가 열린 뒤 바꿀 수 없는 항목이면({@link EventErrorCode#EDIT_LOCKED_AFTER_BOOKING_OPENED}), 예매가 열린
     *                                뒤 장소면({@link EventErrorCode#VENUE_CHANGE_NEEDS_ADMIN_APPROVAL}), 판매가 끝난 뒤 입구 목록 · 검표 계정이
     *                                아니면({@link EventErrorCode#EDIT_LOCKED_AFTER_SALES_CLOSED}), 지난 행사면
     *                                ({@link EventErrorCode#EDIT_LOCKED_FOR_PAST_EVENT})
     */
    public EventEditAvailability requireEditable(EventEditTarget target) {
        EventEditAvailability availability = editAvailability(target);
        if (availability.isEditable()) {
            return availability;
        }
        String detail = detail() + ", 항목: " + target;
        if (availability == EventEditAvailability.NEEDS_ADMIN_APPROVAL) {
            throw new RuleViolationException(EventErrorCode.VENUE_CHANGE_NEEDS_ADMIN_APPROVAL, detail);
        }
        throw new RuleViolationException(lockedErrorCode(editScope()), detail);
    }

    /**
     * 그 회차의 시간(종일형은 그날의 운영 시간)을 주최자가 혼자 지금 바꿀 수 있는지 확인한다. 상태별 수정 범위({@link EventEditTarget#SESSION_DATE_TIME})를
     * 보고, 예매가 열린 뒤({@link #hasBookingOpened})면 그 회차 시작 1주일 전부터 잠근다({@link SessionTimes#isTimeEditLocked}). 잠금은 회차마다 따로다 —
     * 8회차 공연이 이미 시작했어도 아직 열흘 남은 마지막 주 회차는 바꿀 수 있다(docs/04 "회차 시간 잠금은 회차마다 따로입니다"). 판매 대기는 예매가
     * 없어 잠그지 않는다 — 판매 대기에서 관리자 조치로 멈춰 예매가 열린 적이 없는 판매 중단도 같다. 잠긴 뒤에는 연기 절차를 쓰고(#106), 비어 있던 종료 시각을 채우는 것만은 받는다({@link EventEditTarget#MISSING_SESSION_END_TIME}).
     * <p>
     * {@link #requireEditable}처럼 저장된 상태로 판정한다. 티켓이 있는 회차의 알림 · 공제 면제는 회차 시간 수정(#105)이 한다.
     *
     * @throws RuleViolationException 상태가 회차 일시를 막으면({@link #requireEditable}과 같은 오류), 예매가 열린 뒤 그 회차 시작 1주일 전이
     *                                지났으면({@link EventErrorCode#SESSION_TIME_EDIT_LOCKED})
     * @throws com.ticketon.domain.error.NotFoundException 일정에 없는 회차면({@link EventErrorCode#SESSION_NOT_FOUND})
     */
    public void requireSessionTimeEditable(EventSchedule schedule, long sessionId, Instant now) {
        requireOwnSchedule(schedule);
        Objects.requireNonNull(now, "now");
        requireEditable(EventEditTarget.SESSION_DATE_TIME);
        if (editScope() == EventEditScope.AFTER_BOOKING_OPENED
                && schedule.timesOf(schedule.session(sessionId)).isTimeEditLocked(now)) {
            throw new RuleViolationException(EventErrorCode.SESSION_TIME_EDIT_LOCKED, detail() + ", 회차 " + sessionId);
        }
    }

    /**
     * 이 행사를 지울 때 확인할 운영진의 일 — 작성 중은 운영진 누구나({@link StaffAction#DELETE_EVENT_BEFORE_SALES_STARTED}), 판매를 시작한 뒤에는
     * 소유자만({@link StaffAction#DELETE_EVENT_AFTER_SALES_STARTED})(docs/04 "행사 삭제", docs/01 "권한 두 가지"). 그 일을 할 수 있는지는 운영진
     * 권한 확인이 본다.
     */
    public StaffAction deleteAction() {
        return status.hasStartedSales() ? StaffAction.DELETE_EVENT_AFTER_SALES_STARTED : StaffAction.DELETE_EVENT_BEFORE_SALES_STARTED;
    }

    /**
     * 지울 수 있는지 확인한다. <b>결제된 예매와 초대권이 한 번도 없었으면 상태와 상관없이 지울 수 있다</b> — 가격을 잘못 넣고 판매를 시작했는데 아직
     * 0건이면 지우고 새로 만든다(docs/04 "행사 삭제", docs/15 박정훈). 한 장이라도 팔렸거나 초대권을 발권했으면 전부 취소 · 회수됐어도 지울 수 없다 —
     * 결제 · 환불 기록이 법정 보존 대상이고 초대권 링크가 이미 나갔다. 그때는 행사 취소를 쓴다.
     *
     * @param everHadTicket 이 행사에 티켓(결제된 예매 · 초대권)이 한 번이라도 있었는지 — 취소 · 회수된 티켓도 센다. 티켓은 위 층이 알므로 부르는
     *                      쪽이 조회해 넘긴다(#66)
     * @throws RuleViolationException 티켓이 있었으면({@link EventErrorCode#EVENT_HAD_TICKETS})
     */
    public void requireDeletable(boolean everHadTicket) {
        if (everHadTicket) {
            throw new RuleViolationException(EventErrorCode.EVENT_HAD_TICKETS, detail());
        }
    }

    /**
     * 지금 저장된 상태의 수정 범위 — 상태와 예매가 한 번이라도 열렸는지로 정한다({@link EventEditScope#of}). 판매 중단은 예매가 열린 뒤면
     * '예매가 열린 뒤' 범위, 판매 대기에서 관리자 조치로 멈춰 예매가 열린 적이 없으면 판매 대기와 같은 범위다.
     */
    public EventEditScope editScope() {
        return EventEditScope.of(status, hasBookingOpened());
    }

    /**
     * 예매가 한 번이라도 열렸는지 — 판매 중이 된 적이 있는지. 예매는 판매 중일 때만 들어오므로, 판매 대기에서 관리자 조치로 멈춘 행사는 멈춘 동안
     * 오픈 시각이 지나도 예매가 열린 적이 없다. docs/04 "판매 대기(판매 시작을 눌렀지만 오픈 전)는 예매가 없어 전부 고칠 수 있습니다"가 그 행사에도
     * 그대로 맞으므로, '예매가 열린 뒤' 수정 범위와 회차 시간 1주일 잠금은 상태 이름이 아니라 이것으로 정한다.
     */
    public boolean hasBookingOpened() {
        return bookingOpenedAt != null;
    }

    /** 처음 판매 중이 된 시각 — 그 전환을 상태에 반영한 시각이다. 예매가 열린 적이 없으면 비어 있다. */
    public Optional<Instant> bookingOpenedAt() {
        return Optional.ofNullable(bookingOpenedAt);
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

    /** 판매 중단 이력 — 멈춘 순서대로. 상태가 덮여도 남는다. */
    public List<SalesPause> salesPauses() {
        return Collections.unmodifiableList(salesPauses);
    }

    /**
     * 작성 진행 단계 — 작성 중에 저장한 가장 뒤의 위저드 단계. 만들 때 1단계이고, 앞 단계를 다시 저장해도 내려가지 않는다. 작성 중 행사 목록이
     * "4/7 단계 · 가격 설정까지 완료"처럼 보이고, [이어서 작성]이 그다음 단계를 연다(docs/04 "작성 중 행사 관리"). 판매를 시작한 뒤에는 더
     * 움직이지 않는다 — 위저드는 설정 편집 화면으로 다시 쓰이지만 작성은 끝났다.
     */
    public EventWizardStep wizardProgress() {
        return wizardProgress;
    }

    /** 마지막 수정 시각 — 단계를 저장해 무엇이든 바뀐 시각. 같은 값을 다시 저장하면 그대로다. 작성 중 행사 목록의 "마지막 수정 3일 전". */
    public Instant lastModifiedAt() {
        return lastModifiedAt;
    }

    /** 걸려 있는(아직 풀리지 않은) 관리자 조치. 판매가 끝난 뒤에도 풀기 전까지 남는다. */
    public Set<SalesPauseCause> activeSalesHolds() {
        Set<SalesPauseCause> holds = EnumSet.noneOf(SalesPauseCause.class);
        salesPauses.stream()
                .filter(SalesPause::isActive)
                .map(SalesPause::cause)
                .filter(SalesPauseCause::isAdminAction)
                .forEach(holds::add);
        return holds;
    }

    /** 지금 판매 중단에 걸린 주최자의 중단 — 관객에게 보일 한 줄 사유가 여기 있다. 판매 중단 상태가 아니면 비어 있다. */
    public Optional<SalesPause> activeOrganizerPause() {
        if (status != EventStatus.SALES_PAUSED) {
            return Optional.empty();
        }
        return salesPauses.stream()
                .filter(pause -> pause.cause() == SalesPauseCause.ORGANIZER && pause.isActive())
                .findFirst();
    }

    /**
     * 위저드 단계를 저장한 뒤의 공통 처리 — 작성 중이면 진행 단계를 그 단계까지로 올리고(내려가지 않는다), 값이나 진행 단계가 바뀌었으면 마지막
     * 수정 시각을 지금으로 한다.
     *
     * @return 값이나 진행 단계가 바뀌었는지
     */
    private boolean completeWizardStep(EventWizardStep savedStep, boolean valuesChanged, Instant now) {
        boolean progressed = status == EventStatus.DRAFT && savedStep.isAfter(wizardProgress);
        if (progressed) {
            wizardProgress = savedStep;
        }
        boolean changed = valuesChanged || progressed;
        if (changed) {
            lastModifiedAt = now;
        }
        return changed;
    }

    /** 종일형 회차의 날짜를 바꾼다 — 시작 · 종료 시각은 받지 않는다. */
    private boolean changeAllDaySessionDate(EventSchedule schedule, EventSession session, LocalDate currentDate, LocalDate newDate,
            LocalTime newStartTime, LocalTime newEndTime, Instant now) {
        long sessionId = session.id().orElseThrow();
        if (newStartTime != null || newEndTime != null) {
            throw new RuleViolationException(EventErrorCode.ALL_DAY_SINGLE_SESSION_PER_DATE, detail() + ", 회차 " + sessionId);
        }
        if (currentDate.equals(newDate)) {
            return false;
        }
        requireSessionTimeEditable(schedule, sessionId, now);
        return schedule.changeAllDaySessionDate(sessionId, newDate);
    }

    /** 회차형 회차의 날짜 · 시간을 바꾼다. 비어 있던 종료 시각만 채우면 시간 수정이 잠긴 뒤에도 받는다. */
    private boolean changeTimedSessionDateTime(EventSchedule schedule, EventSession session, LocalDate currentDate, LocalDate newDate,
            LocalTime newStartTime, LocalTime newEndTime, Instant now) {
        long sessionId = session.id().orElseThrow();
        if (newStartTime == null) {
            throw new RuleViolationException(EventErrorCode.SESSION_START_TIME_REQUIRED, detail() + ", 회차 " + sessionId);
        }
        TimedSessionTime newTime = TimedSessionTime.of(newStartTime, newEndTime);
        TimedSessionTime currentTime = session.time().orElseThrow();
        if (currentDate.equals(newDate) && currentTime.equals(newTime)) {
            return false;
        }
        boolean fillsMissingEndTime = currentDate.equals(newDate) && currentTime.startTime().equals(newTime.startTime())
                && currentTime.end().isEmpty() && newTime.end().isPresent();
        if (fillsMissingEndTime) {
            requireEditable(EventEditTarget.MISSING_SESSION_END_TIME);
            schedule.enterSessionEndTime(sessionId, newTime.endTime());
            return true;
        }
        requireSessionTimeEditable(schedule, sessionId, now);
        return schedule.changeTimedSessionDateTime(sessionId, newDate, newTime);
    }

    /**
     * 운영 시간을 바꾸면 시간이 따라 바뀌는 회차마다 시간 수정을 확인한다 — 판정은 {@link #requireSessionTimeEditable} 하나다. 따라 바뀌는 회차가 없으면
     * 회차 일시의 수정 범위만 본다. 저장소에서 읽은 일정의 회차(id 있음)를 넘긴다.
     *
     * @throws IllegalArgumentException 저장 전(id 없는) 회차가 있으면
     */
    private void requireSessionTimesEditable(EventSchedule schedule, List<EventSession> affectedSessions, Instant now) {
        if (affectedSessions.isEmpty()) {
            requireEditable(EventEditTarget.SESSION_DATE_TIME);
            return;
        }
        affectedSessions.forEach(session -> requireSessionTimeEditable(schedule, session.id().orElseThrow(
                () -> new IllegalArgumentException("저장 전 회차의 시간 수정은 판정하지 않습니다 — 저장소에서 읽은 일정을 넘깁니다: 행사 " + id)),
                now));
    }

    /** 기본 운영 시간을 따르는 회차 — 운영 시간을 덮어쓰지 않은 날짜의 회차(종일형). */
    private static List<EventSession> sessionsFollowingDefaultOperatingHours(EventSchedule schedule) {
        return schedule.dates().stream()
                .filter(eventDate -> eventDate.operatingHoursOverride().isEmpty())
                .flatMap(eventDate -> eventDate.sessions().stream())
                .toList();
    }

    /** 종일형은 기본 운영 시간이 있고 회차형은 없다. */
    private static void requireOperatingHoursMatchMode(SessionMode mode, OperatingHours defaultOperatingHours) {
        if ((mode == SessionMode.ALL_DAY) != (defaultOperatingHours != null)) {
            throw new IllegalArgumentException("기본 운영 시간은 종일형만 갖고, 종일형에는 꼭 있습니다: " + mode);
        }
    }

    private static EventErrorCode lockedErrorCode(EventEditScope scope) {
        return switch (scope) {
            case AFTER_BOOKING_OPENED -> EventErrorCode.EDIT_LOCKED_AFTER_BOOKING_OPENED;
            case ON_SITE_ONLY -> EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED;
            case NOTHING -> EventErrorCode.EDIT_LOCKED_FOR_PAST_EVENT;
            case EVERYTHING, EVERYTHING_EXCEPT_INVITED -> throw new IllegalStateException("전부 고치는 범위에는 잠긴 항목이 없습니다: " + scope);
        };
    }

    private boolean endActivePause(SalesPauseCause cause, Instant now) {
        for (int index = 0; index < salesPauses.size(); index++) {
            SalesPause pause = salesPauses.get(index);
            if (pause.cause() == cause && pause.isActive()) {
                salesPauses.set(index, pause.end(now));
                return true;
            }
        }
        return false;
    }

    private static void requireAdminAction(SalesPauseCause cause) {
        if (!cause.isAdminAction()) {
            throw new IllegalArgumentException("관리자 조치가 아닙니다: " + cause);
        }
    }

    private void moveTo(EventStatus next, Instant now) {
        status = next;
        if (next == EventStatus.ON_SALE && bookingOpenedAt == null) {
            bookingOpenedAt = now;
        }
    }

    private void requireConsistentBookingOpening() {
        boolean beforeSalesStartedOrOpened = status == EventStatus.DRAFT || status == EventStatus.AWAITING_OPEN;
        if (beforeSalesStartedOrOpened && bookingOpenedAt != null) {
            throw new IllegalArgumentException("예매가 열리기 전 상태인데 예매가 열린 시각이 있습니다: 행사 " + id + ", " + status);
        }
        if (status == EventStatus.ON_SALE && bookingOpenedAt == null) {
            throw new IllegalArgumentException("판매 중인데 예매가 열린 시각이 없습니다: 행사 " + id);
        }
    }

    private void requireConsistentSalesPauses() {
        salesPauses.forEach(pause -> Objects.requireNonNull(pause, "salesPause"));
        if (status == EventStatus.DRAFT && !salesPauses.isEmpty()) {
            throw new IllegalArgumentException("판매를 시작하기 전 행사에 판매 중단 이력이 있습니다: 행사 " + id);
        }
        Set<SalesPauseCause> activeCauses = EnumSet.noneOf(SalesPauseCause.class);
        for (SalesPause pause : salesPauses) {
            if (pause.isActive() && !activeCauses.add(pause.cause())) {
                throw new IllegalArgumentException("같은 까닭의 끝나지 않은 판매 중단이 둘입니다: 행사 " + id + ", " + pause.cause());
            }
        }
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

    private void requireOwnFloorPlan(FloorPlan floorPlan) {
        Objects.requireNonNull(floorPlan, "floorPlan");
        requireSaved("도면");
        if (floorPlan.eventId() != id) {
            throw new IllegalArgumentException("행사 " + id + "에 다른 행사(" + floorPlan.eventId() + ")의 도면을 넘겼습니다");
        }
    }

    private void requireOwnEntrances(EventEntrances entrances) {
        Objects.requireNonNull(entrances, "entrances");
        requireSaved("입구 목록");
        if (entrances.eventId() != id) {
            throw new IllegalArgumentException("행사 " + id + "에 다른 행사(" + entrances.eventId() + ")의 입구 목록을 넘겼습니다");
        }
    }

    private void requireSaved(String what) {
        if (id == null) {
            throw new IllegalStateException("저장 전 행사는 " + what + "이 없습니다");
        }
    }

    private String detail() {
        return "행사 " + id + ", 상태: " + status;
    }
}
