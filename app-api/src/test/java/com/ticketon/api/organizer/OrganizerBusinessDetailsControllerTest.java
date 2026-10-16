package com.ticketon.api.organizer;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.GetOrganizerBusinessDetailsQuery;
import com.ticketon.application.organizer.port.in.GetOrganizerBusinessDetailsUseCase;
import com.ticketon.application.organizer.port.in.OrganizerBusinessDetailsResult;
import com.ticketon.application.organizer.port.in.RequestOrganizerChangeCommand;
import com.ticketon.application.organizer.port.in.RequestOrganizerChangeResult;
import com.ticketon.application.organizer.port.in.RequestOrganizerChangeUseCase;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.BusinessInformation;
import com.ticketon.domain.organizer.BusinessRegistrationNumber;
import com.ticketon.domain.organizer.BusinessStatus;
import com.ticketon.domain.organizer.OrganizerChangeRequestKind;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.RequestedOrganizerChange;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 사업자 정보 · 정산 계좌 API — 경로의 주최자 id와 로그인한 운영진을 그대로 유스케이스에 넘기는지, 입력 검증(400)과 응답 모양, 유스케이스가
 * 거절할 때의 상태 코드를 본다. 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = OrganizerBusinessDetailsController.class)
@Import(SecurityConfiguration.class)
@DisplayName("사업자 정보 · 정산 계좌 API")
class OrganizerBusinessDetailsControllerTest {

    private static final String BASE_PATH = "/api/organizer/organizers/13";

    private static final String ACCOUNT_PATH = BASE_PATH + "/settlement-account-change-requests";

    private static final String BUSINESS_PATH = BASE_PATH + "/business-information-change-requests";

    private static final AccountRef YEOREUM = AccountRef.of(AccountKind.STAFF, 3);

    private static final Instant REQUESTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final String ACCOUNT_BODY = """
            {"bankName": " 우리은행 ", "accountNumber": "1002-345-678901", "accountHolder": "최여름"}""";

