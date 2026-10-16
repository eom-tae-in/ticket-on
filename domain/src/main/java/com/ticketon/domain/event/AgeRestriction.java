package com.ticketon.domain.event;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * 연령 제한 — 없음, 또는 만 N세 이상(docs/03 "연령 제한"). <b>처음 값은 없음</b>이다({@link #none}).
 * <p>
 * 생년월일상 나이가 모자라면 예매를 막는다 — 경고만 하면 막는 효과가 없다. 기준 나이는 그 회차 날짜의 만 나이(묶음권은 첫 날)이고, 관객이
 * 회차를 고르는 순간 본다(docs/06 "전체 흐름"). 판정은 예매 자격 판정이 한다.
 * <p>
 * 생년월일은 자기 신고라 검증되지 않으므로, 연령 제한을 걸 때 주최자에게 그렇다고 알린다({@link #organizerNotice}).
 * <p>
 * 없음은 "만 0세 이상"과 같아 기준 나이 0으로 갖는다. 기준 나이의 상한은 문서에 없어 두지 않았다.
 *
 * @param minimumAge 기준 나이 — 만 이 나이 이상만 예매한다. 0이면 제한 없음
 */
public record AgeRestriction(int minimumAge) {

    /** 연령 제한을 걸 때 주최자에게 보여 주는 안내 — docs/03 "연령 제한"의 문구 그대로. */
    public static final String ORGANIZER_NOTICE = "연령 정보는 본인이 입력한 값이며 검증되지 않습니다. 실제 확인은 현장 신분증으로 해주세요.";

    private static final AgeRestriction NONE = new AgeRestriction(0);

    public AgeRestriction {
        if (minimumAge < 0) {
            throw new IllegalArgumentException("기준 나이는 0 이상이어야 합니다: " + minimumAge);
        }
    }

    /** 제한 없음 — 처음 값. */
    public static AgeRestriction none() {
        return NONE;
    }

    /**
     * 만 age세 이상. 예: {@code atLeast(19)} — "이 공연은 만 19세 이상만 예매할 수 있습니다".
     *
     * @param age 기준 나이 (1 이상). 제한을 없애려면 {@link #none}
     */
    public static AgeRestriction atLeast(int age) {
        if (age < 1) {
            throw new IllegalArgumentException("연령 제한의 기준 나이는 1 이상이어야 합니다: " + age);
        }
        return new AgeRestriction(age);
    }

    /** 연령 제한이 걸려 있는지. */
    public boolean isRestricted() {
        return minimumAge > 0;
    }

    /** 기준 나이 — 제한이 없으면 비어 있다. */
    public OptionalInt requiredAge() {
        return isRestricted() ? OptionalInt.of(minimumAge) : OptionalInt.empty();
    }

    /** 이 설정일 때 주최자에게 보여 줄 안내 — 제한이 걸려 있으면 {@link #ORGANIZER_NOTICE}, 없으면 없다. */
    public Optional<String> organizerNotice() {
        return isRestricted() ? Optional.of(ORGANIZER_NOTICE) : Optional.empty();
    }
}
