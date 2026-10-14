package com.ticketon.api.account;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.SignUpAudienceUseCase;
import com.ticketon.application.account.port.in.VerifyAudienceEmailUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 가입·인증 API의 요청·응답 모양이 명세에 그대로 실리는지 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = AudienceSignUpController.class)
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("관객 가입·이메일 인증 API 명세")
class AudienceSignUpApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignUpAudienceUseCase signUpAudienceUseCase;

    @MockitoBean
    private VerifyAudienceEmailUseCase verifyAudienceEmailUseCase;

    @Test
    @DisplayName("가입은 201에 SignUpAudienceResponse(email)를 돌려주고, 요청 칸 아홉 개가 모두 필수다")
    void signUpOperation() throws Exception {
        ResultActions apiDocs = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());

        apiDocs.andExpect(jsonPath("$.paths['/api/auth/audience/signup'].post.responses['201']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/SignUpAudienceResponse"))
                .andExpect(jsonPath("$.components.schemas.SignUpAudienceRequest.required", containsInAnyOrder(
                        "name", "birthDate", "email", "phoneNumber", "password", "termsOfServiceAgreed",
                        "personalInformationCollectionAgreed", "minimumAgeConfirmed", "marketingAgreed")))
                .andExpect(jsonPath("$.components.schemas.SignUpAudienceResponse.required", containsInAnyOrder("email")));
    }

    @Test
    @DisplayName("이메일 인증 확인은 token 하나를 받고 204로 끝난다")
    void emailVerificationOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/audience/email-verification'].post.responses['204']").exists())
                .andExpect(jsonPath("$.components.schemas.VerifyAudienceEmailRequest.required", containsInAnyOrder("token")));
    }
}
