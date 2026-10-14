package com.ticketon.domain.policy;

/**
 * '확인 필요' 구역 — 문서에 없어 제안값으로 둔 기본값·기술값.
 * <p>
 * 여기 값은 확정되지 않았다. 상수마다 설명 첫머리에 "확인 필요 (#제안한 이슈 번호)"를 적고,
 * 무엇의 값인지, 왜 그 값을 제안했는지, 근거가 된 문서 위치를 한 줄로 단다.
 * <p>
 * #135에서 사용자가 확정하면 docs/99 C에 기록하고, 값을 {@link TimePolicy}·{@link QuantityPolicy}·
 * {@link RecordRetentionPolicy} 중 맞는 곳으로 옮긴 뒤 여기서 지운다. 제안값과 달라지면 관련 테스트도 고친다.
 * <p>
 * 아직 제안값이 없다.
 */
public final class ProposedPolicy {

    private ProposedPolicy() {
    }
}
