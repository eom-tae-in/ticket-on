package com.ticketon.domain.account;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * 가입 때 받는 동의 항목 — 필수 셋과 선택 하나(docs/01 "약관과 동의 — 가입 시").
 * <p>
 * <b>"필수 항목 모두 동의" 체크박스는 필수 셋만 한 번에 체크한다</b>({@link #requiredItems()}). 마케팅 수신은 여기에 묶지 않고
 * 따로 고른다. 서두르다 원치 않는 광고 동의를 하지 않게 하려는 것이다(docs/01, docs/15 한서윤, docs/99 D "가입 동의").
 * <p>
 * 이 이름은 동의 기록에 그대로 남으므로 바꾸지 않는다.
 */
public enum ConsentItem {

    /** 서비스 이용약관 — 필수. */
    TERMS_OF_SERVICE(true),

    /** 개인정보 수집·이용 — 필수. 수집 항목과 이용 목적은 {@link PersonalInformationItem}. */
    PERSONAL_INFORMATION_COLLECTION(true),

    /**
     * 만 14세 이상 확인("만 14세 이상입니다") — 필수.
     * 생년월일 차단과 함께 두는 연령 허위 기재 방지 장치다(docs/01 "전화번호"의 만 14세 미만 차단, docs/99 A-3).
     */
    MINIMUM_AGE_CONFIRMATION(true),

    /** 마케팅 수신 — 선택. 언제든 철회할 수 있다. 동의한 사람에게만 관리자가 신규 행사 소식을 보낸다(docs/12). */
    MARKETING(false);

    private final boolean required;

    ConsentItem(boolean required) {
        this.required = required;
    }

    /** 동의하지 않으면 가입할 수 없는 항목인지. */
    public boolean isRequired() {
        return required;
    }

    /** 필수 항목 셋 — "필수 항목 모두 동의" 체크박스가 한 번에 체크하는 항목이다. 마케팅은 들어가지 않는다. */
    public static Set<ConsentItem> requiredItems() {
        EnumSet<ConsentItem> requiredItems = EnumSet.noneOf(ConsentItem.class);
        Arrays.stream(values()).filter(ConsentItem::isRequired).forEach(requiredItems::add);
        return requiredItems;
    }
}
