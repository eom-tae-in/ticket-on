package com.ticketon.application.event.port.in;

/**
 * 변경 이력의 첫 버전 쌓기 — 들어오는 포트. 판매를 시작해 판매 대기로 넘어간(공개된) 순간의 제목 · 소개 · 포스터와 출연이 적힌 회차의 출연을 첫
 * 버전으로 쌓는다(docs/03 "제목 · 소개 · 포스터는 변경 이력을 남깁니다", docs/02 "출연 (선택)"). 그 뒤의 변경은 행사를 고치는 유스케이스가 쌓는다.
 * <p>
 * <b>판매 시작 흐름(#58)이 부른다</b> — 행사 줄을 FOR UPDATE로 잡고 판매 대기로 바꿔 저장한 뒤, <b>같은 트랜잭션에서</b>. 트랜잭션 밖에서 부르면
 * 예외(Spring의 IllegalTransactionStateException)로 막는다 — 판매는 시작됐는데 첫 버전이 빠지거나, 판매 시작은 되돌려졌는데 첫 버전만 남는 일이
 * 없게. 컨트롤러가 부르는 길은 없다. 무엇을 쌓는지는 도메인({@code EventChangeHistory#firstVersions})이 정한다.
 */
public interface RecordChangeHistoryFirstVersionUseCase {

    /**
     * @throws IllegalStateException    판매를 시작하지 않았거나(작성 중) 포스터 · 일정이 없거나, 이미 이력이 있는 행사면 — 부르는 쪽의 잘못이다
     * @throws IllegalArgumentException 없는 행사이거나 권한을 확인한 주최자의 행사가 아니면 — 부르는 쪽의 잘못이다
     */
    void record(RecordChangeHistoryFirstVersionCommand command);
}
