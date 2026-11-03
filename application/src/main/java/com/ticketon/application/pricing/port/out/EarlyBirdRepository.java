package com.ticketon.application.pricing.port.out;

import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.domain.pricing.EarlyBird;
import com.ticketon.domain.pricing.EarlyBirds;
import java.time.Instant;

/**
 * 조기예매 저장소 — 나가는 포트. 한 행사의 조기예매를 함께 읽는다 — 기간 겹침 경고와, 한 티켓에 닿는 조기예매를 고르는 순서(만든 순서)가 행사의
 * 조기예매 전체를 본다({@link EarlyBirds}).
 * <p>
 * <b>사용 수는 쓰지 않는다.</b> 주최자의 저장({@link #update})은 설정(할인 · 기간 · 수량 · 적용 범위)과 도달한 임박 단계만 쓴다 — 사용 수는 선점이
 * "사용 수 + n ≤ 수량"인 한 문장으로만 늘린다(#54). 주최자의 저장이 읽은 사용 수를 다시 쓰면 그 사이 늘어난 몫이 줄어든다(docs/05 "소진되면 영구
 * 마감" — 줄지 않는다).
 * <p>
 * <b>지운 조기예매는 줄을 남긴다</b> — 티켓이 받은 할인이 원래 조기예매를 가리킨다(docs/14 "받은 할인 — 조기예매가 끝나도 남음"). 다시 읽을 때는
 * 담지 않는다.
 * <p>
 * <b>잠금 — 행사 줄 다음이다.</b> 조기예매를 읽고 고쳐 저장하는 흐름은 먼저 행사 줄을 FOR UPDATE로 잠근다({@link EventRepository#findByIdForUpdate}).
 * 같은 행사의 가격 저장은 행사 줄에서 차례로 처리되므로 조기예매 줄은 따로 잠그지 않는다.
 */
public interface EarlyBirdRepository {

    /** 그 행사의 지우지 않은 조기예매 — 만든 순서. 없으면 비어 있다. 행사가 있는지는 보지 않는다. */
    EarlyBirds findByEventId(long eventId);

    /**
     * 새 조기예매를 넣는다 — 사용 수 0, 도달한 임박 단계는 지금 값.
     *
     * @return id가 붙은 조기예매
     * @throws IllegalArgumentException 이미 id가 있으면
     */
    EarlyBird add(EarlyBird earlyBird);

    /**
     * 저장된 조기예매의 설정과 도달한 임박 단계를 지금 값으로 쓴다. 사용 수는 쓰지 않는다.
     *
     * @throws IllegalArgumentException id가 없거나, 표에 없거나 지운 조기예매면 — 아무것도 쓰지 않는다
     */
    void update(EarlyBird earlyBird);

    /**
     * 조기예매를 지운다 — 줄에 지운 시각을 적는다.
     *
     * @throws IllegalArgumentException 그 행사의 지우지 않은 조기예매가 아니면
     */
    void remove(long eventId, long earlyBirdId, Instant now);
}
