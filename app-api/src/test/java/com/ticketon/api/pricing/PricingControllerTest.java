package com.ticketon.api.pricing;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.event.port.in.SessionPriceAdjustmentResult;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.pricing.port.in.AddBundleProductCommand;
import com.ticketon.application.pricing.port.in.AddBundleProductUseCase;
import com.ticketon.application.pricing.port.in.AddEarlyBirdCommand;
import com.ticketon.application.pricing.port.in.AddEarlyBirdUseCase;
import com.ticketon.application.pricing.port.in.BundleProductResult;
import com.ticketon.application.pricing.port.in.ChangeBundleProductUseCase;
import com.ticketon.application.pricing.port.in.ChangeCouponCommand;
import com.ticketon.application.pricing.port.in.ChangeCouponUseCase;
import com.ticketon.application.pricing.port.in.ChangeEarlyBirdUseCase;
import com.ticketon.application.pricing.port.in.CouponResult;
import com.ticketon.application.pricing.port.in.DiscountMethod;
import com.ticketon.application.pricing.port.in.DiscountResult;
import com.ticketon.application.pricing.port.in.EarlyBirdResult;
import com.ticketon.application.pricing.port.in.GetPricingStepQuery;
import com.ticketon.application.pricing.port.in.GetPricingStepUseCase;
import com.ticketon.application.pricing.port.in.IssueCouponCommand;
import com.ticketon.application.pricing.port.in.IssueCouponUseCase;
import com.ticketon.application.pricing.port.in.PricingStepResult;
import com.ticketon.application.pricing.port.in.RemoveBundleProductUseCase;
import com.ticketon.application.pricing.port.in.RemoveCouponUseCase;
import com.ticketon.application.pricing.port.in.RemoveEarlyBirdCommand;
import com.ticketon.application.pricing.port.in.RemoveEarlyBirdUseCase;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.SessionPriceAdjustmentKind;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.policy.Percent;
import com.ticketon.domain.pricing.BundleProductName;
import com.ticketon.domain.pricing.BundleProductTerms;
import com.ticketon.domain.pricing.CouponCode;
import com.ticketon.domain.pricing.CouponTerms;
import com.ticketon.domain.pricing.Discount;
import com.ticketon.domain.pricing.DiscountPeriod;
import com.ticketon.domain.pricing.EarlyBirdQuantity;
import com.ticketon.domain.pricing.EarlyBirdScope;
import com.ticketon.domain.pricing.EarlyBirdStatus;
import com.ticketon.domain.pricing.EarlyBirdTerms;
import com.ticketon.domain.pricing.Money;
import com.ticketon.domain.pricing.PricingErrorCode;
import com.ticketon.domain.pricing.TicketProduct;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 위저드 4단계 API — 경로의 주최자 · 행사 · 조기예매 · 쿠폰 · 묶음권 id와 로그인한 운영진을 그대로 유스케이스에 넘기는지, 입력 검증(400)과 응답
 * 모양, 유스케이스가 거절할 때의 상태 코드를 본다. 유스케이스는 흉내 낸다. 늦여름(docs/15 이도현)의 1차 얼리버드 30% · 팬 쿠폰 · 3일 통권 7%.
 */
@WebMvcTest(controllers = {EventPricingController.class, EarlyBirdController.class, CouponController.class, BundleProductController.class})
@Import(SecurityConfiguration.class)
@DisplayName("위저드 4단계 API (가격 · 조기예매 · 쿠폰 · 묶음권)")
class PricingControllerTest {

    private static final String EVENT_PATH = "/api/organizer/organizers/12/events/7";

    private static final AccountRef PLANNER = AccountRef.of(AccountKind.STAFF, 2);

    private static final ActingStaff PLANNER_IN_LATE_SUMMER = new ActingStaff(PLANNER, 12);

    /** 4/1 0시 ~ 4/21 0시(한국 시각). */
    private static final DiscountPeriod APRIL = DiscountPeriod.of(Instant.parse("2026-03-31T15:00:00Z"), Instant.parse("2026-04-20T15:00:00Z"));

