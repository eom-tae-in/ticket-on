package com.ticketon.api.account;

import com.ticketon.domain.account.ConsentItem;
import com.ticketon.domain.account.SignupConsentChoices;
import java.util.EnumSet;
import java.util.Set;

/**
 * 가입 요청의 동의 칸 넷(직접 가입·카카오·구글 가입 같음) → 동의한 항목. 항목마다 따로 받는다 — "필수 항목 모두 동의"는 필수 셋만 한 번에
 * 체크하고 마케팅 수신은 묶지 않는다(docs/01 "약관과 동의 — 가입 시"). 필수 동의가 빠졌는지는 가입 규칙이 본다.
 */
final class SignupConsentItems {

    private SignupConsentItems() {
    }

    static SignupConsentChoices choicesOf(boolean termsOfServiceAgreed, boolean personalInformationCollectionAgreed,
            boolean minimumAgeConfirmed, boolean marketingAgreed) {
        Set<ConsentItem> agreedItems = EnumSet.noneOf(ConsentItem.class);
        if (termsOfServiceAgreed) {
            agreedItems.add(ConsentItem.TERMS_OF_SERVICE);
        }
        if (personalInformationCollectionAgreed) {
            agreedItems.add(ConsentItem.PERSONAL_INFORMATION_COLLECTION);
        }
        if (minimumAgeConfirmed) {
            agreedItems.add(ConsentItem.MINIMUM_AGE_CONFIRMATION);
        }
        if (marketingAgreed) {
            agreedItems.add(ConsentItem.MARKETING);
        }
        return new SignupConsentChoices(agreedItems);
    }
}