    private static final String BUSINESS_BODY = """
            {"tradeName": "늦여름 레코드", "representativeName": "최여름", "businessAddress": "서울특별시 마포구 독막로 7",
             "businessPhoneNumber": "02-765-4321", "businessRegistrationNumber": "123-45-67890"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetOrganizerBusinessDetailsUseCase getBusinessDetailsUseCase;

    @MockitoBean
    private RequestOrganizerChangeUseCase requestOrganizerChangeUseCase;

    @Test
    @DisplayName("보기는 지금 값(정산 계좌번호는 가린 값)과 확인 중인 요청을 돌려준다 — 없는 요청은 null")
    void showsBusinessDetails() throws Exception {
        given(getBusinessDetailsUseCase.get(new GetOrganizerBusinessDetailsQuery(new ActingStaff(YEOREUM, 13))))
                .willReturn(new OrganizerBusinessDetailsResult(13, BusinessStatus.BUSINESS,
                        new OrganizerBusinessDetailsResult.BusinessInformation("늦여름 레코드", "최여름", "서울특별시 마포구 와우산로 12",
                                "02-123-4567", "1234567890"),
                        new OrganizerBusinessDetailsResult.MaskedSettlementAccount("신한은행", "***-***-**6789", "늦여름 레코드"),
                        new OrganizerBusinessDetailsResult.PendingSettlementAccountChange(8, REQUESTED_AT,
                                new OrganizerBusinessDetailsResult.MaskedSettlementAccount("우리은행", "****-***-**8901", "최여름")),
                        null));

        mockMvc.perform(get(BASE_PATH + "/business-details").with(loggedInAs(YEOREUM)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"organizerId": 13,
                         "businessStatus": "BUSINESS",
                         "businessInformation": {"tradeName": "늦여름 레코드", "representativeName": "최여름",
                                                 "businessAddress": "서울특별시 마포구 와우산로 12",
                                                 "businessPhoneNumber": "02-123-4567",
                                                 "businessRegistrationNumber": "1234567890"},
                         "settlementAccount": {"bankName": "신한은행", "maskedAccountNumber": "***-***-**6789",
                                               "accountHolder": "늦여름 레코드"},
                         "pendingSettlementAccountChange": {"changeRequestId": 8,
                                                            "requestedAt": "2026-10-15T10:00:00+09:00",
                                                            "requested": {"bankName": "우리은행",
                                                                          "maskedAccountNumber": "****-***-**8901",
                                                                          "accountHolder": "최여름"}},
                         "pendingBusinessInformationChange": null}""", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("정산 계좌 변경을 요청하면 경로의 주최자 · 로그인한 운영진 · 앞뒤 공백을 걷은 새 계좌를 넘기고 201에 요청을 돌려준다")
    void requestsSettlementAccountChange() throws Exception {
        given(requestOrganizerChangeUseCase.request(new RequestOrganizerChangeCommand(new ActingStaff(YEOREUM, 13),
                new RequestedOrganizerChange.NewSettlementAccount(new SettlementAccount("우리은행", "1002-345-678901", "최여름")))))
                .willReturn(new RequestOrganizerChangeResult(8, OrganizerChangeRequestKind.SETTLEMENT_ACCOUNT, REQUESTED_AT));

        mockMvc.perform(postAs(YEOREUM, ACCOUNT_PATH, ACCOUNT_BODY))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"changeRequestId": 8, "kind": "SETTLEMENT_ACCOUNT", "requestedAt": "2026-10-15T10:00:00+09:00"}""",
                        JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("사업자 정보 변경을 요청하면 사업자등록번호의 하이픈을 걷은 새 사업자 정보를 넘기고 201에 요청을 돌려준다")
    void requestsBusinessInformationChange() throws Exception {
        given(requestOrganizerChangeUseCase.request(new RequestOrganizerChangeCommand(new ActingStaff(YEOREUM, 13),
                new RequestedOrganizerChange.NewBusinessInformation(new BusinessInformation("늦여름 레코드", "최여름",
                        "서울특별시 마포구 독막로 7", TelephoneNumber.of("02-765-4321"), BusinessRegistrationNumber.of("1234567890"))))))
                .willReturn(new RequestOrganizerChangeResult(9, OrganizerChangeRequestKind.BUSINESS_INFORMATION, REQUESTED_AT));

        mockMvc.perform(postAs(YEOREUM, BUSINESS_PATH, BUSINESS_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.changeRequestId").value(9))
                .andExpect(jsonPath("$.kind").value("BUSINESS_INFORMATION"));
    }

    @Test
    @DisplayName("정산 계좌의 세 칸이 없거나 공백뿐이면(전각 공백 포함) 400이고 칸마다 까닭을 담는다 — 유스케이스를 부르지 않는다")
    void rejectsBlankAccountFields() throws Exception {
        mockMvc.perform(postAs(YEOREUM, ACCOUNT_PATH, """
                        {"bankName": "　", "accountHolder": " "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(3)))
                .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("bankName", "accountNumber", "accountHolder")))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'accountNumber')].message").value("계좌번호를 입력해 주세요."));

        verifyNoInteractions(requestOrganizerChangeUseCase);
    }

    @ParameterizedTest(name = "{0} = \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "tradeName                  | '　'           | 상호를 입력해 주세요.",
            "representativeName         | ''            | 대표자 성명을 입력해 주세요.",
            "businessAddress            | ' '           | 사업장 주소를 입력해 주세요.",
            "businessPhoneNumber        | ''            | 사업장 전화번호를 입력해 주세요.",
            "businessRegistrationNumber | ''            | 사업자등록번호를 입력해 주세요.",
            "businessRegistrationNumber | 123-45-6789   | 사업자등록번호 숫자 10자리를 입력해 주세요."})
    @DisplayName("사업자 정보 칸이 비었거나 사업자등록번호가 숫자 10자리가 아니면 400이고 그 칸에 까닭을 담는다")
    void rejectsInvalidBusinessField(String field, String value, String message) throws Exception {
        String body = BUSINESS_BODY.replaceFirst("\"" + field + "\": \"[^\"]*\"", "\"" + field + "\": \"" + value + "\"");

        mockMvc.perform(postAs(YEOREUM, BUSINESS_PATH, body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                .andExpect(jsonPath("$.fieldErrors[0].message").value(message));

        verifyNoInteractions(requestOrganizerChangeUseCase);
    }

    @Test
    @DisplayName("운영자면 403 OWNER_ONLY_ACTION — 보기도 요청도")
    void operatorIsDenied() throws Exception {
        AccountRef dohyun = AccountRef.of(AccountKind.STAFF, 4);
        willThrow(new PermissionDeniedException(OrganizerErrorCode.OWNER_ONLY_ACTION))
                .given(getBusinessDetailsUseCase).get(any());
        willThrow(new PermissionDeniedException(OrganizerErrorCode.OWNER_ONLY_ACTION))
                .given(requestOrganizerChangeUseCase).request(any());

        mockMvc.perform(get(BASE_PATH + "/business-details").with(loggedInAs(dohyun)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("OWNER_ONLY_ACTION", "소유자만 할 수 있는 일입니다."));
        mockMvc.perform(postAs(dohyun, ACCOUNT_PATH, ACCOUNT_BODY))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("OWNER_ONLY_ACTION", "소유자만 할 수 있는 일입니다."));
    }

    @Test
    @DisplayName("요청 규칙에 걸리면 409 — 확인 중인 요청이 있음, 비사업자의 사업자 정보 변경, 지금 값과 같음")
    void ruleViolationsAreConflicts() throws Exception {
        willThrow(new RuleViolationException(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW))
                .given(requestOrganizerChangeUseCase).request(any());
        mockMvc.perform(postAs(YEOREUM, ACCOUNT_PATH, ACCOUNT_BODY))
                .andExpect(status().isConflict())
                .andExpect(errorBody("ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW",
                        "관리자가 확인 중인 변경 요청이 있습니다. 확인 결과를 기다려 주세요."));

        willThrow(new RuleViolationException(OrganizerErrorCode.NON_BUSINESS_ORGANIZER_HAS_NO_BUSINESS_INFORMATION))
                .given(requestOrganizerChangeUseCase).request(any());
        mockMvc.perform(postAs(YEOREUM, BUSINESS_PATH, BUSINESS_BODY))
                .andExpect(status().isConflict())
                .andExpect(errorBody("NON_BUSINESS_ORGANIZER_HAS_NO_BUSINESS_INFORMATION", "비사업자 주최자는 사업자 정보가 없습니다."));

        willThrow(new RuleViolationException(OrganizerErrorCode.ORGANIZER_CHANGE_SAME_AS_CURRENT))
                .given(requestOrganizerChangeUseCase).request(any());
        mockMvc.perform(postAs(YEOREUM, ACCOUNT_PATH, ACCOUNT_BODY))
                .andExpect(status().isConflict())
                .andExpect(errorBody("ORGANIZER_CHANGE_SAME_AS_CURRENT", "지금과 같은 값입니다. 바꿀 값을 입력해 주세요."));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AccountKind.class, names = "STAFF", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("운영진이 아닌 계정은 주최 관리 영역이라 403이고, 로그인하지 않으면 401이다")
    void onlyStaffArea(AccountKind kind) throws Exception {
        mockMvc.perform(get(BASE_PATH + "/business-details").with(loggedInAs(AccountRef.of(kind, 1))))
                .andExpect(status().isForbidden());
        mockMvc.perform(postAs(AccountRef.of(kind, 1), ACCOUNT_PATH, ACCOUNT_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE_PATH + "/business-details"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(ACCOUNT_PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ACCOUNT_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(getBusinessDetailsUseCase, requestOrganizerChangeUseCase);
    }

    private MockHttpServletRequestBuilder postAs(AccountRef account, String path, String body) {
        return post(path).with(csrf()).with(loggedInAs(account)).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
