package com.ticketon.domain.policy;

import java.time.Duration;

/**
 * '확인 필요' 구역 — 문서에 없어 제안값으로 둔 기본값·기술값.
 * <p>
 * 여기 값은 확정되지 않았다. 상수마다 설명 첫머리에 "확인 필요 (#제안한 이슈 번호)"를 적고,
 * 무엇의 값인지, 왜 그 값을 제안했는지, 근거가 된 문서 위치를 한 줄로 단다.
 * <p>
 * #135에서 사용자가 확정하면 docs/99 C에 기록하고, 값을 {@link TimePolicy}·{@link QuantityPolicy}·
 * {@link RecordRetentionPolicy} 중 맞는 곳으로 옮긴 뒤 여기서 지운다. 제안값과 달라지면 관련 테스트도 고친다.
 * <p>
 * 숫자가 아니라 다른 도메인의 종류끼리 잇는 대응표인 제안은 여기 둘 수 없다(이 패키지는 다른 도메인 패키지를 부르지 않는다).
 * 그런 제안은 그 도메인 패키지에 두고 설명 첫머리에 같은 "확인 필요 (#이슈 번호)"를 적은 뒤, 아래 목록에 위치를 남긴다.
 * <ul>
 *   <li>확인 필요 (#13) — 알림 수신 설정의 세 묶음이 각각 어느 알림 종류를 덮는지: {@code domain.notification.NotificationPreference}.
 *       세 묶음의 기본값은 값이라 아래 상수({@code ..._RECEIVED_BY_DEFAULT})로 둔다.</li>
 * </ul>
 */
public final class ProposedPolicy {

    /**
     * 확인 필요 (#8) — 로그인 세션 비활성 만료. 마지막 요청 뒤 이만큼 요청이 없으면 세션이 끝나 다시 로그인해야 한다.
     * 30분을 제안한다: Spring 기본값이고, 관객 예매 한 번(좌석 선택 10분 + 결제 7분, docs/06 "타이머 두 개")보다 넉넉하며,
     * 위저드 작성 중인 주최자는 자동 임시저장 요청이 세션을 이어 준다(docs/13 "자동 임시저장"). 검표 세션은 따로 정한다(#89).
     */
    public static final Duration LOGIN_SESSION_IDLE_TIMEOUT = Duration.ofMinutes(30);

    /**
     * 확인 필요 (#11) — 이메일 인증 링크 유효 시간. 가입할 때 보낸 링크는 이만큼 지나면 열어도 인증되지 않는다.
     * 24시간을 제안한다: 가입한 날 메일을 늦게 열어도 인증할 수 있고, 남의 메일함에 잘못 간 링크가 오래 살아 있지 않다.
     * 문서에는 "이메일 인증"만 있고 시간이 없다(docs/01 "가입 경로 — 직접 가입"). 비밀번호 재설정 링크는 따로 정한다(#16).
     */
    public static final Duration EMAIL_VERIFICATION_LINK_VALIDITY = Duration.ofHours(24);

    /**
     * 확인 필요 (#11) — 비밀번호 최소 길이(글자 수). 가입할 때 이보다 짧은 비밀번호는 받지 않는다. 그 밖의 조합 규칙(숫자·기호 섞기 등)은 두지 않는다.
     * 8자를 제안한다: 흔히 쓰는 최소 길이이고, 조합 규칙 없이 길이만 보면 가입 칸이 덜 까다롭다. 문서에는 비밀번호 규칙이 없다
     * (docs/01 "가입 경로 — 직접 가입"). 운영진 로그인(#15)·비밀번호 재설정(#16)도 같은 값을 쓴다.
     */
    public static final int PASSWORD_MINIMUM_LENGTH = 8;

    /**
     * 확인 필요 (#13, docs/12:120 리마인더 ☐ 예시와 :137 '받는 게 기본'이 어긋남) — 알림 설정 '예매·취소 확인'을 처음에 받는지.
     * 받음을 제안한다: 알림 설정의 세 줄은 거래 알림이라 받는 게 기본이라고 적혀 있다(docs/12 "마케팅 수신은 구분해서 표시").
     * 설정 예시 그림(docs/12 "알림 설정")에서도 켜져 있다.
     */
    public static final boolean BOOKING_CONFIRMATION_NOTICE_RECEIVED_BY_DEFAULT = true;

    /**
     * 확인 필요 (#13, docs/12:120 리마인더 ☐ 예시와 :137 '받는 게 기본'이 어긋남) — 알림 설정 '공연 전날 리마인더'를 처음에 받는지.
     * 받음을 제안한다: 거래 알림이라 받는 게 기본이라는 문장(docs/12 "마케팅 수신은 구분해서 표시")을 따랐고, 리마인더는 노쇼를 눈에 띄게
     * 줄인다(docs/12 "전날 리마인더를 문자로 하는 게 효과가 큽니다"). 다만 설정 예시 그림(docs/12 "알림 설정")은 꺼진 칸(☐)으로 그려져 있다.
     */
    public static final boolean DAY_BEFORE_REMINDER_RECEIVED_BY_DEFAULT = true;

    /**
     * 확인 필요 (#13, docs/12:120 리마인더 ☐ 예시와 :137 '받는 게 기본'이 어긋남) — 알림 설정 '예매 오픈 알림'을 처음에 받는지.
     * 받음을 제안한다: 거래 알림이라 받는 게 기본이고(docs/12 "마케팅 수신은 구분해서 표시"), 오픈 알림은 관객이 행사마다 직접 신청한
     * 사람에게만 간다(docs/12 "예매 오픈 알림 (신청자)"). 설정 예시 그림(docs/12 "알림 설정")에서도 켜져 있다.
     */
    public static final boolean BOOKING_OPEN_NOTICE_RECEIVED_BY_DEFAULT = true;

    private ProposedPolicy() {
    }
}
