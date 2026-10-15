package com.ticketon.domain.organizer;

import com.ticketon.domain.error.RuleViolationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 사업자 여부와 사업자 정보 — 신청서에서 함께 받는다(docs/01 "신청 항목").
 * <p>
 * <b>사업자면 사업자 정보 다섯 가지가 모두 필수이고, 비사업자면 받지 않는다.</b> 사업자등록번호는 개인 주최자에게 없으니 사업자일 때만
 * 받는다(docs/01). 사업자 정보는 행사 페이지 "판매자 정보"에 그대로 나가므로 사업자 여부와 어긋난 값을 받아 두지 않는다.
 * 신청자가 적은 값으로 만들 때는 {@link #declare}를 쓴다 — 빠졌거나 어긋난 항목을 규칙 위반으로 알려 준다.
 *
 * @param businessStatus      사업자 여부
 * @param businessInformation 사업자 정보. 사업자면 있고, 비사업자면 null이다.
 */
public record BusinessDeclaration(BusinessStatus businessStatus, BusinessInformation businessInformation) {

    public BusinessDeclaration {
        Objects.requireNonNull(businessStatus, "businessStatus");
        boolean hasInformation = businessInformation != null;
        if (hasInformation != (businessStatus == BusinessStatus.BUSINESS)) {
            throw new IllegalArgumentException("사업자만 사업자 정보를 갖습니다: " + businessStatus);
        }
    }

    /** 비사업자. */
    public static BusinessDeclaration nonBusiness() {
        return new BusinessDeclaration(BusinessStatus.NON_BUSINESS, null);
    }

    /** 사업자와 그 사업자 정보. */
    public static BusinessDeclaration business(BusinessInformation businessInformation) {
        return new BusinessDeclaration(BusinessStatus.BUSINESS, Objects.requireNonNull(businessInformation, "businessInformation"));
    }

    /**
     * 신청자가 고른 사업자 여부와 적은 사업자 항목으로 만든다. 비었거나 공백뿐인 칸은 적지 않은 것으로 본다.
     *
     * @param businessStatus             사업자 여부 — 필수
     * @param tradeName                  상호. 적지 않았으면 null이나 빈 값.
     * @param representativeName         대표자 성명. 적지 않았으면 null이나 빈 값.
     * @param businessAddress            사업장 주소. 적지 않았으면 null이나 빈 값.
     * @param businessPhoneNumber        사업장 전화번호. 적지 않았으면 null이나 빈 값.
     * @param businessRegistrationNumber 사업자등록번호. 적지 않았으면 null이나 빈 값.
     * @throws RuleViolationException   사업자인데 빠진 항목이 있으면({@link OrganizerErrorCode#BUSINESS_INFORMATION_MISSING}),
     *                                  비사업자인데 사업자 항목을 적었으면({@link OrganizerErrorCode#BUSINESS_INFORMATION_FOR_NON_BUSINESS}).
     *                                  detail에 해당 항목을 적는다.
     * @throws IllegalArgumentException 사업자등록번호가 숫자 10자리가 아니면
     */
    public static BusinessDeclaration declare(BusinessStatus businessStatus, String tradeName, String representativeName,
            String businessAddress, String businessPhoneNumber, String businessRegistrationNumber) {
        Objects.requireNonNull(businessStatus, "businessStatus");
        Map<ApplicationItem, String> businessItems = new LinkedHashMap<>();
        businessItems.put(ApplicationItem.TRADE_NAME, tradeName);
        businessItems.put(ApplicationItem.REPRESENTATIVE_NAME, representativeName);
        businessItems.put(ApplicationItem.BUSINESS_ADDRESS, businessAddress);
        businessItems.put(ApplicationItem.BUSINESS_PHONE_NUMBER, businessPhoneNumber);
        businessItems.put(ApplicationItem.BUSINESS_REGISTRATION_NUMBER, businessRegistrationNumber);
        return switch (businessStatus) {
            case BUSINESS -> declareBusiness(businessItems);
            case NON_BUSINESS -> declareNonBusiness(businessItems);
        };
    }

    public boolean isBusiness() {
        return businessStatus == BusinessStatus.BUSINESS;
    }

    private static BusinessDeclaration declareBusiness(Map<ApplicationItem, String> businessItems) {
        List<ApplicationItem> missingItems = itemsWhere(businessItems, false);
        if (!missingItems.isEmpty()) {
            throw new RuleViolationException(OrganizerErrorCode.BUSINESS_INFORMATION_MISSING,
                    "빠진 항목: " + labelsOf(missingItems));
        }
        return business(new BusinessInformation(
                businessItems.get(ApplicationItem.TRADE_NAME),
                businessItems.get(ApplicationItem.REPRESENTATIVE_NAME),
                businessItems.get(ApplicationItem.BUSINESS_ADDRESS),
                TelephoneNumber.of(businessItems.get(ApplicationItem.BUSINESS_PHONE_NUMBER)),
                BusinessRegistrationNumber.of(businessItems.get(ApplicationItem.BUSINESS_REGISTRATION_NUMBER))));
    }

    private static BusinessDeclaration declareNonBusiness(Map<ApplicationItem, String> businessItems) {
        List<ApplicationItem> filledItems = itemsWhere(businessItems, true);
        if (!filledItems.isEmpty()) {
            throw new RuleViolationException(OrganizerErrorCode.BUSINESS_INFORMATION_FOR_NON_BUSINESS,
                    "적은 사업자 항목: " + labelsOf(filledItems));
        }
        return nonBusiness();
    }

    private static List<ApplicationItem> itemsWhere(Map<ApplicationItem, String> businessItems, boolean filled) {
        return businessItems.entrySet().stream()
                .filter(entry -> isFilled(entry.getValue()) == filled)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }

    private static String labelsOf(List<ApplicationItem> items) {
        return items.stream().map(ApplicationItem::label).collect(Collectors.joining(", "));
    }
}