    private static final String APRIL_JSON = """
            {"startsAt": "2026-04-01T00:00:00+09:00", "endsAt": "2026-04-21T00:00:00+09:00"}""";

    private static final EarlyBirdResult FIRST_EARLY_BIRD = new EarlyBirdResult(5, new DiscountResult(DiscountMethod.RATE, 30),
            APRIL.startsAt(), APRIL.endsAt(), 30, 12, EarlyBirdStatus.OPEN, true, List.of(32L), List.of(), List.of(6L));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetPricingStepUseCase getPricingStepUseCase;

    @MockitoBean
    private AddEarlyBirdUseCase addEarlyBirdUseCase;

    @MockitoBean
    private ChangeEarlyBirdUseCase changeEarlyBirdUseCase;

    @MockitoBean
    private RemoveEarlyBirdUseCase removeEarlyBirdUseCase;

    @MockitoBean
    private IssueCouponUseCase issueCouponUseCase;

    @MockitoBean
    private ChangeCouponUseCase changeCouponUseCase;

    @MockitoBean
    private RemoveCouponUseCase removeCouponUseCase;

    @MockitoBean
    private AddBundleProductUseCase addBundleProductUseCase;

    @MockitoBean
    private ChangeBundleProductUseCase changeBundleProductUseCase;

