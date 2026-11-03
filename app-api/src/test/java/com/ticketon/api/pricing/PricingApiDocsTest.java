package com.ticketon.api.pricing;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.pricing.port.in.AddBundleProductUseCase;
import com.ticketon.application.pricing.port.in.AddEarlyBirdUseCase;
import com.ticketon.application.pricing.port.in.ChangeBundleProductUseCase;
import com.ticketon.application.pricing.port.in.ChangeCouponUseCase;
import com.ticketon.application.pricing.port.in.ChangeEarlyBirdUseCase;
import com.ticketon.application.pricing.port.in.GetPricingStepUseCase;
import com.ticketon.application.pricing.port.in.IssueCouponUseCase;
import com.ticketon.application.pricing.port.in.RemoveBundleProductUseCase;
import com.ticketon.application.pricing.port.in.RemoveCouponUseCase;
import com.ticketon.application.pricing.port.in.RemoveEarlyBirdUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 위저드 4단계 API의 요청 · 응답 모양이 명세에 그대로 실리는지 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = {EventPricingController.class, EarlyBirdController.class, CouponController.class, BundleProductController.class})
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("위저드 4단계 API 명세")
class PricingApiDocsTest {

    private static final String EVENT = "$.paths['/api/organizer/organizers/{organizerId}/events/{eventId}";
    private static final String SCHEMAS = "$.components.schemas";

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

    @Test
    @DisplayName("4단계 보기 200, 조기예매 · 쿠폰 · 묶음권은 추가 201 · 수정 200 · 삭제 204")
    void operations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(EVENT + "/pricing'].get.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/PricingStepResponse"))
                .andExpect(jsonPath(EVENT + "/early-birds'].post.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EarlyBirdRequest"))
                .andExpect(jsonPath(EVENT + "/early-birds'].post.responses['201'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EarlyBirdResponse"))
                .andExpect(jsonPath(EVENT + "/early-birds/{earlyBirdId}'].put.parameters[*].name",
                        containsInAnyOrder("organizerId", "eventId", "earlyBirdId")))
                .andExpect(jsonPath(EVENT + "/early-birds/{earlyBirdId}'].delete.responses['204']").exists())
                .andExpect(jsonPath(EVENT + "/coupons'].post.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/IssueCouponRequest"))
                .andExpect(jsonPath(EVENT + "/coupons'].post.responses['201'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/CouponResponse"))
                .andExpect(jsonPath(EVENT + "/coupons/{couponId}'].put.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ChangeCouponRequest"))
                .andExpect(jsonPath(EVENT + "/coupons/{couponId}'].delete.responses['204']").exists())
                .andExpect(jsonPath(EVENT + "/bundle-products'].post.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/BundleProductRequest"))
                .andExpect(jsonPath(EVENT + "/bundle-products'].post.responses['201'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/BundleProductResponse"))
                .andExpect(jsonPath(EVENT + "/bundle-products/{bundleProductId}'].put.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/BundleProductResponse"))
                .andExpect(jsonPath(EVENT + "/bundle-products/{bundleProductId}'].delete.responses['204']").exists());
    }

    @Test
    @DisplayName("요청의 필수 칸 — 조기예매는 수량 밖의 칸, 쿠폰 발급은 코드를 포함한 칸 모두, 묶음권은 칸 모두")
    void requiredRequestFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(SCHEMAS + ".EarlyBirdRequest.required", containsInAnyOrder("discount", "period", "singleSessionExcluded",
                        "excludedZoneIds", "excludedBundleProductIds")))
                .andExpect(jsonPath(SCHEMAS + ".IssueCouponRequest.required", containsInAnyOrder("code", "discount", "usageLimit",
                        "onePerPerson", "validity")))
                .andExpect(jsonPath(SCHEMAS + ".ChangeCouponRequest.required", containsInAnyOrder("discount", "usageLimit", "onePerPerson",
                        "validity")))
                .andExpect(jsonPath(SCHEMAS + ".BundleProductRequest.required", containsInAnyOrder("name", "dateIds", "zoneIds",
                        "discountRate")))
                .andExpect(jsonPath(SCHEMAS + ".DiscountRequest.required", containsInAnyOrder("method", "value")))
                .andExpect(jsonPath(SCHEMAS + ".DiscountRequest.properties.method.enum", containsInAnyOrder("FIXED_AMOUNT", "RATE")))
                .andExpect(jsonPath(SCHEMAS + ".DiscountPeriodRequest.required", containsInAnyOrder("startsAt", "endsAt")));
    }

    @Test
    @DisplayName("응답의 칸 — 조기예매 수량 · 묶음권 시작 가격 · 회차 가격 · 회차 시작 시각만 null일 수 있다")
    void responseFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(SCHEMAS + ".EarlyBirdResponse.properties.quantity.type", containsInAnyOrder("integer", "null")))
                .andExpect(jsonPath(SCHEMAS + ".EarlyBirdResponse.properties.status.enum",
                        containsInAnyOrder("NOT_STARTED", "OPEN", "SOLD_OUT", "ENDED")))
                .andExpect(jsonPath(SCHEMAS + ".EarlyBirdResponse.required", containsInAnyOrder("earlyBirdId", "discount", "period",
                        "claimedCount", "status", "singleSessionExcluded", "excludedZoneIds", "excludedBundleProductIds",
                        "overlappingEarlyBirdIds")))
                .andExpect(jsonPath(SCHEMAS + ".BundleProductResponse.properties.startingPrice.type", containsInAnyOrder("integer", "null")))
                .andExpect(jsonPath(SCHEMAS + ".ZoneSessionPriceResponse.properties.price.type", containsInAnyOrder("integer", "null")))
                .andExpect(jsonPath(SCHEMAS + ".SessionPricesResponse.properties.startTime.type", containsInAnyOrder("string", "null")))
                .andExpect(jsonPath(SCHEMAS + ".PricingStepResponse.required", containsInAnyOrder("eventId", "zones", "sessions", "earlyBirds",
                        "coupons", "bundleProducts", "bundleDiscountDefaults")))
                .andExpect(jsonPath(SCHEMAS + ".CouponResponse.required", containsInAnyOrder("couponId", "code", "discount", "usageLimit",
                        "usedCount", "onePerPerson", "validity")));
    }
}
