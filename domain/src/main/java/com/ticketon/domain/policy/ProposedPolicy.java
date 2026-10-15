package com.ticketon.domain.policy;

import com.ticketon.common.ImageFormat;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

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
     * 문서에는 "이메일 인증"만 있고 시간이 없다(docs/01 "가입 경로 — 직접 가입"). 비밀번호 재설정 링크는 따로 정한다
     * ({@link #PASSWORD_RESET_LINK_VALIDITY}).
     * 운영진 로그인(#15)의 가입 인증 링크도 같은 값을 쓴다.
     */
    public static final Duration EMAIL_VERIFICATION_LINK_VALIDITY = Duration.ofHours(24);

    /**
     * 확인 필요 (#11) — 비밀번호 최소 길이(글자 수). 가입할 때 이보다 짧은 비밀번호는 받지 않는다. 그 밖의 조합 규칙(숫자·기호 섞기 등)은 두지 않는다.
     * 8자를 제안한다: 흔히 쓰는 최소 길이이고, 조합 규칙 없이 길이만 보면 가입 칸이 덜 까다롭다. 문서에는 비밀번호 규칙이 없다
     * (docs/01 "가입 경로 — 직접 가입"). 운영진 로그인(#15)·비밀번호 재설정(#16)도 같은 값을 쓴다.
     */
    public static final int PASSWORD_MINIMUM_LENGTH = 8;

    /**
     * 확인 필요 (#16, 문서에 없음) — 비밀번호 재설정 링크 유효 시간. 비밀번호 찾기로 보낸 링크는 이만큼 지나면 열어도 비밀번호를 바꿀 수 없다.
     * 1시간을 제안한다: 비밀번호 찾기는 본인이 요청하고 바로 메일을 여는 일이라 길 필요가 없고, 링크 하나로 계정의 비밀번호를 바꿀 수 있어
     * 메일함에 오래 살아 있으면 위험하다. 메일이 조금 늦게 와도 열 수 있게 수 분보다는 넉넉히 둔다. 가입 인증 링크(24시간)보다 짧은 까닭이
     * 이것이다. 문서에는 "비밀번호 찾기 — 관객/주최자 중 고름, 같은 안내"만 있고 시간이 없다(docs/01 "같은 이메일로 양쪽 가입").
     */
    public static final Duration PASSWORD_RESET_LINK_VALIDITY = Duration.ofHours(1);

    /**
     * 확인 필요 (#15, 문서에 없음 — 공개 이름이라 화면 표시 기준으로 제안) — 운영진 닉네임 최대 길이(글자 수). 이보다 긴 닉네임은 받지 않는다.
     * 글자 수는 앞뒤 공백을 걷고 NFC로 맞춘 뒤 사람이 세는 대로 센다(유니코드 code point — 한글 한 자, 이모지 하나가 한 자).
     * 20자를 제안한다: 닉네임은 공개 프로필·주최자 페이지 운영진 목록·행사 운영 기록("운영자 김○○")에 이름 대신 보여(docs/01) 한 줄에
     * 들어가야 하고, 랜덤 닉네임 조합(가장 긴 것 9자)도 넉넉히 들어간다. 상한이 없으면 아주 긴 값이 유일 색인 한도에 걸려 저장되지 않는다.
     * 문서에는 "필수 · 중복 불가 · 변경 가능"만 있고 길이가 없다(docs/99 C "운영진 닉네임"). 쓸 수 있는 글자는 정하지 않는다.
     */
    public static final int NICKNAME_MAXIMUM_LENGTH = 20;

    /**
     * 확인 필요 (#28, 문서에 없음 — 공개 프로필의 "한 줄" 기준으로 제안) — 운영진 한 줄 소개 최대 길이(글자 수). 이보다 긴 소개는 받지 않는다.
     * 글자 수는 닉네임({@link #NICKNAME_MAXIMUM_LENGTH})과 같이 앞뒤 공백을 걷고 NFC로 맞춘 뒤 사람이 세는 대로 센다(유니코드 code point).
     * 50자를 제안한다: 이름이 "한 줄 소개"라 운영진 프로필 머리말에서 닉네임 아래 한 줄로 보이는 길이여야 하고, 문서 예시
     * "극단에서 연출하고 있습니다"(docs/01 "운영진 프로필", 14자)의 세 배가 넘어 하는 일과 단체를 함께 적어도 넉넉히 들어간다.
     * 상한이 없으면 공개 페이지에 아주 긴 글이 실린다 — 프로필은 글·연락 기능 없이 닉네임·소개·참여 이력까지만이다(docs/99 B "운영진 프로필의
     * 글·연락 기능"). 문서에는 "닉네임 · 한 줄 소개"만 있고 길이가 없다(docs/01, docs/13 "내 프로필"). 쓸 수 있는 글자(줄바꿈 포함)는 정하지 않는다.
     */
    public static final int STAFF_INTRODUCTION_MAXIMUM_LENGTH = 50;

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

    /**
     * 확인 필요 (#23, 문서에 없음) — 관리자 처리 목록에서 주최자 신청을 강조하기 시작하는 때: 심사 목표일(제출일부터 영업일 3일,
     * {@link TimePolicy#ORGANIZER_REVIEW_TARGET})까지 남은 영업일이 이 수 이하면 강조한다. 목표일 당일과 지난 신청도 강조한다.
     * 1영업일을 제안한다: 처리 목록은 공연이 임박한 건과 기한이 다가오는 건을 강조하는데(docs/12 "관리자 대상", docs/04 "처리 목록 — 정산 기한이
     * 다가오는 건은 강조") 주최자 신청에는 공연이 없어 심사 목표일을 기한으로 본다. 하루 전부터 눈에 띄면 목표일 안에 처리할 여유가 있고,
     * 목표가 영업일 3일뿐이라 2일 이상으로 잡으면 들어온 다음 영업일부터 강조돼 강조의 뜻이 옅어진다.
     */
    public static final BusinessDays ORGANIZER_REVIEW_DEADLINE_APPROACHING_WITHIN = BusinessDays.of(1);

    /**
     * 확인 필요 (#25, 문서에 없음) — 운영자 초대 유효 기간. 소유자가 보낸 초대는 보낸 때부터 이만큼 지나면 수락·거절할 수 없고 받은 초대에서도
     * 빠진다. 지난 초대는 소유자가 같은 이메일로 다시 초대하면 그때부터 이 기간만큼 다시 열린다.
     * 7일을 제안한다: docs/01이 운영진 구조를 GitHub Organization에 견주는데("주최자와 운영진") GitHub 조직 초대가 7일 뒤 만료된다. 받은
     * 사람이 주최자 쪽 로그인을 새로 만들고 이메일 인증({@link #EMAIL_VERIFICATION_LINK_VALIDITY})까지 마칠 여유가 있고, 주말에만 메일을 보는
     * 동아리 단원도 다음 주말 전에 답할 수 있다. 문서에는 "소유자가 이메일로 초대"만 있고 기간이 없다(docs/01 "운영자 초대").
     */
    public static final Duration STAFF_INVITATION_VALIDITY = Duration.ofDays(7);

    /**
     * 확인 필요 (#29, 문서에 없음) — 주최자 로고 파일 용량 상한. 이보다 큰 로고는 받지 않는다(1MB = 1,048,576바이트,
     * {@link FileSize}).
     * 2MB를 제안한다: 로고는 화면에서 정사각형으로 잘라 올리는 작은 그림이고(docs/01 "공개 페이지 — 로고"), 주최자 페이지 머리에 작게 보여 수백
     * 픽셀이면 충분하다. 휴대폰 사진을 잘라 그대로 올려도 대개 들어가는 크기다. 포스터(#40, 제안 5MB)는 목록 카드와 상세 페이지에 크게 보여 더
     * 넉넉히 둔다. 로고는 "포스터처럼 크롭 도구"로 올리므로(docs/01) 포스터의 "업로드 후 크롭 도구를 제공하고 용량 상한을 둡니다"(docs/03
     * "기본 정보")를 로고에도 같은 방식으로 적용했다.
     */
    public static final FileSize ORGANIZER_LOGO_MAXIMUM_SIZE = FileSize.ofMegabytes(2);

    /**
     * 확인 필요 (#29, 문서에 없음) — 주최자 로고로 받는 그림 형식. 이 밖의 형식은 받지 않는다. 형식은 올린 쪽이 적은 형식과 파일 첫머리의 실제
     * 시그니처가 모두 맞아야 한다(확장자는 보지 않는다).
     * PNG · JPEG를 제안한다: 화면의 크롭 도구가 잘라 낸 그림을 내보내는 형식이고(브라우저 캔버스는 PNG · JPEG로 내보낸다), 로고에 흔한 투명
     * 배경은 PNG가 담는다. SVG는 받지 않는다 — 그림 파일 안에 스크립트를 넣을 수 있어, 누구나 로그인 없이 여는 공개 주소로 내주기 위험하다.
     * GIF · WebP는 JDK ImageIO가 크기를 읽지 못하거나(WebP) 움직이는 그림이라(GIF) 뺐다.
     */
    public static final Set<ImageFormat> ORGANIZER_LOGO_ACCEPTED_FORMATS =
            Collections.unmodifiableSet(EnumSet.of(ImageFormat.PNG, ImageFormat.JPEG));

    /**
     * 확인 필요 (#29, 문서에 없음 — 기술값) — 파일 올리기 요청 하나의 바깥 울타리. 서버는 이보다 큰 파일 · 요청을 끝까지 읽지 않고 끊는다(400).
     * 파일마다의 상한(로고 {@link #ORGANIZER_LOGO_MAXIMUM_SIZE})은 그 안에서 따로 본다.
     * 10MB를 제안한다: 파일마다의 상한(로고 2MB, 포스터 제안 5MB — #40)보다 넉넉해, 상한을 조금 넘은 파일도 끝까지 받아 "2MB 이하로 올려 주세요"처럼
     * 까닭을 칸 옆에 알려 줄 수 있다. 그보다 훨씬 큰 요청(휴대폰 원본 동영상 등)은 읽기 전에 끊어 서버 디스크 · 메모리를 아낀다. Spring 기본값(1MB)은
     * 로고 상한보다 작아 그대로 둘 수 없다.
     */
    public static final FileSize UPLOAD_REQUEST_MAXIMUM_SIZE = FileSize.ofMegabytes(10);

    private ProposedPolicy() {
    }
}
