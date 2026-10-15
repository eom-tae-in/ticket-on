package com.ticketon.api.organizer;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.organizer.port.in.BusinessItems;
import com.ticketon.application.organizer.port.in.SubmitOrganizerApplicationCommand;
import com.ticketon.application.organizer.port.in.SubmitOrganizerApplicationResult;
import com.ticketon.application.organizer.port.in.SubmitOrganizerApplicationUseCase;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.BusinessStatus;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerType;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 주최자 신청서 제출 API — 보안 필터를 건 채로 운영진 영역인지, 요청이 Command로 바뀌는지, 입력 검증(400)과 규칙 위반·경합(409)을 본다.
 * 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = OrganizerApplicationController.class)
@Import(SecurityConfiguration.class)
@DisplayName("주최자 신청서 제출 API")
class OrganizerApplicationControllerTest {

    private static final String APPLICATIONS_PATH = "/api/organizer/applications";

    private static final AccountRef JUNGHOON = AccountRef.of(AccountKind.STAFF, 1);

    private static final Instant SUBMITTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final String TWELVE_MONTHS_BODY = """
            {
              "organizerType": "GROUP",
              "name": "극단 열두달",
              "contactPhoneNumber": "010-2345-6789",
              "email": "twelve-months@example.com",
              "websiteAndSnsLinks": ["https://www.instagram.com/twelve_months_theater", "https://blog.example/twelve-months"],
              "eventHistoryOrPlan": "2019년부터 소극장 정기공연을 해마다 두 번 올립니다.",
              "businessStatus": "NON_BUSINESS",
              "settlementAccount": {"bankName": "국민은행", "accountNumber": "123456-78-901234", "accountHolder": "박정훈"}
            }""";

