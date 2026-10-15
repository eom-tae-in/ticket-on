package com.ticketon.api.account;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.ChangeStaffNicknameUseCase;
import com.ticketon.application.account.port.in.LogInStaffUseCase;
import com.ticketon.application.account.port.in.WithdrawStaffAccountUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 운영진 로그인·로그아웃·닉네임 변경의 요청·응답 모양이 명세에 그대로 실리는지
 * 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = {StaffLoginController.class, StaffAccountController.class})
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("운영진 로그인·로그아웃·닉네임 변경 API 명세")
class StaffLoginApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LogInStaffUseCase logInStaffUseCase;

    @MockitoBean
    private ChangeStaffNicknameUseCase changeStaffNicknameUseCase;

    @MockitoBean
    private WithdrawStaffAccountUseCase withdrawStaffAccountUseCase;

    @Test
    @DisplayName("운영진 로그인 탈퇴는 경로 · 본문 없이 204다")
    void withdrawalOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/organizer/account/withdrawal'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/organizer/account/withdrawal'].post.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/organizer/account/withdrawal'].post.requestBody").doesNotExist());
    }

    @Test
    @DisplayName("로그인은 200에 LogInStaffResponse(returnPath)를 돌려주고, 요청은 email·password가 필수이고 returnPath는 고를 수 있다")
    void logInOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/staff/login'].post.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/LogInStaffResponse"))
                .andExpect(jsonPath("$.components.schemas.LogInStaffRequest.required", containsInAnyOrder("email", "password")))
                .andExpect(jsonPath("$.components.schemas.LogInStaffRequest.properties.returnPath").exists())
                .andExpect(jsonPath("$.components.schemas.LogInStaffResponse.required", containsInAnyOrder("returnPath")));
    }

    @Test
    @DisplayName("로그아웃은 본문 없이 204로 끝난다")
    void logOutOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/staff/logout'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/staff/logout'].post.requestBody").doesNotExist());
    }

    @Test
    @DisplayName("닉네임 변경은 PUT으로 nickname 하나를 받아 200에 StaffNicknameResponse를 돌려준다 — 로그인 주체는 파라미터로 싣지 않는다")
    void changeNicknameOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/organizer/account/nickname'].put.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/StaffNicknameResponse"))
                .andExpect(jsonPath("$.paths['/api/organizer/account/nickname'].put.parameters").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ChangeStaffNicknameRequest.required", containsInAnyOrder("nickname")))
                .andExpect(jsonPath("$.components.schemas.StaffNicknameResponse.required", containsInAnyOrder("nickname")));
    }
}
