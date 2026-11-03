package com.ticketon.domain.payment;

import com.ticketon.domain.pricing.DiscountKind;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 내역 줄의 설명을 만들 때 따라가 읽는 값 — 대상 티켓의 지금 표기, 티켓이 받은 할인, 회차의 지금 날짜. 내역 줄은 이 값들을 글자로 복사하지 않고
 * 티켓 id · 회차 id만 갖는다. 블록 이름 오타를 고치거나 회차를 연기하면 내역의 설명도 따라 바뀐다(docs/99 B "티켓에 좌석 표기를 글자로 복사",
 * docs/07 "티켓이 직접 갖는 것과 가리키기만 하는 것").
 * <p>
 * 한 예매 건의 내역을 그릴 때 그 예매 건의 티켓 · 회차를 한 번에 담아 모든 줄에 같이 넘긴다. 다른 티켓 · 회차가 더 있어도 된다.
 *
 * @param ticketLabels         티켓 id마다 지금 표기({@link PaymentHistoryTicketLabel} — 지정석 "12열 10번", 수량형 "스탠딩A · 0091-2")
 * @param appliedDiscountKinds 티켓 id마다 받은 할인의 종류({@code Ticket#appliedDiscountKind}) — 티켓이 직접 가져 바뀌지 않는다
 * @param sessionDates         회차 id마다 지금 날짜 — 연기하면 따라 바뀐다
 */
public record PaymentHistoryDescriptionSource(Map<Long, PaymentHistoryTicketLabel> ticketLabels,
        Map<Long, DiscountKind> appliedDiscountKinds, Map<Long, LocalDate> sessionDates) {

    private static final String LABEL_SEPARATOR = ", ";
    private static final String PART_SEPARATOR = " · ";

    public PaymentHistoryDescriptionSource {
        ticketLabels = Map.copyOf(ticketLabels);
        appliedDiscountKinds = Map.copyOf(appliedDiscountKinds);
        sessionDates = Map.copyOf(sessionDates);
    }

    /**
     * 티켓들의 지금 표기를 쉼표로 잇는다 — "12열 9번, 12열 10번". 수량형 표기("스탠딩A · 0091-2")가 가운뎃점을 쓰므로 티켓 사이는 쉼표다.
     *
     * @throws IllegalArgumentException 표기가 없는 티켓이 있으면
     */
    String labelsOf(List<Long> ticketIds) {
        return ticketIds.stream().map(this::labelOf).map(PaymentHistoryTicketLabel::value)
                .collect(Collectors.joining(LABEL_SEPARATOR));
    }

    /**
     * 받은 할인을 종류별 장수로 — "조기예매 2매 · 쿠폰 1매"(docs/08 "적용된 할인도 여기 표시합니다"). 조기예매와 쿠폰 중 유리한 하나를 티켓마다
     * 자동으로 고르므로(docs/05) 무엇이 적용됐는지 보여 준다. 할인을 받은 티켓이 없으면 비어 있다.
     * <p>
     * 확인 필요 (#223) — 문서는 "적용된 할인도 여기 표시합니다"라고만 하고 어디에 어떤 모양으로 보일지는 적지 않았다. 결제 줄의 설명에 종류별
     * 장수로 붙였다. 문서 예시의 "3/1 결제 165,000원"에 설명이 없는 것은 할인이 없었다는 뜻일 수도, 할인은 [상세](결제 상세)에서 보인다는 뜻일
     * 수도 있다 — 뒤의 뜻이면 이 요약을 목록 줄에서 빼고 결제 상세에서 장마다 보인다.
     *
     * @throws IllegalArgumentException 할인 종류가 없는 티켓이 있으면
     */
    Optional<String> appliedDiscountSummaryOf(List<Long> ticketIds) {
        Map<DiscountKind, Integer> ticketCounts = new EnumMap<>(DiscountKind.class);
        for (long ticketId : ticketIds) {
            DiscountKind kind = discountKindOf(ticketId);
            if (kind != DiscountKind.NONE) {
                ticketCounts.merge(kind, 1, Integer::sum);
            }
        }
        if (ticketCounts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ticketCounts.entrySet().stream()
                .map(count -> discountLabelOf(count.getKey()) + " " + count.getValue() + "매")
                .collect(Collectors.joining(PART_SEPARATOR)));
    }

    /**
     * 회차의 지금 날짜를 "5/4"로.
     *
     * @throws IllegalArgumentException 날짜가 없는 회차면
     */
    String monthDayOf(long sessionId) {
        LocalDate date = sessionDates.get(sessionId);
        if (date == null) {
            throw new IllegalArgumentException("회차의 날짜가 없습니다: 회차 " + sessionId);
        }
        return date.getMonthValue() + "/" + date.getDayOfMonth();
    }

    /** 설명 조각들을 가운뎃점으로 잇는다 — "15열 3번 · 쿠폰 1매". */
    static String joinParts(List<String> parts) {
        return String.join(PART_SEPARATOR, parts);
    }

    private PaymentHistoryTicketLabel labelOf(long ticketId) {
        PaymentHistoryTicketLabel label = ticketLabels.get(ticketId);
        if (label == null) {
            throw new IllegalArgumentException("티켓 표기가 없습니다: 티켓 " + ticketId);
        }
        return label;
    }

    private DiscountKind discountKindOf(long ticketId) {
        DiscountKind kind = appliedDiscountKinds.get(ticketId);
        if (kind == null) {
            throw new IllegalArgumentException("티켓이 받은 할인 종류가 없습니다: 티켓 " + ticketId);
        }
        return kind;
    }

    private static String discountLabelOf(DiscountKind kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case EARLY_BIRD -> "조기예매";
            case COUPON -> "쿠폰";
            case NONE -> throw new IllegalArgumentException("할인이 없는 티켓은 표시하지 않습니다");
        };
    }
}
