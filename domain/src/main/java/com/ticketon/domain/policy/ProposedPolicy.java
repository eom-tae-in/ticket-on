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
 *   <li>확인 필요 (#38) — 예매가 열린 뒤 수정 범위 표(docs/04)에 없는 항목(유형 · 카테고리, 상세 안내 · 지도 링크 · 공개용 연락처, 예매 종료 시각
 *       밖의 예매 설정)은 판매 중에 막아 둔다: {@code domain.event.EventEditTarget}.</li>
 *   <li>확인 필요 (#46) — 작성 중의 제목 · 소개 · 포스터 · 출연 변경은 이력에 쌓지 않고, 판매를 시작해 공개된 순간의 내용을 첫 버전으로 둔다(문서는
 *       예매가 열린 뒤의 변경만 말함): {@code domain.event.EventChangeHistory#isRecorded}.</li>
 *   <li>확인 필요 (#46) — 출연이 비어 있는 회차는 첫 버전을 쌓지 않는다(나중에 적으면 바꾸기 전이 빈 버전이 쌓임):
 *       {@code domain.event.EventChangeHistory#firstVersions}.</li>
 *   <li>확인 필요 (#46) — '운영자가 행사 제목을 바꿈' 알림도 이력과 같이 판매를 시작한 뒤의 변경에만 보낸다(작성 중 제목 수정마다 소유자에게
 *       메일이 가지 않게): {@code domain.event.EventChangeHistory#notifiesOwner}.</li>
 *   <li>확인 필요 (#46) — 변경 이력은 행사가 끝난 날(한국 날짜) + 6개월까지 보관하고 그다음 날부터 지운다. 회차가 모두 취소돼 끝난 날을 알 수 없는
 *       행사의 이력은 지우지 않는다: {@code domain.event.EventChangeHistory#isPurgeDue}.</li>
 *   <li>확인 필요 (#53) — 매진 임박 "10% 이하"의 분모는 그 구역의 자리 전부다(수량형은 정원, 지정형은 배정된 활성 좌석 수 — 판매 제외 · 관계자
 *       확보분 포함): {@code domain.inventory.RemainingSeats}.</li>
 *   <li>확인 필요 (#53) — 매진 임박 10%는 반올림하기 전 비율로 본다(205석의 20석은 임박, 21석은 아님):
 *       {@code domain.inventory.RemainingSeats}.</li>
 *   <li>확인 필요 (#53) — 정원이 10석 이하인 구역은 하나도 팔리지 않아도 "10석 이하"라 매진 임박이다(문서 그대로):
 *       {@code domain.inventory.RemainingSeats}.</li>
 *   <li>확인 필요 (#53) — 재고 오류 문구: {@code domain.inventory.InventoryErrorCode}에서 SEAT_ALREADY_TAKEN(docs/06 원문)과 찾을 수 없음 세
 *       가지(행사 오류와 같은 문구) 밖의 문구는 모두 지었다. 경우 자체가 문서에 없는 코드에는 따로 확인 필요 (#53)를 달았다 — 판매 제외 · 초대권
 *       좌석을 관객이 잡을 때 "방금 다른 분이 선택했습니다"와 갈라 "판매하지 않는 좌석입니다"로 알리는 것({@code SEAT_NOT_FOR_SALE}) 포함.</li>
 * </ul>
 */
public final class ProposedPolicy {

    /**
     * 확인 필요 (#46, 문서에 없음) — 보관 기간이 지난 행사 변경 이력(제목 · 소개 · 포스터 · 회차 출연)을 지우는 정해진 시각 작업이 매일 도는 시각(한국
     * 시각). 행사가 끝난 날 + 6개월({@link RecordRetentionPolicy#CHANGE_HISTORY_RETENTION_AFTER_EVENT_END})까지 보관하고 그다음 날 이 시각의 작업이
     * 지운다 — 10/14에 끝난 행사는 4/14까지 보관하고 4/15 04:00 작업이 지운다. 지울지는 한국 날짜로 판정하므로 이 시각이 그날의 판정을 바꾸지 않는다.
     * 오전 4시를 제안한다: 사람에게 보내는 것이 없는 정리 작업이라 이용이 가장 적은 새벽에 돌려 행사 줄을 잠깐 잡는 일(편집과 차례로 처리)이 편집과
     * 거의 겹치지 않게 했고, 날짜가 바뀐 뒤라 그날 지울 이력을 바로 지운다. 작성 중 행사 30일 미수정 알림의 오전 10시
     * ({@link #DRAFT_EVENT_IDLE_NOTICE_RUN_TIME})는 메일을 받는 사람의 때라 따르지 않았다 — 같은 시각에 돌아도 두 작업은 행사 줄을 FOR KEY SHARE로만
     * 잡고 대상도 겹치지 않아(작성 중 행사에는 이력이 없다) 맞물리지 않는다. 문서에는 "6개월 — 변경 이력 삭제"만 있고 언제 지우는지가 없다(docs/04 "행사가 끝난 뒤 일어나는 일").
     */
    public static final LocalTime EVENT_CHANGE_HISTORY_PURGE_RUN_TIME = LocalTime.of(4, 0);

    private ProposedPolicy() {
    }
}
