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
 */
public final class ProposedPolicy {

    /**
     * 확인 필요 (#8) — 로그인 세션 비활성 만료. 마지막 요청 뒤 이만큼 요청이 없으면 세션이 끝나 다시 로그인해야 한다.
     * 30분을 제안한다: Spring 기본값이고, 관객 예매 한 번(좌석 선택 10분 + 결제 7분, docs/06 "타이머 두 개")보다 넉넉하며,
     * 위저드 작성 중인 주최자는 자동 임시저장 요청이 세션을 이어 준다(docs/13 "자동 임시저장"). 검표 세션은 따로 정한다(#89).
     */
    public static final Duration LOGIN_SESSION_IDLE_TIMEOUT = Duration.ofMinutes(30);

    private ProposedPolicy() {
    }
}
