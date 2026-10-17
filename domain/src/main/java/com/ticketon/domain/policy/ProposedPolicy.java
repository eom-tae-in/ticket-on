package com.ticketon.domain.policy;

import com.ticketon.common.ImageFormat;
import java.time.Duration;
import java.time.LocalTime;
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
 * 숫자가 아니라 다른 도메인의 종류를 쓰는 제안(종류끼리 잇는 대응표, 도메인 값의 처음 값, 계산 방식)은 여기 둘 수 없다(이 패키지는 다른 도메인
 * 패키지를 부르지 않는다). 그런 제안은 그 도메인 패키지에 두고 설명 첫머리에 같은 "확인 필요 (#이슈 번호)"를 적은 뒤, 아래 목록에 위치를 남긴다.
 * <ul>
 *   <li>확인 필요 (#13) — 알림 수신 설정의 세 묶음이 각각 어느 알림 종류를 덮는지: {@code domain.notification.NotificationPreference}.
 *       세 묶음의 기본값은 값이라 아래 상수({@code ..._RECEIVED_BY_DEFAULT})로 둔다.</li>
 *   <li>확인 필요 (#35) — 1인 총 매수의 처음 값(제한 없음): {@code domain.event.TicketsPerPersonLimit#defaultLimit}.</li>
 *   <li>확인 필요 (#35) — 1회 선택 매수가 전체 좌석에서 차지하는 비율을 보여 줄 때의 반올림(올림한 정수 퍼센트):
 *       {@code domain.event.SeatsPerSelectionFeedback#percentOfTotal}.</li>
 *   <li>확인 필요 (#35) — 예매 오픈 시각의 처음 값(비워 두면 판매를 시작하는 순간 연다): {@code domain.event.BookingSettings}.</li>
 *   <li>확인 필요 (#37) — 좌석 번호를 매기는 방식(블록 격자의 행 순번이 "N열", 그 행 안의 순번이 "N번". 얹을 때 정하고 비활성 좌석이 있어도 다시
 *       매기지 않음): {@code domain.event.SeatNumber}.</li>
 *   <li>확인 필요 (#37) — 비운 블록 이름에 붙이는 "블록 N"의 번호(다른 블록 · 구역이 쓰지 않는 가장 작은 수):
 *       {@code domain.event.FloorPlan#addBlock}.</li>
 *   <li>확인 필요 (#38) — 판매 대기 행사에 관리자 조치(정지 2단계 · 페이지 내리기)를 걸면 판매 중단으로 둔다(오픈 시각이 와도 열지 않고, 조치가 풀린
 *       뒤 주최자가 재개하면 시각대로 판매 대기 또는 판매 중): {@code domain.event.Event#holdSales}.</li>
 *   <li>확인 필요 (#38) — 예매가 열린 뒤 수정 범위 표(docs/04)에 없는 항목(유형 · 카테고리, 상세 안내 · 지도 링크 · 공개용 연락처, 예매 종료 시각
 *       밖의 예매 설정)은 판매 중에 막아 둔다: {@code domain.event.EventEditTarget}.</li>
 *   <li>확인 필요 (#38) — 비어 있던 회차 종료 시각은 판매가 끝난 뒤에도 행사가 끝날 때까지 입력받는다(막으면 행사 종료를 판정할 수 없음):
 *       {@code domain.event.EventEditTarget#MISSING_SESSION_END_TIME}.</li>
 *   <li>확인 필요 (#45) — 작성 중 행사를 목록에서 접는 "회차 날짜가 이미 지남"은 일정의 모든 날짜가 오늘(한국 날짜) 전일 때(마지막 날짜가 지났을
 *       때)이고, 날짜가 없으면 접지 않는다: {@code domain.event.DraftEventNudges#isFolded}.</li>
 *   <li>확인 필요 (#45) — 판매 중단 행사는 내 행사 목록의 진행 중 구역에 둔다(docs/13의 진행 중 목록에 이름이 없음):
 *       {@code domain.event.EventListSection}.</li>
 *   <li>확인 필요 (#48) — 정액 쿠폰처럼 한 금액을 티켓마다 비례로 나누고 남은 원을 주는 순서(버린 끝자리가 큰 티켓부터 1원씩, 같으면 앞 순번부터):
 *       {@code domain.pricing.Money#splitProportionally}.</li>
 *   <li>확인 필요 (#48) — 금액이 바뀌지 않는 퍼센트(0% 할인)는 100원 단위로 내리지 않는다: {@code domain.pricing.Money#percentRoundedDown}.</li>
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
     * 픽셀이면 충분하다. 휴대폰 사진을 잘라 그대로 올려도 대개 들어가는 크기다. 포스터({@link #EVENT_POSTER_MAXIMUM_SIZE}, 제안 5MB)는 목록
     * 카드와 상세 페이지에 크게 보여 더 넉넉히 둔다. 로고는 "포스터처럼 크롭 도구"로 올리므로(docs/01) 포스터의 "업로드 후 크롭 도구를 제공하고 용량 상한을 둡니다"(docs/03
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
     * 파일마다의 상한(로고 {@link #ORGANIZER_LOGO_MAXIMUM_SIZE}, 포스터 {@link #EVENT_POSTER_MAXIMUM_SIZE})은 그 안에서 따로 본다.
     * 10MB를 제안한다: 파일마다의 상한(로고 2MB, 포스터 5MB)보다 넉넉해, 상한을 조금 넘은 파일도 끝까지 받아 "2MB 이하로 올려 주세요"처럼
     * 까닭을 칸 옆에 알려 줄 수 있다. 그보다 훨씬 큰 요청(휴대폰 원본 동영상 등)은 읽기 전에 끊어 서버 디스크 · 메모리를 아낀다. Spring 기본값(1MB)은
     * 로고 상한보다 작아 그대로 둘 수 없다.
     */
    public static final FileSize UPLOAD_REQUEST_MAXIMUM_SIZE = FileSize.ofMegabytes(10);

    /**
     * 확인 필요 (#40, 문서에 "용량 상한을 둡니다"만 있고 값이 없음) — 행사 포스터 파일 용량 상한. 이보다 큰 포스터는 받지 않는다(1MB =
     * 1,048,576바이트, {@link FileSize}).
     * 5MB를 제안한다: 포스터는 목록 카드와 행사 상세 페이지에 크게 보이고 카톡 링크 미리보기 그림으로도 쓰여(docs/03 "기본 정보", docs/13) 로고
     * ({@link #ORGANIZER_LOGO_MAXIMUM_SIZE}, 2MB)보다 큰 그림이 필요하다. 인쇄용 포스터를 화면의 크롭 도구로 3:4로 잘라 내보내도(브라우저 캔버스의
     * JPEG · PNG) 대개 들어가는 크기이고, 관객이 휴대폰으로 행사 목록을 넘길 때 받는 그림이 지나치게 커지지 않는다. 파일 올리기 요청의 바깥 울타리
     * ({@link #UPLOAD_REQUEST_MAXIMUM_SIZE}, 10MB)보다 작아, 상한을 조금 넘은 포스터도 끝까지 받아 칸 옆에 까닭을 알려 줄 수 있다.
     */
    public static final FileSize EVENT_POSTER_MAXIMUM_SIZE = FileSize.ofMegabytes(5);

    /**
     * 확인 필요 (#40, 문서에 없음) — 행사 포스터로 받는 그림 형식. 이 밖의 형식은 받지 않는다. 형식은 올린 쪽이 적은 형식과 파일 첫머리의 실제
     * 시그니처가 모두 맞아야 한다(확장자는 보지 않는다).
     * PNG · JPEG를 제안한다: 주최자 로고({@link #ORGANIZER_LOGO_ACCEPTED_FORMATS})와 같은 까닭이다 — 화면의 크롭 도구가 잘라 낸 그림을 내보내는
     * 형식이고, SVG는 그림 안에 스크립트를 넣을 수 있어 누구나 여는 공개 주소로 내주기 위험하며, GIF · WebP는 JDK ImageIO가 크기를 읽지 못하거나
     * (WebP) 움직이는 그림이라(GIF) 뺐다.
     */
    public static final Set<ImageFormat> EVENT_POSTER_ACCEPTED_FORMATS =
            Collections.unmodifiableSet(EnumSet.of(ImageFormat.PNG, ImageFormat.JPEG));

    /**
     * 확인 필요 (#40, 문서에 "3:4 세로 고정"만 있고 오차가 없음) — 행사 포스터 비율({@link QuantityPolicy#POSTER_ASPECT_RATIO}, 3:4)의 허용 오차.
     * 그림의 가로 ÷ 세로가 3 ÷ 4에서 이 비율 넘게 벗어나면 받지 않는다({@link AspectRatio#isMatchedBy}). 예: 세로 1,200픽셀이면 가로 891~909픽셀.
     * 1%를 제안한다: 화면의 크롭 도구가 3:4로 잘라도 픽셀은 정수라 가로가 조금 어긋난다(세로 1,199픽셀의 3:4 가로는 899.25픽셀). 가로를 정수로
     * 맞추며 생기는 어긋남(1픽셀 미만)은 세로가 67픽셀 이상이면, 꼭 1픽셀 어긋나도 세로가 134픽셀 이상이면 1% 안에 들고, 1% 차이는 목록 카드에서
     * 눈에 띄지 않는다. 오차가 크면 비율을 고정한 뜻이 옅어진다 — A4 세로
     * (1:1.414, 3:4에서 약 6%)처럼 흔한 다른 비율은 크롭 도구로 3:4로 잘라 올리게 한다.
     */
    public static final Percent EVENT_POSTER_ASPECT_RATIO_TOLERANCE = Percent.of(1);

    /**
     * 확인 필요 (#31, 문서에 없음) — 소유자 화면의 정산 계좌번호에서 그대로 보이는 마지막 숫자의 수. 나머지 숫자는 {@code *}로 가린다
     * ({@code SettlementAccount#maskedAccountNumber} — 예: {@code 123456-78-901234} → {@code ******-**-**1234}).
     * 4자리를 제안한다: 소유자가 "어느 계좌인지" 알아보는 데는 은행 · 예금주와 끝 네 자리면 충분하고, 은행 앱 · 카드사 화면이 흔히 그만큼만
     * 보인다. 정산 계좌는 소유자 자리를 넘겨받은 사람이 정산금을 가로챌 수 있는 값이라(docs/01 "가로채기를 막는 장치") 소유자 화면 응답에도
     * 전체를 싣지 않는다. 관리자 확인 화면은 확인과 손으로 하는 정산(docs/11 "정산 완료 처리")에 전체가 필요해 가리지 않는다(주최자 심사 화면과
     * 같다). 문서에는 가리는 방식이 없다.
     */
    public static final int SETTLEMENT_ACCOUNT_VISIBLE_TRAILING_DIGITS = 4;

    /**
     * 확인 필요 (#34, 문서에 없음) — 행사 카테고리 이름과 '기타' 장르명의 최대 길이(글자 수). 이보다 긴 장르명은 받지 않는다. 관리자가 장르명을
     * 정식 카테고리로 승격하면 그 이름이 그대로 카테고리 이름이 되므로 둘이 같은 값을 쓴다({@code domain.event.EventCategoryName}).
     * 글자 수는 앞뒤 공백을 걷고 NFC로 맞춘 뒤 사람이 세는 대로 센다(유니코드 code point).
     * 20자를 제안한다: 카테고리는 목록의 필터 칩과 위저드의 고르기 칸에 한 줄로 보이고, 처음 목록에서 가장 긴 이름이 "미디어아트"(5자)이며 문서
     * 예시 장르명 "낭독극"(3자)이다(docs/03 "카테고리"). "어린이 인형극" · "스탠드업 코미디" 같은 이름도 넉넉히 들어간다. 장르명은 주최자가
     * 자유 입력하고 관리자가 모아 보는 목록에 그대로 실리므로 상한이 없으면 아주 긴 글이 들어온다. 문서에는 "장르명을 자유 입력"만 있고 길이가 없다.
     */
    public static final int EVENT_CATEGORY_NAME_MAXIMUM_LENGTH = 20;

    /**
     * 확인 필요 (#34, 문서에 없음) — 행사 제목 최대 길이(글자 수). 이보다 긴 제목은 받지 않는다({@code domain.event.EventTitle}).
     * 글자 수는 앞뒤 공백을 걷고 NFC로 맞춘 뒤 사람이 세는 대로 센다(유니코드 code point).
     * 100자를 제안한다: 제목은 목록 카드 · 티켓 · 알림 메일 제목과 문자에 행사 이름으로 실리므로(docs/12) 상한이 없으면 아주 긴 글이 화면과
     * 문자를 넘친다. 한글 제목에 영문 원제나 지역을 덧붙인 긴 제목("뮤지컬 〈…〉 월드투어 - 서울 (… World Tour)", 60자 안팎)도 막지 않게 넉넉히
     * 둔다 — 카드에서 넘치는 부분은 화면이 줄임표로 자른다. 복제한 행사는 제목 뒤에 " (복사본)"이 붙으므로(docs/04 "행사 복제") 그때 상한을 넘으면
     * 어떻게 할지는 복제에서 정한다. 문서에는 "제목 — 필수"만 있고 길이가 없다(docs/03 "기본 정보").
     */
    public static final int EVENT_TITLE_MAXIMUM_LENGTH = 100;

    /**
     * 확인 필요 (#36, 문서에 없음) — 회차 출연의 최대 길이(글자 수). 이보다 긴 출연은 받지 않는다({@code domain.event.SessionPerformers}).
     * 글자 수는 앞뒤 공백을 걷고 NFC로 맞춘 뒤 사람이 세는 대로 센다(유니코드 code point).
     * 100자를 제안한다: 출연은 관객 화면의 회차 목록 · 회차 선택 화면에서 회차 시간 아래 "한 줄"로 보이는 칸이다(docs/02 "출연 (선택)"). 문서
     * 예시 "김배우 · 박배우"(9자) 같은 더블 캐스팅 표기는 서너 명을 적어도 30자 안팎이고, 페스티벌 날짜의 출연 팀 여럿을 " · "로 이어 적어도
     * 들어가게 행사 제목 상한({@link #EVENT_TITLE_MAXIMUM_LENGTH})과 같게 둔다 — 화면에서 넘치는 부분은 줄임표로 자른다. 상한이 없으면 아주 긴
     * 글이 회차 목록과 변경 이력(#46)에 실린다. 문서에는 "선택. 자유 입력 한 줄"만 있고 길이가 없다.
     */
    public static final int SESSION_PERFORMERS_MAXIMUM_LENGTH = 100;

    /**
     * 확인 필요 (#37, 문서에 없음) — 블록 이름 최대 길이(글자 수). 이보다 긴 이름은 받지 않는다({@code domain.event.BlockName}).
     * 글자 수는 앞뒤 공백을 걷고 NFC로 맞춘 뒤 사람이 세는 대로 센다(유니코드 code point).
     * 20자를 제안한다: 블록이 둘 이상이면 블록 이름이 좌석 표기 앞머리로 티켓 · 예매 카드 · 검표 결과(좌석을 크게 보임)에 실린다(docs/02 "좌석 번호
     * 표기", docs/07 "티켓 표기", docs/13 "검표 — 결과 표시 (좌석 크게)"). 문서 예시 "1층" · "2층" · "좌측" · "중앙"(2자)과 "지하 1층"(docs/02
     * "도면은 행사마다 따로 갖습니다", 5자)보다 넉넉해 "2층 발코니 좌측"처럼 층과 방향을 함께 적어도 들어가고, 카테고리 이름
     * ({@link #EVENT_CATEGORY_NAME_MAXIMUM_LENGTH})처럼 한 줄에 보이는 짧은 이름의 상한과 같다. 상한이 없으면 아주 긴 이름이 좌석 표기를 넘친다.
     * 문서에는 "이름은 자유 입력"만 있고 길이가 없다(docs/02 "블록").
     */
    public static final int BLOCK_NAME_MAXIMUM_LENGTH = 20;

    /**
     * 확인 필요 (#37, 문서에 없음) — 구역 이름 최대 길이(글자 수). 이보다 긴 이름은 받지 않는다({@code domain.event.ZoneName}).
     * 글자 수는 앞뒤 공백을 걷고 NFC로 맞춘 뒤 사람이 세는 대로 센다(유니코드 code point).
     * 20자를 제안한다: 구역 이름은 좌석 선택 화면의 범례("R석 50,000원"), 티켓("R석 12열 10번", "스탠딩A · 0091-2"), 검표 결과에 한 줄로 실린다
     * (docs/02 "관객에게는 번호를 보여주지 않습니다", docs/07 "티켓 표기"). 문서 예시 "VIP석" · "R석" · "스탠딩A" · "테이블석" · "전체"(docs/02
     * "구역", 5자 이하)와 "휠체어 구역"(docs/02 "휠체어석은 체크박스 확인", 6자)보다 넉넉하고, 블록 이름({@link #BLOCK_NAME_MAXIMUM_LENGTH})과
     * 서로 겹치지 않게 견주는 이름이라 같은 상한으로 둔다. 문서에는 구역 이름의 길이가 없다.
     */
    public static final int ZONE_NAME_MAXIMUM_LENGTH = 20;

    /**
     * 확인 필요 (#37, 문서에 없음 — 기술값) — 블록 하나의 최대 행 수(가로줄 수). 이보다 많은 행의 블록은 받지 않는다({@code domain.event.BlockSize}).
     * 50행을 제안한다: 블록은 "5행 × 10열" 같은 덩어리이고(docs/02 "에디터는 블록 단위") 도면 하나는 좌석 수백 개이며(docs/02 "도면은 행사마다
     * 따로 갖습니다") 가장 큰 템플릿도 500석이다(docs/99 C "좌석 템플릿"). 열 상한({@link #SEAT_BLOCK_MAXIMUM_COLUMNS})과 함께 블록 하나가
     * 2,500석까지라 한 층짜리 대공연장도 블록 하나로 그릴 수 있고, 더 크면 "1층" · "2층" · "좌측"처럼 블록을 나눈다. 상한이 없으면 요청 하나로 좌석
     * 수백만 개를 만들어 서버와 DB를 묶을 수 있다.
     */
    public static final int SEAT_BLOCK_MAXIMUM_ROWS = 50;

    /**
     * 확인 필요 (#37, 문서에 없음 — 기술값) — 블록 하나의 최대 열 수(한 줄의 자리 수). 이보다 많은 열의 블록은 받지 않는다
     * ({@code domain.event.BlockSize}). 50열을 제안한다: 행 상한({@link #SEAT_BLOCK_MAXIMUM_ROWS})과 같은 까닭이다. 대공연장도 통로로 나뉜 한
     * 줄이 50석을 넘는 일은 드물고, 넘으면 블록을 나눈다.
     */
    public static final int SEAT_BLOCK_MAXIMUM_COLUMNS = 50;

    /**
     * 확인 필요 (#37, 문서에 없음) — 입구 이름 최대 길이(글자 수). 이보다 긴 이름은 받지 않는다({@code domain.event.EntranceName}).
     * 글자 수는 앞뒤 공백을 걷고 NFC로 맞춘 뒤 사람이 세는 대로 센다(유니코드 code point).
     * 20자를 제안한다: 입구 이름은 검표 시작 때 고르는 목록과 검표 화면의 입구 변경, 게이트별 입장 현황에 한 줄로 보인다(docs/02 "입구 (게이트)",
     * docs/13 "검표"). 문서 예시 "정문" · "2층 전용" · "VIP 입구"(docs/02, 6자 이하)와 "옆문"(docs/04)보다 넉넉하고, 블록 · 구역 이름
     * ({@link #BLOCK_NAME_MAXIMUM_LENGTH}, {@link #ZONE_NAME_MAXIMUM_LENGTH})과 같은 한 줄 이름의 상한으로 둔다. 문서에는 입구 이름의 길이가 없다.
     */
    public static final int ENTRANCE_NAME_MAXIMUM_LENGTH = 20;

    /**
     * 확인 필요 (#38, 문서에 없음) — 판매 중단 사유의 최대 길이(글자 수). 이보다 긴 사유는 받지 않는다({@code domain.event.SalesPauseReason}).
     * 글자 수는 앞뒤 공백을 걷고 NFC로 맞춘 뒤 사람이 세는 대로 센다(유니코드 code point).
     * 50자를 제안한다: 문서가 "한 줄 사유"라 하고(docs/04 "판매 중단 — 주최자가 한 줄 사유를 적을 수 있게 하면 문의가 줄어듭니다") 관객이 "판매
     * 일시 중단" 표시와 함께 한 줄로 읽는 칸이라, 같은 "한 줄"인 운영진 한 줄 소개({@link #STAFF_INTRODUCTION_MAXIMUM_LENGTH})와 같게 둔다.
     * "출연진 사정으로 잠시 판매를 멈춥니다"(18자) 같은 사유가 넉넉히 들어간다. 상한이 없으면 공개 화면에 아주 긴 글이 실린다.
     */
    public static final int SALES_PAUSE_REASON_MAXIMUM_LENGTH = 50;

    /**
     * 확인 필요 (#45, 문서에 없음) — 작성 중 행사 30일 미수정 알림을 보내는 정해진 시각 작업이 매일 도는 시각(한국 시각). 그날 이 시각까지 30일이
     * 지난 행사에 알린다 — 9/14 15:00에 마지막으로 고쳤으면 10/14 15:00에 30일이 차고, 10/15 10:00 작업이 알린다.
     * 오전 10시를 제안한다: 주최자 쪽 알림은 급한 것이 없어 전부 이메일이고(docs/12 "주최자 대상"), 밤 · 새벽에 메일이 오지 않게 하면서 하루를 시작할
     * 때 메일함에서 보이는 때다. 관객 전날 리마인더의 낮 12시(docs/12 "전날 리마인더" — 점심시간 확인율)는 관객 문자의 까닭이라 그대로 따르지
     * 않았다. 문서에는 "30일 미수정 — 알림"만 있고 언제 보내는지가 없다(docs/04 "작성 중 행사 관리").
     */
    public static final LocalTime DRAFT_EVENT_IDLE_NOTICE_RUN_TIME = LocalTime.of(10, 0);

    private ProposedPolicy() {
    }
}
