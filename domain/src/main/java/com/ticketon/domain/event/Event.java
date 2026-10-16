package com.ticketon.domain.event;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 행사 — 주최자가 여는 하나의 공연 · 전시 · 페스티벌. 주최자에 속한다(docs/00 "용어", docs/14 "무엇이 무엇을 가리키나").
 * <p>
 * 지금은 위저드 1단계의 기본 정보와 공개 수준, 공개 주소를 갖는다.
 * <ul>
 *   <li><b>기본 정보</b>({@link EventBasicInformation}) — 제목 · 유형 · 카테고리 · 소개 · 장소 등. 행사를 만들 때 함께 받는다 — 위저드는
 *       단계를 넘길 때마다 저장하므로(docs/03 "임시저장은 자동으로") 1단계를 마쳐야 처음 저장된다.</li>
 *   <li><b>포스터</b>({@link EventPoster}) — 1단계의 필수 항목이지만 행사를 만든 뒤 따로 올린다. 그래서 작성 중에는 없을 수 있고, 판매를
 *       시작하기 전에 갖춰야 한다({@link #isBasicInformationComplete}).</li>
 *   <li><b>공개 수준</b>({@link EventVisibilitySetting}) — 공개로 시작한다(docs/04 "행사 복제 — 공개 수준은 기본값 공개로 시작").</li>
 *   <li><b>공개 주소</b>({@link EventPublicAddress}) — 만들 때 발급하고 바뀌지 않는다. 공개 수준을 바꿔도 같은 링크다.</li>
 * </ul>
 * 화면마다 부르는 이름이 다르다 — 관객 화면은 유형 이름, 주최자 화면은 "행사"({@link #nameOn}).
 * <p>
 * 언제 무엇을 고칠 수 있는지(상태별 수정 범위, 장소 변경의 관리자 승인, 제목 · 소개 · 포스터의 변경 이력)는 이 객체가 아직 보지 않는다
 * (docs/04 "상태별로 할 수 있는 것", "예매가 열린 뒤 수정 범위", docs/03 "변경 이력").
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

    private Event(Long id, long organizerId, EventPublicAddress publicAddress, EventBasicInformation basicInformation,
            EventPoster poster, EventVisibilitySetting visibility) {
        if (organizerId < 1) {
            throw new IllegalArgumentException("주최자 id는 1 이상이어야 합니다: " + organizerId);
        }
        this.id = id;
        this.organizerId = organizerId;
        this.publicAddress = Objects.requireNonNull(publicAddress, "publicAddress");
        this.basicInformation = Objects.requireNonNull(basicInformation, "basicInformation");
        this.poster = poster;
        this.visibility = Objects.requireNonNull(visibility, "visibility");
    }

    /**
     * 위저드 1단계를 마친 새 행사를 만든다. 포스터는 아직 없고 공개 수준은 공개다.
     *
     * @param organizerId      행사를 여는 주최자의 id
     * @param publicAddress    새로 발급한 공개 주소 — 서비스가 TokenGenerator로 만든 값으로 발급한다({@link EventPublicAddress#issue})
     * @param basicInformation 1단계에서 적은 기본 정보
     */
    public static Event create(long organizerId, EventPublicAddress publicAddress, EventBasicInformation basicInformation) {
        return new Event(null, organizerId, publicAddress, basicInformation, null, EventVisibilitySetting.publicByDefault());
    }

    /**
     * 저장소에서 읽은 값으로 행사를 되살린다.
     *
     * @param poster 포스터. 아직 올리지 않았으면 null
     */
    public static Event restore(long id, long organizerId, EventPublicAddress publicAddress,
            EventBasicInformation basicInformation, EventPoster poster, EventVisibilitySetting visibility) {
        if (id < 1) {
            throw new IllegalArgumentException("행사 id는 1 이상이어야 합니다: " + id);
        }
        return new Event(id, organizerId, publicAddress, basicInformation, poster, visibility);
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
}
