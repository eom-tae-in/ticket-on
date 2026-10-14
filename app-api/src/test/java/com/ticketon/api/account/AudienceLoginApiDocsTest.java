package com.ticketon.api.account;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.GetMyAudienceAccountUseCase;
import com.ticketon.application.account.port.in.LogInAudienceUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 관객 로그인·로그아웃·내 계정 조회의 요청·응답 모양이 명세에 그대로 실리는지 본다.
 * DB 없이 돈다.
 */
@WebMvcTest(controllers = {AudienceLoginController.class, AudienceAccountController.class})
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("관객 로그인·로그아웃·내 계정 조회 API 명세")
class AudienceLoginApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LogInAudienceUseCase logInAudienceUseCase;

    @MockitoBean
    private GetMyAudienceAccountUseCase getMyAudienceAccountUseCase;

    @Test
    @DisplayName("로그인은 200에 LogInAudienceResponse(returnPath)를 돌려주고, 요청은 email·password가 필수이고 returnPath는 고를 수 있다")
    void logInOperation() throws Exception {
        ResultActions apiDocs = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());

        apiDocs.andExpect(jsonPath("$.paths['/api/auth/audience/login'].post.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/LogInAudienceResponse"))
                .andExpect(jsonPath("$.components.schemas.LogInAudienceRequest.required",
                        containsInAnyOrder("email", "password")))
                .andExpect(jsonPath("$.components.schemas.LogInAudienceRequest.properties.returnPath").exists())
                .andExpect(jsonPath("$.components.schemas.LogInAudienceResponse.required", containsInAnyOrder("returnPath")));
    }

    @Test
    @DisplayName("로그아웃은 본문 없이 204로 끝난다")
    void logOutOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/audience/logout'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/audience/logout'].post.requestBody").doesNotExist());
    }

    @Test
    @DisplayName("내 계정 조회는 200에 MyAudienceAccountResponse를 돌려주고, 칸 다섯 개가 모두 필수다 — 로그인 주체는 파라미터로 싣지 않는다")
    void myAccountOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/audience/account'].get.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/MyAudienceAccountResponse"))
                .andExpect(jsonPath("$.paths['/api/audience/account'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.MyAudienceAccountResponse.required", containsInAnyOrder(
                        "name", "email", "phoneNumber", "suspended", "linkedOAuthProviders")));
    }
}
