package com.ticketon.application.pricing.port.out;

import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.domain.pricing.BundleProduct;
import java.time.Instant;
import java.util.List;

/**
 * 묶음권 상품 저장소 — 나가는 포트. 상품은 이름 · 할인율과 함께 묶는 날짜 · 파는 구역을 갖는다(docs/05 "묶음권 (수량형 전용)").
 * <p>
 * <b>날짜 · 구역은 id로 가리킨다.</b> 묶은 날짜가 지워지면 지운 날짜를 그대로 가리킨다 — 날짜 줄은 지운 시각을 적어 남는다. 회차를 옮겨 날짜가
 * 빠지면 옮겨 간 날짜로 바꾸고({@link BundleProduct#followMovedDate}), 구역이 지워지면 빼서({@link BundleProduct#dropRemovedZone}) 저장한다.
 * <p>
 * <b>지운 상품은 줄을 남긴다</b> — 그 상품으로 산 티켓이 원래 상품을 가리킨다. 다시 읽을 때는 담지 않는다.
 * <p>
 * <b>잠금 — 행사 줄 다음이다</b>({@link EventRepository#findByIdForUpdate}). 같은 행사의 가격 저장과 일정 · 도면 편집은 행사 줄에서 차례로
 * 처리되므로 상품 줄은 따로 잠그지 않는다.
 */
public interface BundleProductRepository {

    /** 그 행사의 지우지 않은 묶음권 상품 — 만든 순서. 없으면 비어 있다. 행사가 있는지는 보지 않는다. */
    List<BundleProduct> findByEventId(long eventId);

    /**
     * 새 묶음권 상품을 넣는다 — 이름 · 할인율과 묶는 날짜 · 파는 구역.
     *
     * @return id가 붙은 상품
     * @throws IllegalArgumentException 이미 id가 있으면
     */
    BundleProduct add(BundleProduct bundleProduct);

    /**
     * 저장된 상품을 지금 값(이름 · 할인율 · 날짜 · 구역)으로 쓴다.
     *
     * @throws IllegalArgumentException id가 없거나, 표에 없거나 지운 상품이면 — 아무것도 쓰지 않는다
     */
    void update(BundleProduct bundleProduct);

    /**
     * 묶음권 상품을 지운다 — 줄에 지운 시각을 적는다. 묶는 날짜 · 파는 구역 줄은 그대로 남긴다.
     *
     * @throws IllegalArgumentException 그 행사의 지우지 않은 상품이 아니면
     */
    void remove(long eventId, long bundleProductId, Instant now);
}
