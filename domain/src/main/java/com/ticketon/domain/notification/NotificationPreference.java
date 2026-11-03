package com.ticketon.domain.notification;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 관객이 끌 수 있는 알림 묶음 — 마이페이지 알림 설정의 세 줄(docs/12 "알림 설정", Figma U8). 선언 순서가 화면에 보이는 순서다.
 * <p>
 * <b>끌 수 있는 것은 이 셋뿐</b>이다. 행사 변경·취소·연기·장소 변경·시간 변경·출연자 변경·공연 중단 안내는 조건 없이 보내고
 * 설정 목록에 나타나지 않는다(docs/12 "해제할 수 없는 알림은 목록에 넣지 않습니다"). 어느 묶음도 덮지 않는 종류는 늘 보낸다.
 * <p>
 * <b>세 줄 모두 처음에 받는다</b>(docs/12, docs/99 D "알림 설정 기본값"). 마케팅 수신은 법적 동의 항목이라 여기 두지 않는다(계정의 동의 기록, #18).
 */
public enum NotificationPreference {

    /**
     * 예매·취소 확인 — 예매 완료·예매 변경 완료·취소·환불 완료를 덮는다.
     * 결제 실패는 덮지 않는다: 결제가 응답 없이 끝나 나중에 실패로 확정되면(docs/06) 돈이 걸린 일을 알 길이 이 알림뿐이다(docs/12).
     */
    BOOKING_CONFIRMATION(NotificationKind.BOOKING_COMPLETED, NotificationKind.BOOKING_CHANGED, NotificationKind.CANCELLATION_REFUNDED),

    /** 공연 전날 안내(화면 이름, Figma U8) — 공연 전날 리마인더 하나를 덮는다. */
    DAY_BEFORE_REMINDER(NotificationKind.DAY_BEFORE_REMINDER),

    /**
     * 예매 오픈 알림 — 예매 오픈 알림 하나를 덮는다. 오픈 알림 신청자에게 가는 행사 삭제 알림은 덮지 않는다:
     * 기다리던 행사가 없어졌다는 안내라 행사 취소처럼 조건 없이 보낸다(docs/12).
     */
    BOOKING_OPEN(NotificationKind.BOOKING_OPENED);

    private final Set<NotificationKind> coveredKinds;

    NotificationPreference(NotificationKind firstKind, NotificationKind... otherKinds) {
        this.coveredKinds = EnumSet.of(firstKind, otherKinds);
    }

    /** 이 묶음을 끄면 보내지 않는 알림 종류. */
    public Set<NotificationKind> coveredKinds() {
        return EnumSet.copyOf(coveredKinds);
    }

    /** 바꾼 적 없을 때 받는지 — 세 묶음 모두 받는다(docs/12, docs/99 D "알림 설정 기본값"). */
    public boolean receivesByDefault() {
        return true;
    }

    /** 그 종류를 덮는 묶음. 끌 수 없는 종류(주최자 쪽 전부 포함)면 비어 있다. */
    public static Optional<NotificationPreference> covering(NotificationKind kind) {
        Objects.requireNonNull(kind, "kind");
        return Arrays.stream(values())
                .filter(preference -> preference.coveredKinds.contains(kind))
                .findFirst();
    }
}
