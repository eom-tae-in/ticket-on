package com.ticketon.domain.policy;

import java.time.LocalTime;

/**
 * '확인 필요' 구역 — 문서에 없어 제안값으로 둔 기본값·기술값.
 * <p>
 * 여기 값은 확정되지 않았다. 상수마다 설명 첫머리에 "확인 필요 (#제안한 이슈 번호)"를 적고,
 * 무엇의 값인지, 왜 그 값을 제안했는지, 근거가 된 문서 위치를 한 줄로 단다.
 * <p>
 * #223에서 사용자가 확정하면 docs/99 C에 기록하고, 값을 {@link TimePolicy}·{@link QuantityPolicy}·
 * {@link RecordRetentionPolicy} 중 맞는 곳으로 옮긴 뒤 여기서 지운다. 제안값과 달라지면 관련 테스트도 고친다.
 * <p>
 * 숫자가 아니라 다른 도메인의 종류를 쓰는 제안(종류끼리 잇는 대응표, 도메인 값의 처음 값, 계산 방식)은 여기 둘 수 없다(이 패키지는 다른 도메인
 * 패키지를 부르지 않는다). 그런 제안은 그 도메인 패키지에 두고 설명 첫머리에 같은 "확인 필요 (#이슈 번호)"를 적은 뒤, 아래 목록에 위치를 남긴다.
 * <ul>
 *   <li>확인 필요 (#223) — 묶음권 할인율을 예매가 열린 뒤 바꿀 수 있는지, 판매 중에 조기예매 · 쿠폰 · 묶음권을 새로 만들 수 있는지(조기예매 ·
 *       쿠폰을 고칠 때의 하한은 docs/99 E "가격 · 할인"에 있음): {@code domain.event.EventEditTarget#PRICE}.</li>
 *   <li>확인 필요 (#223) — 재입장 끄기 · 연령 제한 올리기를 막을 때의 오류 문구:
 *       {@code domain.event.EventErrorCode#REENTRY_TURN_OFF_LOCKED_AFTER_BOOKING_OPENED},
 *       {@code #AGE_RESTRICTION_RAISE_LOCKED_AFTER_BOOKING_OPENED}.</li>
 *   <li>확인 필요 (#46) — 작성 중의 제목 · 소개 · 포스터 · 출연 변경은 이력에 쌓지 않고, 판매를 시작해 공개된 순간의 내용을 첫 버전으로 둔다(문서는
 *       예매가 열린 뒤의 변경만 말함): {@code domain.event.EventChangeHistory#isRecorded}.</li>
 *   <li>확인 필요 (#46) — 출연이 비어 있는 회차는 첫 버전을 쌓지 않는다(나중에 적으면 바꾸기 전이 빈 버전이 쌓임):
 *       {@code domain.event.EventChangeHistory#firstVersions}.</li>
 *   <li>확인 필요 (#223) — 상세 안내 · 지도 링크 · 공개용 연락처도 출연과 같이, 판매를 시작한 순간 비어 있는 칸은 첫 버전을 쌓지 않는다(나중에
 *       적으면 바꾸기 전이 빈 버전이 쌓임). 작성 중의 변경을 쌓지 않는 것도 다른 칸과 같다: {@code domain.event.EventChangeHistory#firstVersions}.</li>
 *   <li>확인 필요 (#46) — '운영자가 행사 제목을 바꿈' 알림도 이력과 같이 판매를 시작한 뒤의 변경에만 보낸다(작성 중 제목 수정마다 소유자에게
 *       메일이 가지 않게): {@code domain.event.EventChangeHistory#notifiesOwner}.</li>
 *   <li>확인 필요 (#46) — 변경 이력은 행사가 끝난 날(한국 날짜) + 6개월까지 보관하고 그다음 날부터 지운다. 회차가 모두 취소돼 끝난 날을 알 수 없는
 *       행사의 이력은 지우지 않는다: {@code domain.event.EventChangeHistory#isPurgeDue}.</li>
 *   <li>확인 필요 (#223) — 매진 임박의 "절반 이상 팔림"은 판매 제외 · 관계자 확보분도 팔린 쪽으로 센다(잔여 × 2 ≤ 전체):
 *       {@code domain.inventory.RemainingSeats}.</li>
 *   <li>확인 필요 (#223) — 목록 카드는 매진된 회차와 여유 있는 회차만 섞여 있으면 표시하지 않는다:
 *       {@code domain.inventory.RemainingSeatsBadge#forEventCard}.</li>
 *   <li>확인 필요 (#53) — 재고 오류 문구: {@code domain.inventory.InventoryErrorCode}에서 SEAT_ALREADY_TAKEN(docs/06 원문)과 찾을 수 없음 세
 *       가지(행사 오류와 같은 문구) 밖의 문구는 모두 지었다. 경우 자체가 문서에 없는 코드에는 따로 확인 필요 (#53)를 달았다 — 판매 제외 · 초대권
 *       좌석을 관객이 잡을 때 "방금 다른 분이 선택했습니다"와 갈라 "판매하지 않는 좌석입니다"로 알리는 것({@code SEAT_NOT_FOR_SALE}) 포함.</li>
 *   <li>확인 필요 (#223) — 조기예매 · 쿠폰을 만들고 고칠 때의 오류 문구(할인 값 범위, 0원이 되는 할인, 마감된 조기예매, 쓴 수 아래로 줄이기).
 *       경우는 docs/99 C · E가 정했고 문구만 지었다: {@code domain.pricing.PricingErrorCode}.</li>
 *   <li>확인 필요 (#223) — 이미 취소된 티켓 · 이미 포기한 날짜의 오류 문구(문서에 없어 지었다): {@code domain.ticket.TicketErrorCode}.</li>
 *   <li>확인 필요 (#52) — 5일 이상 묶음권의 기본 할인율은 채우지 않고(주최자가 직접 적음), 할인율 상한(20%)도 두지 않는다:
 *       {@code domain.pricing.BundleProduct#defaultDiscountRate}.</li>
 *   <li>확인 필요 (#223) — 묶음권 상품이 팔 구역(수량형, 하나 이상)을 고른다. 문서의 "묶음권 만들기"에는 이름 · 포함 날짜 · 할인율만 있다:
 *       {@code domain.pricing.BundleProductTerms#zoneIds}.</li>
 *   <li>확인 필요 (#223) — 묶음권 상품은 날짜를 id로 가리키고, 묶은 날짜가 지워지며 회차가 다른 날짜로 옮겨 가면 따라간다. 옮겨 간 날짜를 이미
 *       묶고 있으면(묶은 날짜끼리 합쳐지면) 따라가지 않는다: {@code domain.pricing.BundleProduct#followMovedDate}.</li>
 *   <li>확인 필요 (#223) — 묶음권 상품을 만들고 고칠 때(날짜 2개 미만, 구역 없음, 지정형 구역)와 관객이 고른 구역 · 회차가 묶음권에 맞지 않을 때의
 *       오류 문구. 경우는 docs/05가 정했고 문구만 지었다: {@code domain.pricing.PricingErrorCode}.</li>
 *   <li>확인 필요 (#223) — 묶음권 할인율을 정할 때 구역마다 가장 싼 회차 조합에 적용해 0원이 되면 받지 않는다(조기예매 · 쿠폰의 "가장 낮은 가격에
 *       적용해도 1원 이상"을 묶음권에도 썼다, docs/99 C "할인 값"): {@code domain.pricing.BundleProduct#create}.</li>
 *   <li>확인 필요 (#223) — 묶음권 목록의 시작 가격은 매진 여부를 보지 않는다(한 구역만 매진된 회차도 조합에 들어감):
 *       {@code domain.pricing.BundleProduct#startingPrice}.</li>
 *   <li>확인 필요 (#223) — 결제·환불 내역 설명 중 문서 예시에 없는 모양: 여러 장은 쉼표로 이음("12열 9번, 12열 10번 변경"), 관객 취소가 아닌
 *       취소는 사유를 괄호로 붙임("2매 취소 (회차 취소)"), 배상 줄은 함께 나간 환불 줄과 같은 설명: {@code domain.payment.PaymentHistoryCause#describe}.</li>
 *   <li>확인 필요 (#223) — 적용 할인 표시는 결제 줄(예매 결제 · 변경 결제)의 설명에 할인 종류별 장수로 붙인다("조기예매 2매 · 쿠폰 1매"). 할인
 *       없는 예매 결제는 설명이 없다. 문서 예시 "3/1 결제 165,000원"에 설명이 없는 것은 할인이 없었다는 뜻일 수도, 할인은 [상세](결제 상세)에서
 *       보인다는 뜻일 수도 있다 — 뒤의 뜻이면 이 요약을 목록 줄에서 빼고 결제 상세(#76)에서 장마다 보인다:
 *       {@code domain.payment.PaymentHistoryDescriptionSource#appliedDiscountSummaryOf}.</li>
 *   <li>확인 필요 (#223) — 묶음권에서 그 회차의 지연 환급을 이미 받은 티켓에 나중에 그날 몫을 돌려줄 때 그날 몫에서 지연 환급을 빼지 않고 그날 몫
 *       전액을 돌려준다(반대 순서 — 그날 몫 뒤 그 회차 지연 환급 — 는 막는다): {@code domain.payment.PaymentHistory#recordBundleDayRefund}.</li>
 *   <li>확인 필요 (#223) — 예매 자격 판정의 오류 문구(미리보기 · 오픈 전 · 판매 중단 · 마감 · 취소된 행사 · 취소된 회차 · 회차 마감 · 회차 선택
 *       제약 · 1인 총 매수). 경우는 docs/06 · docs/03 · docs/04가 정했고, 변경 중 판매 중단(docs/04 원문)과 연령 제한(docs/03 원문에 행사 이름 ·
 *       기준 나이를 채움) 밖은 문구만 지었다. 회차 선택 제약 · 1인 총 매수도 연령 제한처럼 "이 공연은 "을 앞에 붙이고, 1인 총 매수는 한도와
 *       "이미 예매한 표도 함께 셉니다."(묶음권이 끼면 "묶음권은 날짜마다 1매로 셉니다."까지)를 붙인다. 이용 정지는 계정의 문구를 그대로 쓴다:
 *       {@code domain.booking.BookingErrorCode}.</li>
 *   <li>확인 필요 (#223) — 회차 선택 제약 · 1인 총 매수는 이미 가진 티켓 가운데 취소된 회차(묶음권의 하루 취소)는 세지 않고, 출연자가 바뀌어
 *       포기한 날은 센다(티켓이 계속 가리키고 자리가 풀리는지 문서에 없음): {@code domain.booking.HeldBookingTickets}.</li>
 *   <li>확인 필요 (#223) — '행사 전체에서 한 회차만'인 행사에서는 회차 여럿을 가리키는 묶음권을 살 수 없다(문서가 둘이 함께 걸린 경우를 말하지
 *       않아 글자 그대로): {@code domain.booking.BookingEligibilityJudge#atSessionChoice}.</li>
 *   <li>확인 필요 (#223) — 예매 변경도 새로 잡는 쪽은 새 예매와 같은 판정을 받고, 바꿀 티켓은 곧 취소되므로 이미 가진 티켓에서 빼고 넘긴다고
 *       보았다(docs/08 "변경 = 취소 + 재예매"). 그래서 판매 중에 매수 제한을 낮추거나 회차 선택 제약을 건 뒤에는 이미 산 관객의 좌석 변경도 막힐 수
 *       있다(docs/04 "신규 예매부터 적용"과 갈림): {@code domain.booking.BookingPurpose#CHANGE}.</li>
 *   <li>확인 필요 (#223) — 예매 자격의 거부 까닭이 겹치면 미리보기 → 판매 상태 → 계정 → 회차 → 연령 → 회차 선택 제약 → 1인 총 매수 순서로 하나만
 *       알린다: {@code domain.booking.BookingEligibilityJudge}.</li>
 * </ul>
 */
