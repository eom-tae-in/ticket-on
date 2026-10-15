package com.ticketon.domain.organizer;

import com.ticketon.domain.account.InitialAvatar;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 주최자 — 사람이 아니라 단체다. 심사·정지·정산·행사가 전부 주최자에 붙고, 사람은 각자 자기 운영진 로그인으로 소속된다
 * (docs/01 "계정 4종", "주최자와 운영진"). 혼자 활동하는 개인 주최자는 소유자 한 명뿐인 단체다.
 * <p>
 * 이름 · 상태 · 공개 정보를 갖는다. 소유자·운영자 소속은 {@link OrganizerStaff}가, 심사용 신청 내용은 {@link OrganizerApplication}이 갖는다.
 * <ul>
 *   <li><b>상태</b>({@link OrganizerStatus}) — 심사 중으로 시작해 {@link #approve 승인}되면 활동이 된다. 신청이 반려되면 심사 중으로
 *       남는다({@link OrganizerApplication#reject}). 닫혀도 지우지 않는다.</li>
 *   <li><b>주최자명</b> — 소유자가 바로 바꾸고({@link StaffAction#RENAME_ORGANIZER}), 변경 이력({@link OrganizerNameChange})을 남긴다.</li>
 *   <li><b>공개 정보</b>({@link OrganizerPublicProfile}) — 소개 · SNS 링크 · 로고(선택). 운영진 누구나 편집한다.
 *       로고가 없으면 {@link #initialAvatar 이름 첫 글자 + 정해진 색}으로 보인다.</li>
 * </ul>
 * 누가 그 일을 할 수 있는지는 운영진 권한 확인({@link StaffPermission#check})이 운영진 명단의 소속·역할과 이 주최자의 상태를 함께 본다.
 * 이 객체는 주최자 상태에 따른 규칙만 지킨다.
 * 시각은 인자로 받는다.
 * <p>
 * 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 주최자는 id가 없다.
 */
public final class Organizer {

    private final Long id;
    private OrganizerName name;
    private OrganizerStatus status;
    private OrganizerPublicProfile publicProfile;

    private Organizer(Long id, OrganizerName name, OrganizerStatus status, OrganizerPublicProfile publicProfile) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.status = Objects.requireNonNull(status, "status");
        this.publicProfile = Objects.requireNonNull(publicProfile, "publicProfile");
    }

    /**
     * 심사 중인 주최자를 만든다. 공개 정보는 비어 있다.
     *
     * @param name 신청서의 이름 또는 단체명
     */
    public static Organizer underReview(OrganizerName name) {
        return new Organizer(null, name, OrganizerStatus.UNDER_REVIEW, OrganizerPublicProfile.empty());
    }

    /** 저장소에서 읽은 값으로 주최자를 되살린다. */
    public static Organizer restore(long id, OrganizerName name, OrganizerStatus status,
            OrganizerPublicProfile publicProfile) {
        if (id < 1) {
            throw new IllegalArgumentException("주최자 id는 1 이상이어야 합니다: " + id);
        }
        return new Organizer(id, name, status, publicProfile);
    }

    /**
     * 승인한다. 심사 중에서 활동으로 바뀌고, 이때부터 행사를 열 수 있다(docs/01 "주최자 신청과 승인").
     * 신청한 사람을 소유자로 들이는 일은 {@link OrganizerStaff#startWithOwner}가 한다.
     *
     * @throws RuleViolationException 심사 중이 아니면({@link OrganizerErrorCode#ORGANIZER_NOT_UNDER_REVIEW})
     */
    public void approve() {
        if (status != OrganizerStatus.UNDER_REVIEW) {
            throw new RuleViolationException(OrganizerErrorCode.ORGANIZER_NOT_UNDER_REVIEW, "상태: " + status);
        }
        status = OrganizerStatus.ACTIVE;
    }

    /**
     * 주최자명을 바꾼다. 바로 적용하고 변경 이력을 돌려준다 — 이력을 저장하고 운영진 전원과 관리자에게 알리는 일은 유스케이스가 한다
     * (docs/01 "공개 페이지 — 주최자명 변경"). 소유자만 하는 일이다({@link StaffAction#RENAME_ORGANIZER}).
     * <p>
     * 지금 이름과 같으면 아무것도 바꾸지 않고 이력도 없다.
     *
     * @param changedByStaffAccountId 바꾼 사람의 운영진 계정 id
     * @return 변경 이력. 지금 이름과 같아 그대로면 비어 있다.
     * @throws RuleViolationException 활동 중인 주최자가 아니면({@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     */
    public Optional<OrganizerNameChange> rename(OrganizerName newName, long changedByStaffAccountId, Instant now) {
        Objects.requireNonNull(newName, "newName");
        Objects.requireNonNull(now, "now");
        requireActive();
        if (newName.equals(name)) {
            return Optional.empty();
        }
        OrganizerNameChange change = new OrganizerNameChange(name, newName, changedByStaffAccountId, now);
        name = newName;
        return Optional.of(change);
    }

    /**
     * 공개 정보 중 소개 · SNS 링크를 바꾼다. 로고는 그대로다. 운영진 누구나 하는 일이다({@link StaffAction#EDIT_PUBLIC_PROFILE}).
     * 둘 다 선택이라 비우면 없는 것으로 둔다({@link OrganizerPublicProfile}).
     *
     * @param newIntroduction 소개 — 비었거나 공백뿐이면 소개 없음
     * @param newSnsLinks     SNS 링크 — 적은 순서대로. 없으면 빈 목록
     * @return 바뀌었는지 — 지금과 같으면 아무것도 바꾸지 않고 false
     * @throws RuleViolationException 활동 중인 주최자가 아니면({@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     */
    public boolean changeIntroductionAndSnsLinks(String newIntroduction, List<ExternalLink> newSnsLinks) {
        requireActive();
        OrganizerPublicProfile changed = publicProfile.withIntroductionAndSnsLinks(newIntroduction, newSnsLinks);
        if (changed.equals(publicProfile)) {
            return false;
        }
        publicProfile = changed;
        return true;
    }

    /**
     * 로고를 새로 올린 것으로 바꾼다(처음 올리기 포함). 운영진 누구나 하는 일이다({@link StaffAction#EDIT_PUBLIC_PROFILE}).
     * 바꾸기 전 로고는 더는 가리키지 않으므로 그 파일을 지우는 일은 유스케이스가 한다.
     *
     * @return 바꾸기 전 로고. 없었으면 빈 값
     * @throws RuleViolationException 활동 중인 주최자가 아니면({@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     */
    public Optional<OrganizerLogo> changeLogo(OrganizerLogo newLogo) {
        Objects.requireNonNull(newLogo, "newLogo");
        requireActive();
        Optional<OrganizerLogo> previousLogo = Optional.ofNullable(publicProfile.logo());
        publicProfile = publicProfile.withLogo(newLogo);
        return previousLogo;
    }

    /**
     * 로고를 지운다 — 로고 자리에 다시 이름 첫 글자 + 정해진 색({@link #initialAvatar})이 보인다(docs/01 "공개 페이지 — 로고"). 운영진 누구나
     * 하는 일이다({@link StaffAction#EDIT_PUBLIC_PROFILE}). 지운 로고의 파일을 지우는 일은 유스케이스가 한다.
     *
     * @return 지운 로고. 로고가 없었으면 아무것도 바꾸지 않고 빈 값
     * @throws RuleViolationException 활동 중인 주최자가 아니면({@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     */
    public Optional<OrganizerLogo> removeLogo() {
        requireActive();
        Optional<OrganizerLogo> removedLogo = Optional.ofNullable(publicProfile.logo());
        removedLogo.ifPresent(logo -> publicProfile = publicProfile.withoutLogo());
        return removedLogo;
    }

    /**
     * 이름 첫 글자 + 주최자마다 정해진 색 — 로고가 없을 때 로고 자리에 보이는 표시(docs/01 "공개 페이지 — 로고"). 색은 주최자 id로 정해져
     * 이름을 바꿔도 그대로다. 운영진 프로필과 같은 팔레트를 쓴다.
     *
     * @throws IllegalStateException 저장 전이라 id가 없으면
     */
    public InitialAvatar initialAvatar() {
        if (id == null) {
            throw new IllegalStateException("저장 전 주최자는 색을 정할 id가 없습니다");
        }
        return InitialAvatar.of(name.value(), id);
    }

    /** 활동 중인지 — 승인된 뒤 닫히거나 영구 정지되지 않았다. */
    public boolean isActive() {
        return status == OrganizerStatus.ACTIVE;
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public OrganizerName name() {
        return name;
    }

    public OrganizerStatus status() {
        return status;
    }

    public OrganizerPublicProfile publicProfile() {
        return publicProfile;
    }

    private void requireActive() {
        if (!isActive()) {
            throw new RuleViolationException(OrganizerErrorCode.ORGANIZER_NOT_ACTIVE, "상태: " + status);
        }
    }
}
