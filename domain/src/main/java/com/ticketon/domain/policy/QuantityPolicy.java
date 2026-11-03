package com.ticketon.domain.policy;

import com.ticketon.common.ImageFormat;
import java.time.Period;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * docs/99 C "수량·비율"과 "입력·파일 상한" 표의 확정 숫자.
 * <p>
 * 조기예매 임박의 "종료 3일 전"처럼 99 C에서 이 표에 함께 적힌 기간도 여기 둔다.
 */
public final class QuantityPolicy {

    /** 1회 선택 매수 기본값 — 동시에 선점할 수 있는 좌석 수. 주최자가 조정한다. 묶음권은 티켓 장수로 센다. docs/06 "1회 선택 매수" */
    public static final int DEFAULT_MAX_SEATS_PER_SELECTION = 4;

    /** 1회 선택 매수 경고선 — 8석 초과 또는 전체의 10% 초과면 경고만 한다(막지 않음). docs/06 "1회 선택 매수" */
    public static final QuantityThreshold SEATS_PER_SELECTION_WARNING = new QuantityThreshold(8, Percent.of(10));

    /** 조기예매 "얼마 남지 않음" — 남은 몫이 30% 이하. docs/05 "임박 표시" */
    public static final Percent EARLY_BIRD_RUNNING_LOW = Percent.of(30);

    /** 조기예매 "마감 임박"(수량) — 남은 몫이 10% 이하 또는 10장 이하. docs/05 "임박 표시" */
    public static final QuantityThreshold EARLY_BIRD_ALMOST_GONE = new QuantityThreshold(10, Percent.of(10));

    /** 조기예매 "마감 임박"(기간) — 종료일 3일 이내. 수량과 기간 중 더 급한 쪽을 따른다. docs/05 "임박 표시" */
    public static final Period EARLY_BIRD_ENDING_SOON = Period.ofDays(3);

    /** 잔여 배지 "매진 임박" — 잔여 10% 이하 또는 10석 이하. 목록 카드에만 배지. docs/13 "잔여 좌석 표시" */
    public static final QuantityThreshold REMAINING_SEATS_BADGE = new QuantityThreshold(10, Percent.of(10));

    /** 묶음권 할인율 기본값 — 2일권 3%. 주최자가 조정한다. docs/05 "가격은 할인율로 계산합니다" */
    public static final Percent DEFAULT_BUNDLE_DISCOUNT_TWO_DAYS = Percent.of(3);

    /** 묶음권 할인율 기본값 — 3일권 7%. 주최자가 조정한다. docs/05 "가격은 할인율로 계산합니다" */
    public static final Percent DEFAULT_BUNDLE_DISCOUNT_THREE_DAYS = Percent.of(7);

    /** 묶음권 할인율 기본값 — 4일권 10%. 주최자가 조정한다. docs/05 "가격은 할인율로 계산합니다" */
    public static final Percent DEFAULT_BUNDLE_DISCOUNT_FOUR_DAYS = Percent.of(10);

    /** 퍼센트 할인 반올림 단위 — 100원 단위로 내린다(올리면 관객이 손해). docs/05 "회차별 가격 조정", "가격은 할인율로 계산합니다" */
    public static final long PERCENT_DISCOUNT_ROUNDING_UNIT_WON = 100;

    /** 포스터 비율 — 3:4 세로 고정. docs/03 "기본 정보" */
    public static final AspectRatio POSTER_ASPECT_RATIO = new AspectRatio(3, 4);

    /** 연령대 통계 표시 기준 — 예매자 30명 이상일 때만, 행사 전체와 회차별을 각각. docs/11 "통계 — 연령대" */
    public static final int AGE_GROUP_STATS_MINIMUM_BOOKERS = 30;

    /** 배상률 — 입장료의 10%. 주최자 사정 취소·중단·출연자 변경일 때. 불가항력은 없음. docs/08 "법적 기준", "주최자 귀책" */
    public static final Percent COMPENSATION_RATE = Percent.of(10);

    /**
     * 관객 가입 최소 나이 — 만 14세. 가입일(한국 날짜)의 만 나이가 이보다 적으면 가입을 막고, 동의 항목에도 "만 14세 이상입니다"를 둔다.
     * 99 C 표에는 없지만 법이 정한 값이라 확정이다(개인정보보호법 제22조의2). docs/01 "전화번호"(만 14세 미만 차단), docs/99 A-3
     */
    public static final int MINIMUM_SIGNUP_AGE = 14;

    /**
     * 첫 글자 표시의 색 가짓수 — 8가지. 로고가 없는 주최자는 이름 첫 글자 + 주최자마다 정해진 색, 운영진 프로필은 닉네임 첫 글자 + 사람마다
     * 정해진 색으로 보이고, 둘이 같은 팔레트를 쓴다. 99 C 표에는 없지만 docs/01에 적힌 값이다.
     * docs/01 "공개 페이지 — 로고", "운영진 프로필 — 표시", docs/99 D "주최자 로고"
     */
    public static final int INITIAL_AVATAR_COLOR_COUNT = 8;