public final class ProposedPolicy {

    /**
     * 확인 필요 (#46, 문서에 없음) — 보관 기간이 지난 행사 변경 이력(제목 · 소개 · 포스터 · 회차 출연 · 상세 안내 · 지도 링크 · 공개용 연락처)을
     * 지우는 정해진 시각 작업이 매일 도는 시각(한국 시각). 행사가 끝난 날 + 6개월({@link RecordRetentionPolicy#CHANGE_HISTORY_RETENTION_AFTER_EVENT_END})까지 보관하고 그다음 날 이 시각의 작업이
     * 지운다 — 10/14에 끝난 행사는 4/14까지 보관하고 4/15 04:00 작업이 지운다. 지울지는 한국 날짜로 판정하므로 이 시각이 그날의 판정을 바꾸지 않는다.
     * 오전 4시를 제안한다: 사람에게 보내는 것이 없는 정리 작업이라 이용이 가장 적은 새벽에 돌려 행사 줄을 잠깐 잡는 일(편집과 차례로 처리)이 편집과
     * 거의 겹치지 않게 했고, 날짜가 바뀐 뒤라 그날 지울 이력을 바로 지운다. 작성 중 행사 30일 미수정 알림의 오전 10시
     * ({@link TimePolicy#DRAFT_EVENT_IDLE_NOTICE_RUN_TIME})는 메일을 받는 사람의 때라 따르지 않았다 — 같은 시각에 돌아도 두 작업은 행사 줄을 FOR KEY SHARE로만
     * 잡고 대상도 겹치지 않아(작성 중 행사에는 이력이 없다) 맞물리지 않는다. 문서에는 "6개월 — 변경 이력 삭제"만 있고 언제 지우는지가 없다(docs/04 "행사가 끝난 뒤 일어나는 일").
     */
    public static final LocalTime EVENT_CHANGE_HISTORY_PURGE_RUN_TIME = LocalTime.of(4, 0);

    /**
     * 확인 필요 (#223, 문서에 없음) — 묶음권 상품 이름("3일 통권")의 최대 길이(글자 수). 20자를 제안한다: 티켓("3일권 · 스탠딩A") · 검표 결과
     * ("3일권 (5/3~5/5) · 2일차")에 구역 이름과 나란히 보이는 짧은 이름표라 구역 · 블록 · 입구 이름({@link QuantityPolicy#ZONE_NAME_MAXIMUM_LENGTH})과
     * 같은 상한을 썼다. docs/99 C "입력·파일 상한"에 묶음권 이름이 없다(docs/05 "조합은 자동 생성하지 않습니다 — 묶음권 만들기").
     */
    public static final int BUNDLE_PRODUCT_NAME_MAXIMUM_LENGTH = 20;

    private ProposedPolicy() {
    }
}
