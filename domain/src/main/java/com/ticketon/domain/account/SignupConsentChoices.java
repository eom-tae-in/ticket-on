package com.ticketon.domain.account;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 가입 화면에서 관객이 체크한 동의 항목.
 * <p>
 * 필수 셋 중 하나라도 빠지면 가입할 수 없고({@link #missingRequiredItems()}), 마케팅 수신은 체크하지 않아도 가입된다
 * (docs/01 "약관과 동의 — 가입 시").
 *
 * @param agreedItems 동의한 항목
 */
public record SignupConsentChoices(Set<ConsentItem> agreedItems) {

    public SignupConsentChoices {
        Objects.requireNonNull(agreedItems, "agreedItems");
        agreedItems = Set.copyOf(agreedItems);
    }

    /** 체크한 항목만 동의한 것으로 만든다. */
    public static SignupConsentChoices agreeingTo(ConsentItem... agreedItems) {
        return new SignupConsentChoices(Set.copyOf(List.of(agreedItems)));
    }

    /**
     * "필수 항목 모두 동의"를 체크했을 때 — 필수 셋에 동의한다. 마케팅 수신은 이 체크박스에 묶지 않고 따로 고른 값을 받는다
     * (docs/01 "약관과 동의 — 가입 시").
     *
     * @param marketingAgreed 마케팅 수신을 따로 체크했는지
     */
    public static SignupConsentChoices agreeingToAllRequired(boolean marketingAgreed) {
        EnumSet<ConsentItem> agreedItems = EnumSet.copyOf(ConsentItem.requiredItems());
        if (marketingAgreed) {
            agreedItems.add(ConsentItem.MARKETING);
        }
        return new SignupConsentChoices(agreedItems);
    }

    /** 그 항목에 동의했는지. */
    public boolean agreesTo(ConsentItem item) {
        return agreedItems.contains(item);
    }

    /** 동의하지 않은 필수 항목. 비어 있어야 가입할 수 있다. */
    public Set<ConsentItem> missingRequiredItems() {
        EnumSet<ConsentItem> missingItems = EnumSet.copyOf(ConsentItem.requiredItems());
        missingItems.removeAll(agreedItems);
        return missingItems;
    }

    /** 네 항목 모두의 동의 기록 — 동의한 항목도, 동의하지 않은 항목도 같은 일시로 남긴다. */
    public List<ConsentRecord> recordsAt(Instant decidedAt) {
        Objects.requireNonNull(decidedAt, "decidedAt");
        return Arrays.stream(ConsentItem.values())
                .map(item -> new ConsentRecord(item, agreesTo(item), decidedAt))
                .toList();
    }
}
