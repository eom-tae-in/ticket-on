package com.ticketon.domain.notification;

import static com.ticketon.domain.notification.NotificationChannel.EMAIL;
import static com.ticketon.domain.notification.NotificationChannel.SMS;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 알림 종류와 채널 표 — docs/12 "관객 대상"·"주최자 대상" 표를 그대로 옮긴다. 표 밖에서 docs/12가 보내라고 한 알림(마케팅 수신 동의·철회
 * 처리 결과, "마케팅 발송")과 표 밖의 주최자 쪽 결과 알림(소유자 이전 신청 반려, 사업자 정보 변경 확인 결과)도 여기 둔다.
 * <p>
 * <b>긴급하거나 돈이 걸린 것만 문자, 나머지는 이메일</b>(docs/12 "원칙"). 관객 쪽은 종류마다 이메일·문자 중 무엇으로 보내는지가
 * 정해져 있고, <b>주최자 쪽은 전부 이메일</b>이며 제목에 [주최]가 붙는다({@link EmailSubject#notice}). 주최자 쪽은 종류마다
 * 받는 사람의 범위도 정해져 있다({@link OrganizerRecipientScope}). 주최자에 관한 알림은 소유자와 운영자 전원에게, 소유자만 다루는 일은
 * 소유자에게만 가고(docs/12 "주최자 대상 — 받는 사람"), 주최자 운영진이 없는 때의 결과(주최자 신청·다시 열기의 승인·반려)와
 * 한 사람에게 일어난 일은 그 사람에게 간다.
 * <p>
 * 표의 한 줄에 둘이 적힌 것(승인 / 반려, 접수 확인 · 처리 결과, 정지 · 해제 등)은 서로 다른 일이라 종류를 나눴다. 채널과 받는 사람은 같다.
 * <p>
 * 끌 수 있는 종류는 수신 설정({@link NotificationPreference})이 덮는 것뿐이다. 행사 변경·취소·연기·장소 변경·시간 변경·출연자 변경·
 * 공연 중단 안내는 설정과 상관없이 보낸다(docs/12 "해제할 수 없는 알림은 목록에 넣지 않습니다").
 * <p>
 * 이 이름은 발송 이력에 그대로 남으므로 바꾸지 않는다.
 */
public enum NotificationKind {

    // ── 관객 대상 (docs/12 "관객 대상") ──

    /** 회원가입 완료 — 이메일 인증을 마친 때. */
    SIGNUP_COMPLETED(EMAIL),

    /** 예매 완료 — 문자에는 티켓 링크를 넣는다(docs/12 "예매 완료 문자에는 티켓 링크"). */
    BOOKING_COMPLETED(EMAIL, SMS),

    /** 예매 변경 완료. */
    BOOKING_CHANGED(EMAIL),

    /** 취소·환불 완료. */
    CANCELLATION_REFUNDED(EMAIL),

    /** 결제 실패. */
    PAYMENT_FAILED(EMAIL),

    /** 공연 전날 리마인더 — 관람일 전날 낮 12시, 묶음권은 날짜마다(docs/12 "전날 리마인더"). */
    DAY_BEFORE_REMINDER(SMS),

    /** 예매 오픈 알림 — 신청자에게. */
    BOOKING_OPENED(EMAIL, SMS),

    /** 행사 정보 변경 — 주최자가 발송. 설정과 상관없이 보낸다. */
    EVENT_INFORMATION_CHANGED(EMAIL, SMS),

    /** 장소 변경 — 설정과 상관없이 보낸다. */
    VENUE_CHANGED(EMAIL, SMS),

    /** 회차 연기 — 설정과 상관없이 보낸다. */
    SESSION_POSTPONED(EMAIL, SMS),

    /** 회차 취소 — "다른 회차 예매하기" 링크, 불가항력이면 "배상 여부는 확인 후 알려드립니다"(docs/04). 설정과 상관없이 보낸다. */
    SESSION_CANCELLED(EMAIL, SMS),

    /** 불가항력 배상 확인 결과 — 지급 / 면제(docs/04). */
    FORCE_MAJEURE_COMPENSATION_DECIDED(EMAIL),

    /** 행사 취소 — 설정과 상관없이 보낸다. */
    EVENT_CANCELLED(EMAIL, SMS),

    /** 공연 중단 — 전액 환불 안내(docs/08). 설정과 상관없이 보낸다. */
    PERFORMANCE_INTERRUPTED(EMAIL, SMS),

    /** 회차 시간 변경(docs/04) — 설정과 상관없이 보낸다. */
    SESSION_TIME_CHANGED(EMAIL, SMS),

    /** 출연자 변경 — 공제 없는 취소 · 배상 안내(docs/04). 설정과 상관없이 보낸다. */
    PERFORMER_CHANGED(EMAIL, SMS),

    /** 공연 지연 환급(docs/08). */
    DELAY_REFUNDED(EMAIL),

    /** QR 재발행 완료 — "저장해 둔 캡처도 새로 저장하세요". */
    QR_REISSUED(EMAIL, SMS),

    /** QR 재발행 반려 — 사유 포함. */
    QR_REISSUE_REJECTED(EMAIL),

    /** 행사 페이지 내리기(공연 전) — "예매는 그대로 유효합니다". */
    EVENT_PAGE_TAKEN_DOWN(EMAIL),

    /** 신고 접수 확인(docs/01). */
    REPORT_RECEIVED(EMAIL),

    /** 신고 처리 결과 한 줄(docs/01). */
    REPORT_RESOLVED(EMAIL),

    /** 이용 정지(docs/01). */
    AUDIENCE_SUSPENDED(EMAIL),

    /** 이용 정지 해제(docs/01). */
    AUDIENCE_SUSPENSION_LIFTED(EMAIL),

    /** 행사 삭제 — 오픈 알림 신청자에게(docs/04). */
    EVENT_DELETED(EMAIL),

    // ── 관객 대상, '관객 대상' 표 밖 (docs/12 "마케팅 발송") ──

    /**
     * 마케팅 수신 동의·철회 처리 결과 — 정보통신망법이 요구해 처리 결과를 14일 안에 그 사람에게 알린다. 이 알림에는 광고를 넣지 않는다
     * (docs/12 "마케팅 발송", docs/99 A-6). 거래 알림처럼 이메일로만 보내고, 설정과 상관없이 보낸다 — 어느 묶음도 덮지 않는다.
     */
    MARKETING_CONSENT_CHANGED(EMAIL),

    // ── 주최자 대상 (docs/12 "주최자 대상") — 전부 이메일, 제목에 [주최] ──

    /**
     * 주최자 신청 승인 — 신청서의 통지 이메일로(docs/01 "신청 항목": 이메일 — 통지). 승인되는 순간 신청한 사람이 소유자가 되므로
     * 받는 사람은 같지만, 반려와 같은 곳으로 보내려고 신청서 이메일을 쓴다.
     */
    ORGANIZER_APPLICATION_APPROVED(OrganizerRecipientScope.INDIVIDUAL),

    /**
     * 주최자 신청 반려 — 사유 포함, 신청서의 통지 이메일로(docs/01 "신청 항목", "반려"). 반려할 때는 소유자도 운영자도 없어
     * 주최자로 펼칠 수 없다(docs/01 "승인되면 신청한 사람이 소유자가 되고").
     */
    ORGANIZER_APPLICATION_REJECTED(OrganizerRecipientScope.INDIVIDUAL),

    /** 주최자 정지 통지. */
    ORGANIZER_SUSPENDED(OrganizerRecipientScope.ALL_STAFF),

    /** 주최자 정지 해제 — "판매를 재개할 수 있습니다"(docs/01). */
    ORGANIZER_SUSPENSION_LIFTED(OrganizerRecipientScope.ALL_STAFF),

    /** 장소·일시 변경 승인. */
    VENUE_SCHEDULE_CHANGE_APPROVED(OrganizerRecipientScope.ALL_STAFF),

    /** 장소·일시 변경 반려. */
    VENUE_SCHEDULE_CHANGE_REJECTED(OrganizerRecipientScope.ALL_STAFF),

    /** 정산 완료 — 미수금이 있으면 함께 표시. */
    SETTLEMENT_COMPLETED(OrganizerRecipientScope.ALL_STAFF),

    /** 불가항력 확인 결과 — 배상 면제 / 지급. */
    FORCE_MAJEURE_REVIEW_DECIDED(OrganizerRecipientScope.ALL_STAFF),

    /** 작성 중 행사 30일 미수정(docs/04 "작성 중 행사 관리"). */
    DRAFT_EVENT_IDLE(OrganizerRecipientScope.ALL_STAFF),

    /** 수정 요청 — 관리자 조치, 기한 포함. 신고 접수 자체는 알리지 않는다(docs/01). */
    CORRECTION_REQUESTED(OrganizerRecipientScope.ALL_STAFF),

    /** 행사 페이지 내리기 — 관리자 조치. */
    EVENT_PAGE_TAKEN_DOWN_BY_ADMIN(OrganizerRecipientScope.ALL_STAFF),

    /** 행사 페이지 다시 올리기 — 관리자 조치. */
    EVENT_PAGE_RESTORED_BY_ADMIN(OrganizerRecipientScope.ALL_STAFF),

    /** 공연 지연 · 출연자 변경을 관리자가 대신 처리함. */
    HANDLED_BY_ADMIN_ON_BEHALF(OrganizerRecipientScope.ALL_STAFF),

    /** 운영자 초대 — 받는 사람에게. */
    OPERATOR_INVITED(OrganizerRecipientScope.INDIVIDUAL),

    /** 운영진에서 내보내짐 — 그 사람에게. */
    REMOVED_FROM_STAFF(OrganizerRecipientScope.INDIVIDUAL),

    /**
     * 소유자 변경 — 표는 "운영자 전원에게"다(docs/01 "소유자 넘기기": 운영자 전원과 관리자에게). 넘긴 뒤의 운영진 전원으로 보낸다:
     * 넘긴 사람은 운영자로 남고, 받은 사람도 바뀐 사실을 받는다. 관리자에게는 알림 대신 처리 목록으로 간다(docs/12 "관리자 대상").
     */
    OWNER_CHANGED(OrganizerRecipientScope.ALL_STAFF),

    /** 정산 계좌 변경 확인 결과 — 소유자에게. */
    SETTLEMENT_ACCOUNT_CHANGE_DECIDED(OrganizerRecipientScope.OWNER_ONLY),

    /** 주최자명 변경 — 운영진 전원에게. */
    ORGANIZER_NAME_CHANGED(OrganizerRecipientScope.ALL_STAFF),

    /** 운영자가 행사 제목을 바꿈 — 소유자에게. 승인이 아니라 알림. */
    EVENT_TITLE_CHANGED_BY_OPERATOR(OrganizerRecipientScope.OWNER_ONLY),

    /**
     * 다시 열기 승인 — 다시 열기를 신청한 사람(그 운영진 로그인)에게. 닫힌 주최자는 운영진이 전원 빠져 있어 주최자로 펼칠 수 없다
     * (docs/01 "닫은 뒤에는", "닫힌 주최자의 소유자였다면 '다시 열기 신청'도 보입니다").
     */
    REOPEN_APPROVED(OrganizerRecipientScope.INDIVIDUAL),

    /** 다시 열기 반려 — 다시 열기를 신청한 사람(그 운영진 로그인)에게. 승인과 같은 까닭이다. */
    REOPEN_REJECTED(OrganizerRecipientScope.INDIVIDUAL),

    /** 닉네임 · 소개가 초기화됨 — 그 사람에게, 관리자 조치. */
    STAFF_PROFILE_RESET(OrganizerRecipientScope.INDIVIDUAL),

    /** 주최자 로고가 초기화됨 — 운영진 전원에게, 관리자 조치. */
    ORGANIZER_LOGO_RESET(OrganizerRecipientScope.ALL_STAFF),

    // ── 주최자 대상, '주최자 대상' 표 밖 ──

    /**
     * 소유자 이전 신청 반려 — 사유 포함, <b>신청한 운영자와 운영진 전원에게</b> 한 사람에 한 통씩. 소유자와 연락이 끊겨 운영자가 낸 신청의
     * 결과다(docs/01 "소유자와 연락이 끊긴 경우", #27 "결과를 신청자와 운영진 전원에게"). 주최자에 관한 알림이라 소유자와 운영자 전원이 받고
     * (docs/12 "주최자 대상 — 받는 사람"), 신청한 사람은 결과 전에 주최자에서 나갔어도 받는다. 그래서 주최자로 펼치지 않고 받는 사람마다 그
     * 이메일로 보낸다 — 신청한 사람이 아직 운영진이면 한 번만 간다. 운영진 로그인을 탈퇴한 신청자는 이메일을 파기해 받지 않는다. 승인은 소유자가
     * 바뀌는 일이라 표의 {@link #OWNER_CHANGED}로 보낸다.
     */
    OWNERSHIP_CLAIM_REJECTED(OrganizerRecipientScope.INDIVIDUAL),

    /**
     * 사업자 정보 변경 확인 결과 — 승인 / 반려(사유 포함), <b>소유자에게</b>. 표에는 정산 계좌 쪽({@link #SETTLEMENT_ACCOUNT_CHANGE_DECIDED})만
     * 있지만 사업자 정보 수정도 "정산 계좌와 같습니다"(docs/01 "판매자 정보")라 같은 흐름으로 관리자가 확인하고, #31이 결과를 소유자에게 메일로
     * 보내라고 했다. 소유자만 다루는 일이라 소유자에게만 간다(docs/12 "주최자 대상 — 받는 사람").
     */
    BUSINESS_INFORMATION_CHANGE_DECIDED(OrganizerRecipientScope.OWNER_ONLY);

    private final RecipientSide side;
    private final Set<NotificationChannel> channels;
    private final OrganizerRecipientScope organizerRecipientScope;

    /** 관객 쪽 — 채널은 표대로. */
    NotificationKind(NotificationChannel firstChannel, NotificationChannel... otherChannels) {
        this.side = RecipientSide.AUDIENCE;
        this.channels = EnumSet.of(firstChannel, otherChannels);
        this.organizerRecipientScope = null;
    }

    /** 주최자 쪽 — 전부 이메일. */
    NotificationKind(OrganizerRecipientScope organizerRecipientScope) {
        this.side = RecipientSide.ORGANIZER;
        this.channels = EnumSet.of(EMAIL);
        this.organizerRecipientScope = organizerRecipientScope;
    }

    /** 받는 쪽 — 주최자 쪽이면 메일 제목에 [주최]가 붙는다. */
    public RecipientSide side() {
        return side;
    }

    /** 이 종류를 보내는 수단. */
    public Set<NotificationChannel> channels() {
        return EnumSet.copyOf(channels);
    }

    /** 주최자 쪽 종류의 받는 사람 범위. 관객 쪽 종류면 비어 있다. */
    public Optional<OrganizerRecipientScope> organizerRecipientScope() {
        return Optional.ofNullable(organizerRecipientScope);
    }

    /**
     * 이 종류를 그 받는 사람에게 보낼 수 있는지.
     * <ul>
     *   <li>관객 쪽 — 관객 계정 또는 직접 연락처(초대권 받는 사람 등).</li>
     *   <li>주최자 쪽, 한 사람에게 가는 종류 — 직접 연락처.</li>
     *   <li>주최자 쪽, 운영진 전원·소유자에게 가는 종류 — 주최자 id.</li>
     * </ul>
     */
    public boolean canBeSentTo(NotificationRecipient recipient) {
        Objects.requireNonNull(recipient, "recipient");
        if (side == RecipientSide.AUDIENCE) {
            return !(recipient instanceof NotificationRecipient.Organizer);
        }
        if (organizerRecipientScope == OrganizerRecipientScope.INDIVIDUAL) {
            return recipient instanceof NotificationRecipient.DirectContact;
        }
        return recipient instanceof NotificationRecipient.Organizer;
    }

    /**
     * 그 연락처로 보낼 수단 — 이 종류의 수단 가운데 연락처가 받을 수 있는 것, {@link NotificationChannel} 선언 순서대로.
     * <p>
     * 초대권 받는 사람처럼 연락처가 하나뿐이면 그 수단으로만 간다(docs/12 "발권할 때 문자나 이메일로 보낸 경우 같은 수단으로").
     *
     * @throws IllegalArgumentException 연락처로 이 종류를 받을 수단이 하나도 없으면(문자로만 보내는 종류에 이메일만 있는 연락처 등)
     */
    public List<NotificationChannel> channelsReaching(ContactPoint contactPoint) {
        Objects.requireNonNull(contactPoint, "contactPoint");
        List<NotificationChannel> reachable = Arrays.stream(NotificationChannel.values())
                .filter(channels::contains)
                .filter(contactPoint::canReceiveBy)
                .toList();
        if (reachable.isEmpty()) {
            throw new IllegalArgumentException(name() + " 알림을 받을 수단(" + channels + ")이 연락처에 없습니다");
        }
        return reachable;
    }
}
