package com.ticketon.domain.admin;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerNameChange;
import com.ticketon.domain.organizer.RejectionReason;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 관리자 조작 기록 한 줄 — 누가(관리자) · 언제 · 무엇을(조작 종류) · 무엇에(대상 종류·id) · 요약 · 사유.
 * <p>
 * <b>관리자의 모든 조작에 남긴다</b>(docs/01 "관리자 2등급", docs/13 "5. 플랫폼 관리자"). 되돌릴 수 없는 조작을 추적하려면 필요하다.
 * 조작과 같은 트랜잭션에서 남겨, 조작이 이뤄졌는데 기록이 없는 일이 없게 한다(application의 관리자 조작 기록기).
 * <p>
 * 요약은 사람이 목록에서 읽는 한 줄이다(운영진 쪽 예: "초대권 10장 발권 · 운영자 김○○", docs/01). 조작마다 만드는 방법을 이 클래스의
 * 정적 메서드로 둔다({@link #adminAccountIssued}, {@link #organizerApplicationApproved},
 * {@link #organizerApplicationRejected}, {@link #ownershipChangeConfirmed}, {@link #ownershipClaimApproved},
 * {@link #ownershipClaimRejected}, {@link #organizerNameChangeConfirmed}).
 * 사유는 관리자가 적은 사유가 있는 조작(정지 사유, 반려 사유 등)만 남기고, 없으면 비어 있다.
 * <p>
 * 보관 기간은 문서에 정해져 있지 않아 지우지 않는다(docs/11 "데이터 보관"에 없음 — 정해지면 파기 작업을 더한다).
 */
public final class AdminOperationRecord {

    private final AccountRef operator;
    private final Instant operatedAt;
    private final AdminOperationKind kind;
    private final long targetId;
    private final String summary;
    private final String reason;

    private AdminOperationRecord(AccountRef operator, Instant operatedAt, AdminOperationKind kind, long targetId,
            String summary, String reason) {
        this.operator = operator;
        this.operatedAt = operatedAt;
        this.kind = kind;
        this.targetId = targetId;
        this.summary = summary;
        this.reason = reason;
    }

    /**
     * 조작 기록 한 줄을 만든다.
     *
     * @param operator   조작한 관리자 — 관리자(ADMIN) 주체
     * @param kind       조작 종류. 대상 종류는 조작 종류가 정한다.
     * @param targetId   대상 id (1 이상)
     * @param summary    요약 — 앞뒤 공백을 걷고, 비어 있으면 안 된다
     * @param reason     관리자가 적은 사유 — 앞뒤 공백을 걷는다. 사유가 없는 조작이면 null.
     * @param operatedAt 조작 일시
     * @throws IllegalArgumentException 관리자가 아닌 주체, 1보다 작은 대상 id, 빈 요약, 공백뿐인 사유
     */
    public static AdminOperationRecord of(AccountRef operator, AdminOperationKind kind, long targetId, String summary,
            String reason, Instant operatedAt) {
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(operatedAt, "operatedAt");
        if (operator.kind() != AccountKind.ADMIN) {
            throw new IllegalArgumentException("관리자 조작 기록은 관리자 주체만 남깁니다: " + operator.principalName());
        }
        if (targetId < 1) {
            throw new IllegalArgumentException("대상 id는 1 이상이어야 합니다: " + targetId);
        }
        String strippedSummary = summary.strip();
        if (strippedSummary.isEmpty()) {
            throw new IllegalArgumentException("관리자 조작 기록의 요약이 비어 있습니다");
        }
        return new AdminOperationRecord(operator, operatedAt, kind, targetId, strippedSummary, strippedReason(reason));
    }

    /**
     * 관리자 계정 발급 기록 — 대상은 발급한 계정이고, 요약은 "관리자 계정 발급 — 이메일 (등급)"이다. 사유는 없다.
     *
     * @param issuer          발급한 최고 관리자
     * @param issuedAccountId 발급한 관리자 계정 id
     * @param email           발급한 계정의 이메일
     * @param grade           발급한 계정의 등급
     */
    public static AdminOperationRecord adminAccountIssued(AccountRef issuer, long issuedAccountId, EmailAddress email,
            AdminGrade grade, Instant issuedAt) {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(grade, "grade");
        return of(issuer, AdminOperationKind.ISSUE_ADMIN_ACCOUNT, issuedAccountId,
                "관리자 계정 발급 — " + email.value() + " (" + grade.displayName() + ")", null, issuedAt);
    }

    /**
     * 주최자 신청 승인 기록 — 대상은 승인한 신청서이고, 요약은 "주최자 신청 승인 — 신청한 이름 또는 단체명"이다. 사유는 없다.
     *
     * @param reviewer      승인한 관리자
     * @param applicationId 승인한 신청서 id
     * @param organizerName 신청한 이름 또는 단체명 — 승인으로 주최자명이 된다
     */
    public static AdminOperationRecord organizerApplicationApproved(AccountRef reviewer, long applicationId,
            OrganizerName organizerName, Instant approvedAt) {
        Objects.requireNonNull(organizerName, "organizerName");
        return of(reviewer, AdminOperationKind.APPROVE_ORGANIZER_APPLICATION, applicationId,
                "주최자 신청 승인 — " + organizerName.value(), null, approvedAt);
    }

    /**
     * 주최자 신청 반려 기록 — 대상은 반려한 신청서이고, 요약은 "주최자 신청 반려 — 신청한 이름 또는 단체명"이다. 사유는 신청한 사람에게 전달한
     * 반려 사유 그대로다(docs/01 "반려 — 사유를 반드시 남기고 전달합니다").
     *
     * @param reviewer      반려한 관리자
     * @param applicationId 반려한 신청서 id
     * @param organizerName 신청한 이름 또는 단체명
     * @param reason        반려 사유
     */
    public static AdminOperationRecord organizerApplicationRejected(AccountRef reviewer, long applicationId,
            OrganizerName organizerName, RejectionReason reason, Instant rejectedAt) {
        Objects.requireNonNull(organizerName, "organizerName");
        Objects.requireNonNull(reason, "reason");
        return of(reviewer, AdminOperationKind.REJECT_ORGANIZER_APPLICATION, applicationId,
                "주최자 신청 반려 — " + organizerName.value(), reason.text(), rejectedAt);
    }

    /**
     * 소유자 변경 사후 확인 기록 — 대상은 확인한 소유자 변경 이력이고, 요약은 "소유자 변경 확인 — 주최자명"이다. 사유는 없다.
     *
     * @param admin             확인한 관리자
     * @param ownershipChangeId 확인한 소유자 변경 이력 id
     * @param organizerName     소유자가 바뀐 주최자의 이름
     */
    public static AdminOperationRecord ownershipChangeConfirmed(AccountRef admin, long ownershipChangeId,
            OrganizerName organizerName, Instant confirmedAt) {
        Objects.requireNonNull(organizerName, "organizerName");
        return of(admin, AdminOperationKind.CONFIRM_OWNERSHIP_CHANGE, ownershipChangeId,
                "소유자 변경 확인 — " + organizerName.value(), null, confirmedAt);
    }

    /**
     * 소유자 이전 신청 승인 기록 — 대상은 승인한 신청이고, 요약은 "소유자 이전 신청 승인 — 주최자명"이다. 사유는 없다.
     *
     * @param admin            승인한 관리자
     * @param ownershipClaimId 승인한 소유자 이전 신청 id
     * @param organizerName    소유자가 바뀐 주최자의 이름
     */
    public static AdminOperationRecord ownershipClaimApproved(AccountRef admin, long ownershipClaimId,
            OrganizerName organizerName, Instant approvedAt) {
        Objects.requireNonNull(organizerName, "organizerName");
        return of(admin, AdminOperationKind.APPROVE_OWNERSHIP_CLAIM, ownershipClaimId,
                "소유자 이전 신청 승인 — " + organizerName.value(), null, approvedAt);
    }

    /**
     * 소유자 이전 신청 반려 기록 — 대상은 반려한 신청이고, 요약은 "소유자 이전 신청 반려 — 주최자명"이다. 사유는 운영진에게 알린 반려 사유
     * 그대로다.
     *
     * @param admin            반려한 관리자
     * @param ownershipClaimId 반려한 소유자 이전 신청 id
     * @param organizerName    신청이 가리킨 주최자의 이름
     * @param reason           반려 사유
     */
    public static AdminOperationRecord ownershipClaimRejected(AccountRef admin, long ownershipClaimId,
            OrganizerName organizerName, RejectionReason reason, Instant rejectedAt) {
        Objects.requireNonNull(organizerName, "organizerName");
        Objects.requireNonNull(reason, "reason");
        return of(admin, AdminOperationKind.REJECT_OWNERSHIP_CLAIM, ownershipClaimId,
                "소유자 이전 신청 반려 — " + organizerName.value(), reason.text(), rejectedAt);
    }

    /**
     * 주최자명 변경 사후 확인 기록 — 대상은 확인한 주최자명 변경 이력이고, 요약은 "주최자명 변경 확인 — 바꾸기 전 이름 → 바꾼 이름"이다. 사유는
     * 없다. 주최자가 그 뒤 이름을 또 바꿔도 확인한 그 한 건을 읽을 수 있게 그때의 두 이름을 적는다.
     *
     * @param admin                 확인한 관리자
     * @param organizerNameChangeId 확인한 주최자명 변경 이력 id
     * @param change                확인한 주최자명 변경 한 건
     */
    public static AdminOperationRecord organizerNameChangeConfirmed(AccountRef admin, long organizerNameChangeId,
            OrganizerNameChange change, Instant confirmedAt) {
        Objects.requireNonNull(change, "change");
        return of(admin, AdminOperationKind.CONFIRM_ORGANIZER_NAME_CHANGE, organizerNameChangeId,
                "주최자명 변경 확인 — " + change.previousName().value() + " → " + change.newName().value(), null,
                confirmedAt);
    }

    /** 조작한 관리자. */
    public AccountRef operator() {
        return operator;
    }

    /** 조작한 관리자의 계정 id. */
    public long adminAccountId() {
        return operator.id();
    }

    public Instant operatedAt() {
        return operatedAt;
    }

    public AdminOperationKind kind() {
        return kind;
    }

    /** 대상 종류 — 조작 종류가 정한다. */
    public AdminOperationTargetKind targetKind() {
        return kind.targetKind();
    }

    public long targetId() {
        return targetId;
    }

    public String summary() {
        return summary;
    }

    /** 관리자가 적은 사유. 사유가 없는 조작이면 비어 있다. */
    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    private static String strippedReason(String reason) {
        if (reason == null) {
            return null;
        }
        String stripped = reason.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException("사유가 공백뿐입니다. 사유가 없는 조작이면 null로 넘깁니다");
        }
        return stripped;
    }
}