    @MockitoBean
    private RemoveBundleProductUseCase removeBundleProductUseCase;

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.with(csrf()).with(loggedInAs(PLANNER)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Nested
    @DisplayName("4단계 보기")
    class PricingStep {

        @Test
        @DisplayName("구역 기본가 · 회차 가격 · 조기예매 · 쿠폰 · 묶음권 · 할인율 기본값을 돌려준다 — 시각은 한국 시각, 종일형 회차의 시작 시각은 null")
        void returnsPricingStep() throws Exception {
            given(getPricingStepUseCase.get(new GetPricingStepQuery(PLANNER_IN_LATE_SUMMER, 7))).willReturn(new PricingStepResult(7,
                    List.of(new PricingStepResult.ZonePrice(31, ZoneKind.QUANTITY, "스탠딩A", 35_000)),
                    List.of(new PricingStepResult.SessionPrices(503, 53, LocalDate.of(2026, 5, 3), null,
                            new SessionPriceAdjustmentResult(SessionPriceAdjustmentKind.AMOUNT, 10_000L, null),
                            List.of(new PricingStepResult.ZoneSessionPrice(31, 45_000L)))),
                    List.of(FIRST_EARLY_BIRD),
                    List.of(new CouponResult(6, "FAN5000", new DiscountResult(DiscountMethod.FIXED_AMOUNT, 5_000), 100, 3, true,
                            APRIL.startsAt(), APRIL.endsAt())),
                    List.of(new BundleProductResult(71, "3일 통권", List.of(53L, 54L, 55L), List.of(31L), 7, 130_200L)),
                    List.of(new PricingStepResult.BundleDiscountDefault(2, 3), new PricingStepResult.BundleDiscountDefault(3, 7))));

            mockMvc.perform(get(EVENT_PATH + "/pricing").with(loggedInAs(PLANNER)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"eventId": 7,
                             "zones": [{"zoneId": 31, "kind": "QUANTITY", "name": "스탠딩A", "basePrice": 35000}],
                             "sessions": [{"sessionId": 503, "dateId": 53, "date": "2026-05-03", "startTime": null,
                                           "priceAdjustment": {"kind": "AMOUNT", "amount": 10000, "percent": null},
                                           "prices": [{"zoneId": 31, "price": 45000}]}],
                             "earlyBirds": [{"earlyBirdId": 5, "discount": {"method": "RATE", "value": 30},
                                             "period": {"startsAt": "2026-04-01T00:00:00+09:00", "endsAt": "2026-04-21T00:00:00+09:00"},
                                             "quantity": 30, "claimedCount": 12, "status": "OPEN", "singleSessionExcluded": true,
                                             "excludedZoneIds": [32], "excludedBundleProductIds": [], "overlappingEarlyBirdIds": [6]}],
                             "coupons": [{"couponId": 6, "code": "FAN5000", "discount": {"method": "FIXED_AMOUNT", "value": 5000},
                                          "usageLimit": 100, "usedCount": 3, "onePerPerson": true,
                                          "validity": {"startsAt": "2026-04-01T00:00:00+09:00", "endsAt": "2026-04-21T00:00:00+09:00"}}],
                             "bundleProducts": [{"bundleProductId": 71, "name": "3일 통권", "dateIds": [53, 54, 55], "zoneIds": [31],
                                                 "discountRate": 7, "startingPrice": 130200}],
                             "bundleDiscountDefaults": [{"dateCount": 2, "discountRate": 3}, {"dateCount": 3, "discountRate": 7}]}""",
                            JsonCompareMode.STRICT));
        }

        @Test
        @DisplayName("다른 주최자의 행사면 유스케이스가 막아 404다")
        void notFound() throws Exception {
            willThrow(new NotFoundException(EventErrorCode.EVENT_NOT_FOUND)).given(getPricingStepUseCase).get(any());

            mockMvc.perform(get(EVENT_PATH + "/pricing").with(loggedInAs(PLANNER)))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("EVENT_NOT_FOUND", "행사를 찾을 수 없습니다."));
        }
    }

    @Nested
    @DisplayName("조기예매")
    class EarlyBirds {

        @Test
        @DisplayName("1차 얼리버드(30%, 4월, 30장, 1일권 · 구역 32 끔)를 더하면 그대로 넘기고 201에 겹치는 조기예매를 담아 돌려준다")
        void addsEarlyBird() throws Exception {
            EarlyBirdTerms terms = EarlyBirdTerms.limited(Discount.rate(Percent.of(30)), APRIL, EarlyBirdQuantity.of(30),
                    EarlyBirdScope.everything().withoutProduct(TicketProduct.singleSession()).withoutZone(32));
            given(addEarlyBirdUseCase.add(new AddEarlyBirdCommand(PLANNER_IN_LATE_SUMMER, 7, terms))).willReturn(FIRST_EARLY_BIRD);

            mockMvc.perform(json(post(EVENT_PATH + "/early-birds"), """
                            {"discount": {"method": "RATE", "value": 30}, "period": %s, "quantity": 30, "singleSessionExcluded": true,
                             "excludedZoneIds": [32], "excludedBundleProductIds": []}""".formatted(APRIL_JSON)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.earlyBirdId").value(5))
                    .andExpect(jsonPath("$.overlappingEarlyBirdIds[0]").value(6));
        }

        @Test
        @DisplayName("빈 칸은 칸마다 까닭을 담아 400이고 유스케이스를 부르지 않는다")
        void rejectsBlankFields() throws Exception {
            mockMvc.perform(json(post(EVENT_PATH + "/early-birds"), """
                            {"discount": null, "period": null, "singleSessionExcluded": null, "excludedZoneIds": null,
                             "excludedBundleProductIds": null}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'discount')].message").value("할인을 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'period')].message").value("기간을 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'singleSessionExcluded')].message").value("1일권 적용 여부를 골라 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'excludedZoneIds')].message").value("끈 구역 목록을 보내 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'excludedBundleProductIds')].message").value("끈 묶음권 목록을 보내 주세요."))
                    .andExpect(jsonPath("$.fieldErrors.length()").value(5));

            verifyNoInteractions(addEarlyBirdUseCase);
        }

        @Test
        @DisplayName("정률 100% · 끝이 시작보다 앞선 기간 · 0장 수량은 도메인과 같은 규칙으로 400이다")
        void rejectsOutOfRuleFields() throws Exception {
            mockMvc.perform(json(post(EVENT_PATH + "/early-birds"), """
                            {"discount": {"method": "RATE", "value": 100},
                             "period": {"startsAt": "2026-04-21T00:00:00+09:00", "endsAt": "2026-04-01T00:00:00+09:00"},
                             "quantity": 0, "singleSessionExcluded": false, "excludedZoneIds": [], "excludedBundleProductIds": []}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'discount.value')].message")
                            .value("할인 금액은 1원 이상, 할인율은 1%에서 99% 사이로 정해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'period.endsAt')].message").value("끝 시각을 시작 시각보다 뒤로 정해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'quantity')].message").value("수량은 1장 이상으로 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors.length()").value(3));

            verifyNoInteractions(addEarlyBirdUseCase);
        }

        @Test
        @DisplayName("정액 0원도 400이다")
        void rejectsZeroFixedAmount() throws Exception {
            mockMvc.perform(json(post(EVENT_PATH + "/early-birds"), """
                            {"discount": {"method": "FIXED_AMOUNT", "value": 0}, "period": %s, "singleSessionExcluded": false,
                             "excludedZoneIds": [], "excludedBundleProductIds": []}""".formatted(APRIL_JSON)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'discount.value')]").exists());

            verifyNoInteractions(addEarlyBirdUseCase);
        }

        @Test
        @DisplayName("끝난 조기예매를 고치면 유스케이스가 막아 409, 없는 조기예매를 지우면 404다")
        void ruleViolations() throws Exception {
            willThrow(new RuleViolationException(PricingErrorCode.EARLY_BIRD_CLOSED)).given(changeEarlyBirdUseCase).change(any());
            mockMvc.perform(json(put(EVENT_PATH + "/early-birds/5"), """
                            {"discount": {"method": "RATE", "value": 20}, "period": %s, "singleSessionExcluded": false,
                             "excludedZoneIds": [], "excludedBundleProductIds": []}""".formatted(APRIL_JSON)))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("EARLY_BIRD_CLOSED", "이미 마감된 조기예매는 바꿀 수 없습니다."));

            willThrow(new NotFoundException(PricingErrorCode.EARLY_BIRD_NOT_FOUND)).given(removeEarlyBirdUseCase).remove(any());
            mockMvc.perform(delete(EVENT_PATH + "/early-birds/404").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("EARLY_BIRD_NOT_FOUND", "조기예매를 찾을 수 없습니다."));
        }

        @Test
        @DisplayName("지우면 경로의 id를 넘기고 204다")
        void removes() throws Exception {
            mockMvc.perform(delete(EVENT_PATH + "/early-birds/5").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isNoContent());

            then(removeEarlyBirdUseCase).should().remove(new RemoveEarlyBirdCommand(PLANNER_IN_LATE_SUMMER, 7, 5));
        }
    }

    @Nested
    @DisplayName("쿠폰")
    class Coupons {

        private static final CouponTerms FAN_COUPON = new CouponTerms(Discount.fixedAmount(Money.of(5_000)), 100, true, APRIL);

        private static final CouponResult FAN_COUPON_RESULT = new CouponResult(6, "SPRING20", new DiscountResult(DiscountMethod.FIXED_AMOUNT,
                5_000), 100, 0, true, APRIL.startsAt(), APRIL.endsAt());

        @Test
        @DisplayName("코드는 앞뒤 공백을 걷고 대문자로 맞춰 넘기고 201에 쿠폰을 돌려준다")
        void issuesCoupon() throws Exception {
            given(issueCouponUseCase.issue(new IssueCouponCommand(PLANNER_IN_LATE_SUMMER, 7, CouponCode.of("SPRING20"), FAN_COUPON)))
                    .willReturn(FAN_COUPON_RESULT);

            mockMvc.perform(json(post(EVENT_PATH + "/coupons"), """
                            {"code": " spring20 ", "discount": {"method": "FIXED_AMOUNT", "value": 5000}, "usageLimit": 100,
                             "onePerPerson": true, "validity": %s}""".formatted(APRIL_JSON)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value("SPRING20"))
                    .andExpect(jsonPath("$.validity.startsAt").value("2026-04-01T00:00:00+09:00"));
        }

        @Test
        @DisplayName("안에 공백이 있거나 21자인 코드, 0회 전체 사용 가능 횟수는 400이다")
        void rejectsInvalidCode() throws Exception {
            for (String code : List.of("SPRING 20", "A".repeat(21))) {
                mockMvc.perform(json(post(EVENT_PATH + "/coupons"), """
                                {"code": "%s", "discount": {"method": "FIXED_AMOUNT", "value": 5000}, "usageLimit": 0,
                                 "onePerPerson": true, "validity": %s}""".formatted(code, APRIL_JSON)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.fieldErrors[?(@.field == 'code')].message").value("쿠폰 코드는 공백 없이 20자 이하로 입력해 주세요."))
                        .andExpect(jsonPath("$.fieldErrors[?(@.field == 'usageLimit')].message")
                                .value("전체 사용 가능 횟수는 1회 이상으로 입력해 주세요."));
            }

            verifyNoInteractions(issueCouponUseCase);
        }

        @Test
        @DisplayName("같은 코드의 쿠폰이 있으면 유스케이스가 막아 409다")
        void duplicateCode() throws Exception {
            willThrow(new RuleViolationException(PricingErrorCode.COUPON_CODE_ALREADY_TAKEN)).given(issueCouponUseCase).issue(any());

            mockMvc.perform(json(post(EVENT_PATH + "/coupons"), """
                            {"code": "SPRING20", "discount": {"method": "RATE", "value": 20}, "usageLimit": 100, "onePerPerson": false,
                             "validity": %s}""".formatted(APRIL_JSON)))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("COUPON_CODE_ALREADY_TAKEN", "이 행사에 같은 코드의 쿠폰이 이미 있습니다."));
        }

        @Test
        @DisplayName("수정은 코드 없이 설정을 넘긴다")
        void changesCoupon() throws Exception {
            given(changeCouponUseCase.change(new ChangeCouponCommand(PLANNER_IN_LATE_SUMMER, 7, 6, FAN_COUPON))).willReturn(FAN_COUPON_RESULT);

            mockMvc.perform(json(put(EVENT_PATH + "/coupons/6"), """
                            {"discount": {"method": "FIXED_AMOUNT", "value": 5000}, "usageLimit": 100, "onePerPerson": true,
                             "validity": %s}""".formatted(APRIL_JSON)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.couponId").value(6));
        }
    }

    @Nested
    @DisplayName("묶음권 상품")
    class BundleProducts {

        @Test
        @DisplayName("3일 통권(5/3 · 5/4 · 5/5, 스탠딩A, 7%)을 더하면 그대로 넘기고 201에 시작 가격을 담아 돌려준다")
        void addsBundleProduct() throws Exception {
            BundleProductTerms terms = new BundleProductTerms(BundleProductName.of("3일 통권"), Set.of(53L, 54L, 55L), Set.of(31L),
                    Percent.of(7));
            given(addBundleProductUseCase.add(new AddBundleProductCommand(PLANNER_IN_LATE_SUMMER, 7, terms)))
                    .willReturn(new BundleProductResult(71, "3일 통권", List.of(53L, 54L, 55L), List.of(31L), 7, 130_200L));

            mockMvc.perform(json(post(EVENT_PATH + "/bundle-products"), """
                            {"name": " 3일 통권 ", "dateIds": [53, 54, 55], "zoneIds": [31], "discountRate": 7}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.startingPrice").value(130_200));
        }

        @Test
        @DisplayName("빈 이름 · 100% 할인율 · 0 이하 id는 400이다")
        void rejectsInvalidFields() throws Exception {
            mockMvc.perform(json(post(EVENT_PATH + "/bundle-products"), """
                            {"name": "　", "dateIds": [53, 0], "zoneIds": [31], "discountRate": 100}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')].message").value("묶음권 이름을 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'discountRate')].message").value("할인율은 0%에서 99% 사이로 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors.length()").value(3));

            verifyNoInteractions(addBundleProductUseCase);
        }

        @Test
        @DisplayName("지정형 구역이 있으면 유스케이스가 막아 409다")
        void designatedZone() throws Exception {
            willThrow(new RuleViolationException(PricingErrorCode.BUNDLE_ZONE_NOT_QUANTITY)).given(addBundleProductUseCase).add(any());

            mockMvc.perform(json(post(EVENT_PATH + "/bundle-products"), """
                            {"name": "3일 통권", "dateIds": [53, 54, 55], "zoneIds": [33], "discountRate": 7}"""))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("BUNDLE_ZONE_NOT_QUANTITY", "지정형 구역에서는 묶음권을 팔 수 없습니다. 수량형 구역만 골라 주세요."));
        }
    }
}
