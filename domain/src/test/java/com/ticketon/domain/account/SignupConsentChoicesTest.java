package com.ticketon.domain.account;

import static com.ticketon.domain.account.ConsentItem.MARKETING;
import static com.ticketon.domain.account.ConsentItem.MINIMUM_AGE_CONFIRMATION;
import static com.ticketon.domain.account.ConsentItem.PERSONAL_INFORMATION_COLLECTION;
import static com.ticketon.domain.account.ConsentItem.TERMS_OF_SERVICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("가입 동의 — 필수 셋 + 마케팅 선택")
class SignupConsentChoicesTest {

    private static final Instant SIGNUP_TIME = Instant.parse("2026-10-15T01:00:00Z");

    @Test
    @DisplayName("필수는 이용약관·개인정보 수집·이용·만 14세 이상 확인 셋이고, 마케팅 수신은 선택이다 (docs/01)")
    void requiredItemsAreThree() {
        assertThat(ConsentItem.requiredItems())
                .containsExactlyInAnyOrder(TERMS_OF_SERVICE, PERSONAL_INFORMATION_COLLECTION, MINIMUM_AGE_CONFIRMATION);
        assertThat(MARKETING.isRequired()).isFalse();
    }

    @Test
    @DisplayName("'필수 항목 모두 동의'는 필수 셋만 체크하고 마케팅은 묶지 않는다")
    void agreeAllRequiredDoesNotIncludeMarketing() {
        SignupConsentChoices choices = SignupConsentChoices.agreeingToAllRequired(false);

        assertThat(choices.agreedItems())
                .containsExactlyInAnyOrder(TERMS_OF_SERVICE, PERSONAL_INFORMATION_COLLECTION, MINIMUM_AGE_CONFIRMATION);
        assertThat(choices.agreesTo(MARKETING)).isFalse();
        assertThat(choices.missingRequiredItems()).isEmpty();
    }

    @Test
    @DisplayName("마케팅 수신은 '필수 항목 모두 동의'와 따로 체크한다")
    void marketingIsChosenSeparately() {
        SignupConsentChoices choices = SignupConsentChoices.agreeingToAllRequired(true);

        assertThat(choices.agreesTo(MARKETING)).isTrue();
        assertThat(choices.missingRequiredItems()).isEmpty();
    }

    @Test
    @DisplayName("빠진 필수 항목을 알려 준다 — 마케팅만 체크하면 필수 셋이 모두 빠진다")
    void reportsMissingRequiredItems() {
        assertThat(SignupConsentChoices.agreeingTo(TERMS_OF_SERVICE, PERSONAL_INFORMATION_COLLECTION).missingRequiredItems())
                .containsExactly(MINIMUM_AGE_CONFIRMATION);
        assertThat(SignupConsentChoices.agreeingTo(MARKETING).missingRequiredItems())
                .containsExactlyInAnyOrder(TERMS_OF_SERVICE, PERSONAL_INFORMATION_COLLECTION, MINIMUM_AGE_CONFIRMATION);
        assertThat(SignupConsentChoices.agreeingTo().missingRequiredItems()).hasSize(3);
    }

    @Test
    @DisplayName("네 항목 모두 동의 여부와 일시를 남긴다 — 동의하지 않은 마케팅도 '동의하지 않음'으로 남는다")
    void recordsEveryItemWithTime() {
        SignupConsentChoices choices = SignupConsentChoices.agreeingToAllRequired(false);

        assertThat(choices.recordsAt(SIGNUP_TIME)).containsExactly(
                new ConsentRecord(TERMS_OF_SERVICE, true, SIGNUP_TIME),
                new ConsentRecord(PERSONAL_INFORMATION_COLLECTION, true, SIGNUP_TIME),
                new ConsentRecord(MINIMUM_AGE_CONFIRMATION, true, SIGNUP_TIME),
                new ConsentRecord(MARKETING, false, SIGNUP_TIME));
    }

    @Test
    @DisplayName("같은 항목을 두 번 체크해도 한 번으로 본다")
    void duplicateChoiceCountsOnce() {
        assertThat(SignupConsentChoices.agreeingTo(TERMS_OF_SERVICE, TERMS_OF_SERVICE).agreedItems())
                .containsExactly(TERMS_OF_SERVICE);
    }

    @Test
    @DisplayName("항목이나 일시가 없으면 거부한다")
    void rejectsMissingValues() {
        assertThatThrownBy(() -> SignupConsentChoices.agreeingTo(TERMS_OF_SERVICE, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SignupConsentChoices.agreeingToAllRequired(false).recordsAt(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConsentRecord(null, true, SIGNUP_TIME)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConsentRecord(MARKETING, true, null)).isInstanceOf(NullPointerException.class);
    }
}
