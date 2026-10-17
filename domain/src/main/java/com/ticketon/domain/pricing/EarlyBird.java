package com.ticketon.domain.pricing;

import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * 조기예매 — 구역 재고에서 떼어오는 할인 몫(docs/05 "조기예매"). 행사를 가리키고(docs/14 "조기예매 · 쿠폰 → 행사"), 행사당 여러 개(1차 · 2차
 * 얼리버드)를 둘 수 있다({@link EarlyBirds}).
 * <ul>
 *   <li><b>설정</b>({@link EarlyBirdTerms}) — 할인(정액 · 정률), 기간, 수량(선택), 적용 범위(기본 전체 구역 · 전체 상품, 구역별 · 상품별 끔).</li>
 *   <li><b>사용 수</b>({@link #claimedCount}) — 몫을 잡은 티켓 수. <b>좌석을 선점하는 순간 늘고, 어떤 경우에도 줄지 않는다</b>(docs/05 "소진되면
 *       영구 마감", docs/14 "조기예매 몫은 돌아오지 않습니다"). 결제 후 취소해도, 선점만 하고 만료돼도 돌아오지 않는다 — 좌석은 복구돼 정가로
 *       팔린다. 그래서 몫을 돌려주는 메서드가 없다.</li>
 *   <li><b>상태</b>({@link #status}) — 저장하지 않고 기간과 사용 수로 계산한다. 수량 소진과 기간 종료 중 먼저 오는 쪽에 끝나고, 끝나면 다시 열리지
 *       않는다.</li>
 *   <li><b>임박 단계</b>({@link #urgency}) — 남은 몫과 기간 중 더 급한 쪽. <b>도달한 최고 단계를 기억해</b>({@link #highestUrgencyReached}) 내려가지
 *       않는다.</li>
 * </ul>
 * <h2>왜 줄지 않나</h2>
 * 선점 만료분을 돌려주면 선착순이 깨진다 — 30개가 선점으로 다 잡혀 A가 정가 50,000원에 샀는데, 10분 뒤 25개가 만료돼 돌아오면 뒤에 온 B가
 * 35,000원에 산다. A가 먼저 왔는데 15,000원을 더 냈다(docs/99 B "조기예매 몫 복구"). <b>세는 값은 하나뿐이고 줄어들지 않는다.</b> 감수하는 것 —
 * 취소나 선점 만료가 생기면 의도한 30장이 실제로는 29장만 나갈 수 있다. 몫을 고갈시키려는 선점 반복은 한 번에 선점할 수 있는 매수 상한이 막는다
 * (docs/06 "1회 선택 매수").
 * <p>
 * <b>정확한 남은 수량은 관객에게 내보내지 않는다</b>(docs/05 "임박 표시") — 사용 수와 수량은 저장과 주최자의 소진 현황(docs/11 "예매 현황")에만 쓰고,
 * 관객 화면에는 상태와 임박 단계만 보인다. 남은 몫을 돌려주는 메서드를 두지 않는 까닭이다.
 * <p>
 * 몫을 잡는 일은 행사의 조기예매 묶음({@link EarlyBirds#claim})이 한다 — 한 티켓에 닿는 조기예매가 여럿일 때 고르는 순서가 거기 있다. 저장소에서
 * 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 조기예매는 id가 없다.
 */
public final class EarlyBird {

    private final Long id;
    private final long eventId;
    private EarlyBirdTerms terms;
    private int claimedCount;
    private EarlyBirdUrgency highestUrgencyReached;

    private EarlyBird(Long id, long eventId, EarlyBirdTerms terms, int claimedCount, EarlyBirdUrgency highestUrgencyReached) {
        if (eventId < 1) {
            throw new IllegalArgumentException("행사 id는 1 이상이어야 합니다: " + eventId);
        }
        Objects.requireNonNull(terms, "terms");
        if (claimedCount < 0) {
            throw new IllegalArgumentException("조기예매 사용 수는 0 이상이어야 합니다: " + claimedCount);
        }
        if (terms.quantity().isPresent() && claimedCount > terms.quantity().get().limit()) {
            throw new IllegalArgumentException("조기예매 사용 수가 수량보다 많습니다: " + claimedCount + " > " + terms.quantity().get().limit());
        }
        this.id = id;
        this.eventId = eventId;
        this.terms = terms;
        this.claimedCount = claimedCount;
        this.highestUrgencyReached = Objects.requireNonNull(highestUrgencyReached, "highestUrgencyReached");
    }

    /** 새 조기예매 — 아직 잡힌 몫이 없고, 도달한 임박 단계도 없다. */
    public static EarlyBird create(long eventId, EarlyBirdTerms terms) {
        return new EarlyBird(null, eventId, terms, 0, EarlyBirdUrgency.NONE);
    }

    /**
     * 저장소에서 읽은 값으로 조기예매를 되살린다.
     *
     * @param highestUrgencyReached 도달한 최고 임박 단계({@link #highestUrgencyReached})
     * @throws IllegalArgumentException 사용 수가 음수거나 수량보다 많으면
     */
    public static EarlyBird restore(long id, long eventId, EarlyBirdTerms terms, int claimedCount, EarlyBirdUrgency highestUrgencyReached) {
        if (id < 1) {
            throw new IllegalArgumentException("조기예매 id는 1 이상이어야 합니다: " + id);
        }
        return new EarlyBird(id, eventId, terms, claimedCount, highestUrgencyReached);
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public long eventId() {
        return eventId;
    }

    public EarlyBirdTerms terms() {
        return terms;
    }

    /** 사용 수 — 몫을 잡은 티켓 수. 줄지 않는다. 저장과 주최자의 소진 현황에만 쓰고 관객에게 내보내지 않는다. */
    public int claimedCount() {
        return claimedCount;
    }

    /**
     * 그 순간의 상태 — 기간 전이면 시작 전, 끝 순간부터 기간 종료, 기간 안에서 몫을 다 잡았으면 소진, 아니면 진행 중.
     */
    public EarlyBirdStatus status(Instant now) {
        Objects.requireNonNull(now, "now");
        DiscountPeriod period = terms.period();
        if (period.hasNotStartedAt(now)) {
            return EarlyBirdStatus.NOT_STARTED;
        }
        if (period.hasEndedAt(now)) {
            return EarlyBirdStatus.ENDED;
        }
        if (isSoldOut()) {
            return EarlyBirdStatus.SOLD_OUT;
        }
        return EarlyBirdStatus.OPEN;
    }

    /** 그 구역의 그 상품 티켓에 닿는지 — 적용 범위만 본다. 기간은 {@link #status}가 본다. */
    public boolean covers(long zoneId, TicketProduct product) {
        return terms.scope().covers(zoneId, product);
    }

    /**
     * 그 순간의 임박 단계 — 관객 화면에 남은 몫 대신 보인다(docs/05 "임박 표시"). 진행 중일 때만 보여 준다.
     * <ul>
     *   <li><b>수량과 기간 중 더 급한 쪽</b> — 남은 몫으로 본 단계({@link EarlyBirdUrgency#byQuantity})와 종료 3일 이내({@link EarlyBirdUrgency#byPeriod})
     *       중 급한 것. 수량이 없으면 기간만 본다.</li>
     *   <li><b>한 번 올라간 단계는 내려가지 않는다</b> — 도달한 최고 단계({@link #highestUrgencyReached})보다 낮게 나오지 않는다. 사용 수는 줄지
     *       않으므로 단계가 내려갈 길은 주최자가 수량이나 기간을 늘리는 것뿐이다. 바꾸기 직전의 단계를 기억해 둔다({@link #reconfigure}).</li>
     * </ul>
     */
    public EarlyBirdUrgency urgency(Instant now) {
        return highestUrgencyReached.moreUrgent(currentUrgency(now));
    }

    /**
     * 도달한 최고 임박 단계 — 저장해 두고 단계가 내려가지 않게 하는 데 쓴다. 진행 중일 때 몫을 잡거나 설정을 바꾸면 그 순간의 단계를 기억한다. 시작
     * 전에 계산된 단계는 관객에게 보인 적이 없어 기억하지 않는다.
     */
    public EarlyBirdUrgency highestUrgencyReached() {
        return highestUrgencyReached;
    }

    /**
     * 설정을 바꾼다. 판매 중에도 바꿀 수 있고 신규 예매부터 적용된다(docs/04 "예매가 열린 뒤 수정 범위 — 가격") — 이미 잡은 몫은 선점이 잠근 할인
     * 그대로다(docs/06 "선점이 잠그는 것 셋").
     * <ul>
     *   <li><b>끝난 조기예매는 바꿀 수 없다</b> — 수량을 늘리거나 기간을 늘려 소진 · 종료된 조기예매가 다시 열리면 "마감됐던 게 다시 열리는 이상한
     *       순간"이 생긴다(docs/05 "소진되면 영구 마감 — 주최자에게도 고정된 숫자"). 확인 필요 (#49) — 끝난 뒤의 수정은 문서에 없다.</li>
     *   <li><b>수량은 사용 수 아래로 줄일 수 없다</b> — 이미 잡은 몫은 돌아오지 않는다. 사용 수와 같게 줄이면 그 순간 소진된다. 수량형 정원을
     *       판매 수량 아래로 줄일 수 없는 것과 같다(docs/04). 확인 필요 (#49).</li>
     *   <li><b>임박 단계는 내려가지 않는다</b> — 바꾸기 직전의 단계를 기억해 둔다. 30% 남아 "얼마 남지 않음"이던 조기예매의 수량을 늘려도, 종료 3일
     *       안이라 "마감 임박"이던 조기예매의 기간을 늘려도 그 단계가 그대로 보인다.</li>
     * </ul>
     *
     * @return 바뀌었는지. 같은 설정이면 false
     * @throws RuleViolationException 끝난 조기예매면({@link PricingErrorCode#EARLY_BIRD_CLOSED}), 수량이 사용 수보다 적으면
     *                                ({@link PricingErrorCode#EARLY_BIRD_QUANTITY_BELOW_CLAIMED})
     */
    public boolean reconfigure(EarlyBirdTerms newTerms, Instant now) {
        Objects.requireNonNull(newTerms, "newTerms");
        if (newTerms.equals(terms)) {
            return false;
        }
        if (status(now).isClosed()) {
            throw new RuleViolationException(PricingErrorCode.EARLY_BIRD_CLOSED, "조기예매 " + id + " — " + status(now));
        }
        if (newTerms.quantity().isPresent() && newTerms.quantity().get().limit() < claimedCount) {
            throw new RuleViolationException(PricingErrorCode.EARLY_BIRD_QUANTITY_BELOW_CLAIMED,
                    "조기예매 " + id + " — 수량 " + newTerms.quantity().get().limit() + " < 사용 수 " + claimedCount);
        }
        rememberUrgencyIfOpen(now);
        terms = newTerms;
        return true;
    }

    /**
     * 티켓 N장의 몫을 잡는다 — 진행 중일 때만, 남은 몫만큼. 잡은 만큼 사용 수가 늘고 줄지 않는다. 묶음권 1장도 1장으로 센다.
     *
     * @param ticketCount 몫을 원하는 티켓 수, 1 이상
     * @return 잡은 몫의 수 — 0 이상 ticketCount 이하
     */
    int claim(int ticketCount, Instant now) {
        if (ticketCount < 1) {
            throw new IllegalArgumentException("몫을 잡을 티켓 수는 1 이상이어야 합니다: " + ticketCount);
        }
        if (status(now) != EarlyBirdStatus.OPEN) {
            return 0;
        }
        int granted = terms.quantity().map(quantity -> Math.min(ticketCount, quantity.limit() - claimedCount)).orElse(ticketCount);
        claimedCount += granted;
        rememberUrgencyIfOpen(now);
        return granted;
    }

    /** 관객에게 단계가 보이는 때(진행 중)에만 도달한 단계를 기억한다 — 시작 전에 계산된 단계는 보인 적이 없다. */
    private void rememberUrgencyIfOpen(Instant now) {
        if (status(now) == EarlyBirdStatus.OPEN) {
            highestUrgencyReached = urgency(now);
        }
    }

    private EarlyBirdUrgency currentUrgency(Instant now) {
        EarlyBirdUrgency byPeriod = EarlyBirdUrgency.byPeriod(terms.period(), now);
        return terms.quantity()
                .map(quantity -> EarlyBirdUrgency.byQuantity(quantity.limit() - claimedCount, quantity.limit()).moreUrgent(byPeriod))
                .orElse(byPeriod);
    }

    private boolean isSoldOut() {
        return terms.quantity().isPresent() && claimedCount >= terms.quantity().get().limit();
    }
}