    /** 비밀번호 최소 길이(글자 수) — 조합 규칙은 두지 않는다. docs/01 "로그인 유지와 링크 유효 시간", docs/99 C "비밀번호" */
    public static final int PASSWORD_MINIMUM_LENGTH = 8;

    /** 블록 최대 행 수. docs/99 C "블록 크기" */
    public static final int SEAT_BLOCK_MAXIMUM_ROWS = 50;

    /** 블록 최대 열 수. docs/99 C "블록 크기" */
    public static final int SEAT_BLOCK_MAXIMUM_COLUMNS = 50;

    /** 정산 계좌번호에서 그대로 보이는 마지막 숫자의 수 — 나머지는 가린다. docs/99 C "정산 계좌 표시" */
    public static final int SETTLEMENT_ACCOUNT_VISIBLE_TRAILING_DIGITS = 4;

    // ── docs/99 C "입력·파일 상한". 글자 수는 앞뒤 공백을 걷고 NFC로 맞춘 뒤 사람이 세는 대로 센다(유니코드 code point)

    /** 행사 제목 최대 길이(글자 수). */
    public static final int EVENT_TITLE_MAXIMUM_LENGTH = 100;

    /** 회차 출연 최대 길이(글자 수). */
    public static final int SESSION_PERFORMERS_MAXIMUM_LENGTH = 100;

    /** 운영진 한 줄 소개 최대 길이(글자 수). */
    public static final int STAFF_INTRODUCTION_MAXIMUM_LENGTH = 50;

    /** 판매 중단 사유 최대 길이(글자 수). */
    public static final int SALES_PAUSE_REASON_MAXIMUM_LENGTH = 50;

    /** 운영진 닉네임 최대 길이(글자 수). */
    public static final int NICKNAME_MAXIMUM_LENGTH = 20;

    /** 카테고리 이름과 '기타' 장르명 최대 길이(글자 수) — 장르명을 정식 카테고리로 승격하면 그 이름이 그대로 카테고리 이름이 되어 같은 값을 쓴다. */
    public static final int EVENT_CATEGORY_NAME_MAXIMUM_LENGTH = 20;

    /** 블록 이름 최대 길이(글자 수). */
    public static final int BLOCK_NAME_MAXIMUM_LENGTH = 20;

    /** 구역 이름 최대 길이(글자 수). */
    public static final int ZONE_NAME_MAXIMUM_LENGTH = 20;

    /** 입구 이름 최대 길이(글자 수). */
    public static final int ENTRANCE_NAME_MAXIMUM_LENGTH = 20;

    /** 주최자 로고 용량 상한(1MB = 1,048,576바이트). */
    public static final FileSize ORGANIZER_LOGO_MAXIMUM_SIZE = FileSize.ofMegabytes(2);

    /** 행사 포스터 용량 상한(1MB = 1,048,576바이트). */
    public static final FileSize EVENT_POSTER_MAXIMUM_SIZE = FileSize.ofMegabytes(5);

    /** 포스터 비율({@link #POSTER_ASPECT_RATIO})의 허용 오차 — 크롭 도구가 잘라도 픽셀은 정수라 가로가 조금 어긋난다. */
    public static final Percent EVENT_POSTER_ASPECT_RATIO_TOLERANCE = Percent.of(1);

    /**
     * 주최자 로고로 받는 그림 형식. 올린 쪽이 적은 형식과 파일 첫머리의 실제 시그니처가 모두 맞아야 한다. SVG는 그림 안에 스크립트를 넣을 수 있어 누구나 여는
     * 공개 주소로 내주기 위험하고, GIF · WebP는 움직이는 그림이거나 JDK ImageIO가 크기를 읽지 못한다.
     */
    public static final Set<ImageFormat> ORGANIZER_LOGO_ACCEPTED_FORMATS =
            Collections.unmodifiableSet(EnumSet.of(ImageFormat.PNG, ImageFormat.JPEG));

    /** 행사 포스터로 받는 그림 형식 — 까닭은 {@link #ORGANIZER_LOGO_ACCEPTED_FORMATS}와 같다. */
    public static final Set<ImageFormat> EVENT_POSTER_ACCEPTED_FORMATS =
            Collections.unmodifiableSet(EnumSet.of(ImageFormat.PNG, ImageFormat.JPEG));

    /** 파일 올리기 요청 하나의 바깥 울타리 — 파일마다의 상한보다 넉넉해, 상한을 조금 넘은 파일도 끝까지 받아 까닭을 칸 옆에 알려 준다. */
    public static final FileSize UPLOAD_REQUEST_MAXIMUM_SIZE = FileSize.ofMegabytes(10);

    private QuantityPolicy() {
    }
}
