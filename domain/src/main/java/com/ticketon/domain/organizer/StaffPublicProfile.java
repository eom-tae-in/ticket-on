package com.ticketon.domain.organizer;

import com.ticketon.domain.account.InitialAvatar;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffAccountStatus;
import com.ticketon.domain.account.StaffIntroduction;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 운영진 프로필 — 운영진의 공개 페이지. 누구나 로그인 없이 본다(docs/01 "공개 페이지 — 운영진 프로필", docs/13 "운영진 프로필").
 * <p>
 * <b>보이는 것은 닉네임 · 한 줄 소개 · 참여한 주최자(이름 · 역할 · 기간)뿐이다.</b> 실명 · 이메일 · 관객으로서 예매한 내역은 보이지 않는다 — 이
 * 객체는 그 값을 갖지 않는다(같은 이메일로 관객 계정이 있어도 연결하지 않는다). 사진은 받지 않고 닉네임 첫 글자 + 사람마다 정해진 색
 * ({@link InitialAvatar})으로 보인다. 공개 여부는 묻지 않는다 — 실명이 아니라 닉네임이라 개인을 특정하지 않는다(docs/99 B "운영진 프로필 공개
 * 여부 선택"). 글 · 연락 기능은 없다(docs/99 B "운영진 프로필의 글·연락 기능").
 * <p>
 * 누가 프로필을 갖는지와 무엇이 보이는지는 {@link #of}가 정한다.
 * <ul>
 *   <li><b>운영진(소유자 · 운영자)만</b> — 운영진이 되면 자동으로 생긴다. 주최자에 소속된 적이 있는 활동 중인 운영진 로그인만 프로필이 있다.
 *       주최자 신청만 했거나(심사 중 · 반려) 초대를 받기만 한 로그인은 아직 운영진이 아니다. 관객 · 관리자 · 검표 계정에는 프로필이 없다 — 이
 *       객체는 운영진 로그인으로만 만든다.</li>
 *   <li><b>탈퇴한 운영진 로그인</b>은 프로필이 없다. 문서에 정해지지 않아(확인 필요 #28) 탈퇴할 때 한 줄 소개까지 파기하는 규칙(docs/11 "회원
 *       탈퇴 시 법정 보존 대상 외 즉시 파기")에 맞춰 보이지 않게 둔다.</li>
 *   <li><b>참여한 주최자</b> — 소속 한 건이 한 줄이다. 역할이 바뀌면(소유자 넘기기) 기간마다 한 줄이 남는다({@link StaffMembership}). 나왔거나
 *       닫힌 주최자의 소속도 이력으로 남는다(docs/01 "운영진 프로필 — 지난 소속"). 공개되지 않는 주최자({@link OrganizerStatus#isPublic})의 소속은
 *       뺀다 — 영구 정지된 주최자는 페이지가 내려가고 프로필 목록에서도 빠진다.</li>
 *   <li><b>활동 종료</b> — 그 주최자에서 나왔거나(지금 소속이 없다) 주최자가 닫혔으면 그 주최자의 줄은 활동 종료다(docs/01 예시 "늦여름 · 운영자 ·
 *       2023~2024 (활동 종료)"). 역할만 바뀌어 지금도 소속돼 있으면 지난 역할의 줄도 활동 종료가 아니다.</li>
 *   <li>순서 — 소속을 시작한 순서(docs/01 예시: "극단 열두달 · 소유자 · 2019~" 다음 "늦여름 · 운영자 · 2023~2024").</li>
 * </ul>
 */
public final class StaffPublicProfile {

    private final long staffAccountId;
    private final Nickname nickname;
    private final StaffIntroduction introduction;
    private final InitialAvatar initialAvatar;
    private final List<ParticipatedOrganizer> participatedOrganizers;

    private StaffPublicProfile(long staffAccountId, Nickname nickname, StaffIntroduction introduction,
            InitialAvatar initialAvatar, List<ParticipatedOrganizer> participatedOrganizers) {
        this.staffAccountId = staffAccountId;
        this.nickname = nickname;
        this.introduction = introduction;
        this.initialAvatar = initialAvatar;
        this.participatedOrganizers = List.copyOf(participatedOrganizers);
    }

    /**
     * 그 사람의 운영진 프로필을 만든다.
     *
     * @param account      운영진 로그인 계정 — 저장된 계정
     * @param affiliations 그 사람의 모든 소속(지난 소속 포함)
     * @param organizers   소속된 적이 있는 주최자 — 소속마다 그 주최자가 있어야 한다
     * @return 프로필. 탈퇴했거나 활동 중이 아니거나 주최자에 소속된 적이 없으면 — 운영진이 아니면 — 비어 있다.
     * @throws IllegalArgumentException 저장 전 계정이거나, 다른 사람의 소속이거나, 소속된 주최자가 빠졌으면
     */
    public static Optional<StaffPublicProfile> of(StaffAccount account, StaffAffiliations affiliations,
            Collection<Organizer> organizers) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(affiliations, "affiliations");
        Objects.requireNonNull(organizers, "organizers");
        long staffAccountId = account.id().orElseThrow(() -> new IllegalArgumentException("저장 전 계정은 프로필이 없습니다"));
        if (affiliations.staffAccountId() != staffAccountId) {
            throw new IllegalArgumentException("다른 사람(" + affiliations.staffAccountId() + ")의 소속입니다: 계정 " + staffAccountId);
        }
        if (account.status() != StaffAccountStatus.ACTIVE || affiliations.memberships().isEmpty()) {
            return Optional.empty();
        }
        Map<Long, Organizer> organizersById = indexById(organizers);
        List<ParticipatedOrganizer> participatedOrganizers = affiliations.memberships().stream()
                .sorted(Comparator.comparing(StaffMembership::startedAt).thenComparing(StaffMembership::organizerId))
                .map(membership -> participation(membership, organizerOf(membership, organizersById), affiliations))
                .flatMap(Optional::stream)
                .toList();
        return Optional.of(new StaffPublicProfile(staffAccountId, account.nickname(), account.introduction().orElse(null),
                account.initialAvatar(), participatedOrganizers));
    }

    public long staffAccountId() {
        return staffAccountId;
    }

    public Nickname nickname() {
        return nickname;
    }

    /** 한 줄 소개. 적지 않았으면 비어 있다. */
    public Optional<StaffIntroduction> introduction() {
        return Optional.ofNullable(introduction);
    }

    /** 사진 대신 보이는 닉네임 첫 글자 + 사람마다 정해진 색. */
    public InitialAvatar initialAvatar() {
        return initialAvatar;
    }

    /** 참여한 주최자 — 소속을 시작한 순서. 공개되지 않는 주최자는 빠진다. */
    public List<ParticipatedOrganizer> participatedOrganizers() {
        return participatedOrganizers;
    }

    /** 공개되지 않는 주최자의 소속이면 비어 있다. */
    private static Optional<ParticipatedOrganizer> participation(StaffMembership membership, Organizer organizer,
            StaffAffiliations affiliations) {
        if (!organizer.status().isPublic()) {
            return Optional.empty();
        }
        boolean activityEnded = organizer.status() == OrganizerStatus.CLOSED
                || !affiliations.isActiveIn(membership.organizerId());
        return Optional.of(new ParticipatedOrganizer(membership.organizerId(), organizer.name(), membership.role(),
                membership.startedAt(), membership.endedAt(), activityEnded));
    }

    private static Organizer organizerOf(StaffMembership membership, Map<Long, Organizer> organizersById) {
        Organizer organizer = organizersById.get(membership.organizerId());
        if (organizer == null) {
            throw new IllegalArgumentException("소속된 주최자가 빠졌습니다: 주최자 " + membership.organizerId());
        }
        return organizer;
    }

    private static Map<Long, Organizer> indexById(Collection<Organizer> organizers) {
        Map<Long, Organizer> organizersById = new HashMap<>();
        for (Organizer organizer : organizers) {
            long organizerId = organizer.id().orElseThrow(() -> new IllegalArgumentException("저장 전 주최자입니다"));
            organizersById.put(organizerId, organizer);
        }
        return organizersById;
    }

    /**
     * 참여한 주최자 한 줄 — 이름 · 역할 · 기간(docs/01 "운영진 프로필"). 누르면 그 주최자 페이지로 간다(docs/13).
     *
     * @param organizerId    주최자 id
     * @param organizerName  지금 주최자명 — 이름을 바꾼 주최자는 새 이름으로 보인다
     * @param role           그 기간의 역할
     * @param startedAt      소속 시작 일시
     * @param endedAt        소속 종료 일시. 지금 소속돼 있으면 null.
     * @param activityEnded  활동 종료 — 그 주최자에서 나왔거나 주최자가 닫혔다
     */
    public record ParticipatedOrganizer(long organizerId, OrganizerName organizerName, StaffRole role, Instant startedAt,
            Instant endedAt, boolean activityEnded) {

        public ParticipatedOrganizer {
            Objects.requireNonNull(organizerName, "organizerName");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(startedAt, "startedAt");
        }
    }
}
