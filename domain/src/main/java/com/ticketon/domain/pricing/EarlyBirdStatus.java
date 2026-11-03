package com.ticketon.domain.pricing;

/**
 * 조기예매의 지금 상태 — <b>저장하지 않고 계산한다</b>({@link EarlyBird#status}). 기간과 사용 수만으로 정해지므로 상태로 저장하면 시각이 지날 때마다
 * 고쳐야 한다(docs/14 "저장하는 것과 계산하는 것").
 * <p>
 * 끝나는 길은 둘이다 — <b>수량 소진과 기간 종료 중 먼저 오는 쪽</b>(docs/05 "종료 조건"). 어느 쪽이든 <b>다시 열리지 않는다</b>(docs/05 "소진되면
 * 영구 마감") — 사용 수는 줄지 않고, 끝난 조기예매는 수량 · 기간을 바꿀 수 없다({@link EarlyBird#reconfigure}).
 */
public enum EarlyBirdStatus {

    /** 시작 전 — 기간이 아직 오지 않았다. */
    NOT_STARTED,

    /** 진행 중 — 기간 안이고 몫이 남았다(수량이 없으면 기간 안이기만 하면 된다). */
    OPEN,

    /** 소진 — 기간 안이지만 몫을 모두 잡았다. 영구 마감이다. */
    SOLD_OUT,

    /** 기간 종료 — 끝 순간이 지났다. 소진 여부와 상관없이 이 상태다. */
    ENDED;

    /** 끝났는지 — 소진 또는 기간 종료. 다시 열리지 않는다. */
    public boolean isClosed() {
        return this == SOLD_OUT || this == ENDED;
    }
}