    private static final String LATE_SUMMER_BODY = """
            {
              "organizerType": "GROUP",
              "name": "늦여름",
              "contactPhoneNumber": "010-9876-5432",
              "email": "hello@late-summer.example",
              "eventHistoryOrPlan": "인디 페스티벌 3회 개최.",
              "businessStatus": "BUSINESS",
              "businessInformation": {
                "tradeName": "늦여름 레코드",
                "representativeName": "최여름",
                "businessAddress": "서울특별시 마포구 와우산로 12",
                "businessPhoneNumber": "02-123-4567",
                "businessRegistrationNumber": "123-45-67890"
              },
              "settlementAccount": {"bankName": "신한은행", "accountNumber": "110-123-456789", "accountHolder": "늦여름 레코드"}
            }""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitOrganizerApplicationUseCase submitOrganizerApplicationUseCase;

    @Test
    @DisplayName("로그인한 운영진과 신청 항목을 Command로 넘기고, 201에 심사 중인 신청(한국 시각)만 돌려준다 — 연락처·계좌는 돌려주지 않는다")
    void convertsRequestToCommand() throws Exception {
        given(submitOrganizerApplicationUseCase.submit(any()))
                .willReturn(new SubmitOrganizerApplicationResult(3, "극단 열두달", SUBMITTED_AT));

        mockMvc.perform(submit(TWELVE_MONTHS_BODY).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"applicationId": 3, "organizerName": "극단 열두달", "submittedAt": "2026-10-15T10:00:00+09:00"}""",
                        JsonCompareMode.STRICT));

        assertThat(capturedCommand()).isEqualTo(new SubmitOrganizerApplicationCommand(JUNGHOON, OrganizerType.GROUP,
                OrganizerName.of("극단 열두달"), TelephoneNumber.of("010-2345-6789"),
                EmailAddress.of("twelve-months@example.com"),
                List.of(ExternalLink.of("https://www.instagram.com/twelve_months_theater"),
                        ExternalLink.of("https://blog.example/twelve-months")),
                "2019년부터 소극장 정기공연을 해마다 두 번 올립니다.", BusinessStatus.NON_BUSINESS, BusinessItems.none(),
                new SettlementAccount("국민은행", "123456-78-901234", "박정훈")));
    }

    @Test
    @DisplayName("사업자 정보는 적은 그대로 Command에 담는다 — 빠졌는지는 신청 규칙이 본다")
    void businessInformationAsWritten() throws Exception {
        given(submitOrganizerApplicationUseCase.submit(any()))
                .willReturn(new SubmitOrganizerApplicationResult(4, "늦여름", SUBMITTED_AT));

        mockMvc.perform(submit(LATE_SUMMER_BODY).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isCreated());

        SubmitOrganizerApplicationCommand command = capturedCommand();
        assertThat(command.businessStatus()).isEqualTo(BusinessStatus.BUSINESS);
        assertThat(command.businessItems()).isEqualTo(new BusinessItems("늦여름 레코드", "최여름", "서울특별시 마포구 와우산로 12",
                "02-123-4567", "123-45-67890"));
        assertThat(command.websiteAndSnsLinks()).isEmpty();
    }

    @Test
    @DisplayName("사업자인데 사업자 정보가 빠지면 409 BUSINESS_INFORMATION_MISSING이다")
    void missingBusinessInformationIsConflict() throws Exception {
        given(submitOrganizerApplicationUseCase.submit(any()))
                .willThrow(new RuleViolationException(OrganizerErrorCode.BUSINESS_INFORMATION_MISSING, "빠진 항목: 상호"));

        mockMvc.perform(submit(LATE_SUMMER_BODY).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isConflict())
                .andExpect(errorBody("BUSINESS_INFORMATION_MISSING",
                        "사업자는 상호 · 대표자 성명 · 사업장 주소 · 사업장 전화번호 · 사업자등록번호를 모두 입력해 주세요."));
    }

    @Test
    @DisplayName("심사 중인 신청이 있으면 409 APPLICATION_ALREADY_UNDER_REVIEW이고, 동시 신청에 져도 같은 문구다")
    void applicationUnderReviewIsConflict() throws Exception {
        given(submitOrganizerApplicationUseCase.submit(any()))
                .willThrow(new RuleViolationException(OrganizerErrorCode.APPLICATION_ALREADY_UNDER_REVIEW))
                .willThrow(new ConflictException(OrganizerErrorCode.APPLICATION_ALREADY_UNDER_REVIEW, "경합"));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(submit(TWELVE_MONTHS_BODY).with(loggedInAs(JUNGHOON)))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("APPLICATION_ALREADY_UNDER_REVIEW", "심사 중인 신청이 있습니다. 심사 결과를 기다려 주세요."));
        }
    }

    @Test
    @DisplayName("필수 칸이 비면 400이고 칸마다 문구가 붙는다 — 유스케이스를 부르지 않는다")
    void requiredItemsAreBadRequest() throws Exception {
        String emptyBody = """
                {
                  "name": " ",
                  "contactPhoneNumber": "",
                  "email": "",
                  "eventHistoryOrPlan": "",
                  "settlementAccount": {"bankName": "", "accountNumber": " ", "accountHolder": ""}
                }""";

        mockMvc.perform(submit(emptyBody).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(9)))
                .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("organizerType", "name",
                        "contactPhoneNumber", "email", "eventHistoryOrPlan", "businessStatus", "settlementAccount.bankName",
                        "settlementAccount.accountNumber", "settlementAccount.accountHolder")))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'organizerType')].message").value("주최자 구분을 골라 주세요."))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')].message").value("이름 또는 단체명을 입력해 주세요."))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'settlementAccount.accountNumber')].message")
                        .value("계좌번호를 입력해 주세요."));

        verify(submitOrganizerApplicationUseCase, never()).submit(any());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "name                           | 극단 열두달                                 | 이름 또는 단체명을 입력해 주세요.",
            "contactPhoneNumber             | 010-2345-6789                               | 담당자 연락처를 입력해 주세요.",
            "email                          | twelve-months@example.com                   | 이메일을 입력해 주세요.",
            "eventHistoryOrPlan             | 2019년부터 소극장 정기공연을 해마다 두 번 올립니다. | 과거 행사 이력 또는 계획을 입력해 주세요.",
            "settlementAccount.bankName     | 국민은행                                    | 은행을 입력해 주세요.",
            "settlementAccount.accountNumber | 123456-78-901234                           | 계좌번호를 입력해 주세요.",
            "settlementAccount.accountHolder | 박정훈                                     | 예금주를 입력해 주세요."})
    @DisplayName("전각 공백(U+3000)처럼 유니코드 공백만 넣은 칸도 빈 칸이라 400이다 — 도메인 값 객체와 같은 규칙이라 500이 되지 않는다")
    void unicodeWhitespaceOnlyIsBadRequest(String field, String writtenValue, String message) throws Exception {
        String body = TWELVE_MONTHS_BODY.replace("\"" + writtenValue + "\"", "\" \u3000\u2003\u3000 \"");

        mockMvc.perform(submit(body).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                .andExpect(jsonPath("$.fieldErrors[0].message").value(message));

        verify(submitOrganizerApplicationUseCase, never()).submit(any());
    }

    @Test
    @DisplayName("정산 계좌를 보내지 않으면 400 '정산 계좌를 입력해 주세요.'다")
    void settlementAccountIsRequired() throws Exception {
        String withoutAccount = TWELVE_MONTHS_BODY.replaceAll(",\\s*\"settlementAccount\": \\{[^}]*}", "");

        mockMvc.perform(submit(withoutAccount).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("settlementAccount"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("정산 계좌를 입력해 주세요."));

        verify(submitOrganizerApplicationUseCase, never()).submit(any());
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"javascript:alert(1)", "www.instagram.com/twelve", "ftp://files.example", " ", "\u3000"})
    @DisplayName("http(s) 주소가 아닌 링크는 400이고 몇 번째 링크인지 알려 준다")
    void invalidLinkIsBadRequest(String link) throws Exception {
        String body = TWELVE_MONTHS_BODY.replace("\"https://blog.example/twelve-months\"", "\"" + link + "\"");

        mockMvc.perform(submit(body).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("websiteAndSnsLinks[1]"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("http:// 또는 https://로 시작하는 주소를 입력해 주세요."));

        verify(submitOrganizerApplicationUseCase, never()).submit(any());
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"123-45-6789", "123-45-678901", "abc-de-fghij"})
    @DisplayName("사업자등록번호가 숫자 10자리가 아니면 400이다")
    void invalidRegistrationNumberIsBadRequest(String registrationNumber) throws Exception {
        String body = LATE_SUMMER_BODY.replace("123-45-67890", registrationNumber);

        mockMvc.perform(submit(body).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("businessInformation.businessRegistrationNumber"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("사업자등록번호 숫자 10자리를 입력해 주세요."));

        verify(submitOrganizerApplicationUseCase, never()).submit(any());
    }

    @Test
    @DisplayName("이메일 형식이 틀리면 400이다")
    void invalidEmailIsBadRequest() throws Exception {
        String body = TWELVE_MONTHS_BODY.replace("twelve-months@example.com", "twelve-months");

        mockMvc.perform(submit(body).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("이메일 주소를 확인해 주세요."));
    }

    @Test
    @DisplayName("주최자 구분에 없는 값을 보내면 400이다")
    void unknownOrganizerTypeIsBadRequest() throws Exception {
        String body = TWELVE_MONTHS_BODY.replace("\"GROUP\"", "\"COMPANY\"");

        mockMvc.perform(submit(body).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(submitOrganizerApplicationUseCase, never()).submit(any());
    }

    @Test
    @DisplayName("로그인하지 않았으면 401이다")
    void requiresLogin() throws Exception {
        mockMvc.perform(submit(TWELVE_MONTHS_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"AUDIENCE", "ADMIN", "CHECKIN"})
    @DisplayName("운영진이 아닌 계정으로는 403이다")
    void otherAccountKindIsDenied(AccountKind kind) throws Exception {
        mockMvc.perform(submit(TWELVE_MONTHS_BODY).with(loggedInAs(kind, 7)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));

        verify(submitOrganizerApplicationUseCase, never()).submit(any());
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 403이다")
    void requiresCsrfToken() throws Exception {
        mockMvc.perform(post(APPLICATIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TWELVE_MONTHS_BODY)
                        .with(loggedInAs(JUNGHOON)))
                .andExpect(status().isForbidden());

        verify(submitOrganizerApplicationUseCase, never()).submit(any());
    }

    @Test
    @DisplayName("요청을 글로 남길 때 연락처·사업자 정보·정산 계좌는 싣지 않는다")
    void requestTextHidesSensitiveValues() {
        SubmitOrganizerApplicationRequest request = new SubmitOrganizerApplicationRequest(OrganizerTypeCode.GROUP, "늦여름",
                "010-9876-5432", "hello@late-summer.example", List.of(), "인디 페스티벌 3회 개최.", BusinessStatusCode.BUSINESS,
                new SubmitOrganizerApplicationRequest.BusinessInformationRequest("늦여름 레코드", "최여름", "와우산로 12",
                        "02-123-4567", "123-45-67890"),
                new SubmitOrganizerApplicationRequest.SettlementAccountRequest("신한은행", "110-123-456789", "늦여름 레코드"));

        assertThat(request.toString()).contains("늦여름")
                .doesNotContain("010-9876-5432", "110-123-456789", "123-45-67890", "와우산로", "02-123-4567");
    }

    private SubmitOrganizerApplicationCommand capturedCommand() {
        ArgumentCaptor<SubmitOrganizerApplicationCommand> command =
                ArgumentCaptor.forClass(SubmitOrganizerApplicationCommand.class);
        verify(submitOrganizerApplicationUseCase).submit(command.capture());
        return command.getValue();
    }

    private MockHttpServletRequestBuilder submit(String body) {
        return post(APPLICATIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf());
    }
}
