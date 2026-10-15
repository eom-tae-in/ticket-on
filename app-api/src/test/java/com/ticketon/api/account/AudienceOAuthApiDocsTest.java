package com.ticketon.api.account;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.SignUpWithOAuthUseCase;
import com.ticketon.application.account.port.in.UnlinkOAuthUseCase;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 카카오·구글 로그인 결과·가입·연동의 요청·응답 모양이 명세에 그대로 실리는지 본다.
 * DB 없이 돈다.
 */
@WebMvcTest(controllers = {AudienceOAuthController.class, AudienceOAuthLinkController.class})
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("카카오·구글 로그인·연동 API 명세")
class AudienceOAuthApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignUpWithOAuthUseCase signUpWithOAuthUseCase;

    @MockitoBean
    private UnlinkOAuthUseCase unlinkOAuthUseCase;

    @Test
    @DisplayName("결과는 200에 OAuthLoginResultResponse — outcome·returnPath는 필수, 제공자·가입 대기·오류는 경우에 따라 있다")
    void resultOperation() throws Exception {
        ResultActions apiDocs = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());

        apiDocs.andExpect(jsonPath("$.paths['/api/auth/audience/oauth2/result'].get.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/OAuthLoginResultResponse"))
                .andExpect(jsonPath("$.components.schemas.OAuthLoginResultResponse.required",
                        containsInAnyOrder("outcome", "returnPath")))
                .andExpect(jsonPath("$.components.schemas.OAuthLoginResultResponse.properties.outcome.enum",
                        contains("LOGGED_IN", "SIGNUP_REQUIRED", "LINKED", "FAILED")))
                .andExpect(jsonPath("$.components.schemas.OAuthLoginResultResponse.properties.provider.enum",
                        contains("KAKAO", "GOOGLE")))
                .andExpect(jsonPath("$.components.schemas.OAuthPendingSignUpResponse.required",
                        containsInAnyOrder("emailRequired")));
    }

    @Test
    @DisplayName("가입은 201에 OAuthSignUpResponse — 요청은 이름·생년월일·전화번호와 동의 넷이 필수이고 이메일은 고를 수 있으며 비밀번호 칸이 없다")
    void signUpOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/audience/oauth2/signup'].post.responses['201']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/OAuthSignUpResponse"))
                .andExpect(jsonPath("$.components.schemas.OAuthSignUpRequest.required", containsInAnyOrder("name",
                        "birthDate", "phoneNumber", "termsOfServiceAgreed", "personalInformationCollectionAgreed",
                        "minimumAgeConfirmed", "marketingAgreed")))
                .andExpect(jsonPath("$.components.schemas.OAuthSignUpRequest.properties.email").exists())
                .andExpect(jsonPath("$.components.schemas.OAuthSignUpRequest.properties.password").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.OAuthSignUpResponse.required",
                        containsInAnyOrder("loggedIn", "email", "returnPath")));
    }

    @Test
    @DisplayName("연결 시작은 200에 OAuthLinkStartResponse(authorizationPath), 해제는 본문 없이 204 — 제공자는 KAKAO·GOOGLE 중 하나이고 로그인 주체는 파라미터가 아니다")
    void linkOperations() throws Exception {
        String linkPath = "$.paths['/api/audience/account/oauth-links/{provider}']";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(linkPath + ".post.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/OAuthLinkStartResponse"))
                .andExpect(jsonPath("$.components.schemas.OAuthLinkStartResponse.required",
                        containsInAnyOrder("authorizationPath")))
                .andExpect(jsonPath(linkPath + ".delete.responses['204']").exists())
                .andExpect(jsonPath(linkPath + ".delete.parameters[*].name", contains("provider")))
                .andExpect(jsonPath(linkPath + ".delete.parameters[0].schema.enum", contains("KAKAO", "GOOGLE")))
                .andExpect(jsonPath(linkPath + ".post.parameters[*].name", not(hasItem("account"))));
    }
}
