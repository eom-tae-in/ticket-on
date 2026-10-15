package com.ticketon.api.admin;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.account.AdminLoginController;
import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.LogInAdminUseCase;
import com.ticketon.application.admin.port.in.IssueAdminAccountUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 관리자 로그인·로그아웃과 관리자 계정 발급의 요청·응답 모양이 명세에 그대로
 * 실리는지 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = {AdminLoginController.class, AdminAccountController.class})
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("관리자 로그인·관리자 계정 발급 API 명세")
class AdminApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LogInAdminUseCase logInAdminUseCase;

    @MockitoBean
    private IssueAdminAccountUseCase issueAdminAccountUseCase;

    @Test
    @DisplayName("로그인은 200에 LogInAdminResponse(returnPath·grade)를 돌려주고, 요청은 email·password가 필수이고 returnPath는 고를 수 있다")
    void logInOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/admin/login'].post.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/LogInAdminResponse"))
                .andExpect(jsonPath("$.components.schemas.LogInAdminRequest.required", containsInAnyOrder("email", "password")))
                .andExpect(jsonPath("$.components.schemas.LogInAdminRequest.properties.returnPath").exists())
                .andExpect(jsonPath("$.components.schemas.LogInAdminResponse.required",
                        containsInAnyOrder("returnPath", "grade")))
                .andExpect(jsonPath("$.components.schemas.LogInAdminResponse.properties.grade.enum",
                        containsInAnyOrder("ADMIN", "SUPER_ADMIN")));
    }

    @Test
    @DisplayName("로그아웃은 본문 없이 204로 끝난다")
    void logOutOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/admin/logout'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/admin/logout'].post.requestBody").doesNotExist());
    }

    @Test
    @DisplayName("관리자 계정 발급은 최고 관리자 전용 경로에서 email·password·grade를 받아 201에 IssueAdminAccountResponse를 돌려준다 — 로그인 주체는 파라미터로 싣지 않는다")
    void issueOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/admin/super/admin-accounts'].post.responses['201']"
                        + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/IssueAdminAccountResponse"))
                .andExpect(jsonPath("$.paths['/api/admin/super/admin-accounts'].post.parameters").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.IssueAdminAccountRequest.required",
                        containsInAnyOrder("email", "password", "grade")))
                .andExpect(jsonPath("$.components.schemas.IssueAdminAccountResponse.required",
                        containsInAnyOrder("email", "grade")));
    }
}
