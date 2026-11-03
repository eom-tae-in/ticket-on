package com.ticketon.application.pricing.port.out;

import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.domain.pricing.Coupon;
import java.time.Instant;
import java.util.List;

/**
 * 쿠폰 저장소 — 나가는 포트. 한 행사의 쿠폰을 함께 읽는다 — 한 행사 안에서 코드가 겹치지 않는지 행사의 쿠폰 전체를 본다({@link Coupon#issueAmong}).
 * 지우지 않은 쿠폰끼리는 표의 유일 색인도 코드 겹침을 막는다.
 * <p>
 * <b>사용 수와 코드는 쓰지 않는다.</b> 주최자의 저장({@link #update})은 설정(할인 · 전체 사용 가능 횟수 · 1인 1회 · 유효 기간)만 쓴다 — 코드는 발급한
 * 뒤 바꾸지 않고(docs/99 C), 사용 수는 예매가 "사용 수 + 1 ≤ 전체 횟수"인 한 문장으로 늘린다(#54 · #73).
 * <p>
 * <b>지운 쿠폰은 줄을 남긴다</b> — 쿠폰 사용과 티켓이 받은 할인이 원래 쿠폰을 가리킨다. 다시 읽을 때는 담지 않고, 그 코드는 새 쿠폰이 다시 쓸 수
 * 있다.
 * <p>
 * <b>잠금 — 행사 줄 다음이다</b>({@link EventRepository#findByIdForUpdate}). 같은 행사의 쿠폰 발급끼리는 행사 줄에서 차례로 처리되어, 뒤의 발급이 앞의
 * 쿠폰을 읽고 코드 겹침을 본다.
 */
public interface CouponRepository {

    /** 그 행사의 지우지 않은 쿠폰 — 발급한 순서. 없으면 비어 있다. 행사가 있는지는 보지 않는다. */
    List<Coupon> findByEventId(long eventId);

    /**
     * 새 쿠폰을 넣는다 — 사용 수 0.
     *
     * @return id가 붙은 쿠폰
     * @throws IllegalArgumentException 이미 id가 있으면
     */
    Coupon add(Coupon coupon);

    /**
     * 저장된 쿠폰의 설정을 지금 값으로 쓴다. 코드와 사용 수는 쓰지 않는다.
     *
     * @throws IllegalArgumentException id가 없거나, 표에 없거나 지운 쿠폰이면 — 아무것도 쓰지 않는다
     */
    void update(Coupon coupon);

    /**
     * 쿠폰을 지운다 — 줄에 지운 시각을 적는다.
     *
     * @throws IllegalArgumentException 그 행사의 지우지 않은 쿠폰이 아니면
     */
    void remove(long eventId, long couponId, Instant now);
}
