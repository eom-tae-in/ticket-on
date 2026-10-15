package com.ticketon.api.account;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.ChangeStaffProfileUseCase;
import com.ticketon.application.account.port.in.GetMyStaffProfileUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 운영진 로그인·로그아웃·내 프로필·탈퇴의 요청·응답 모양이 명세에 그대로 실리는지
 * 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = {StaffLoginController.class, StaffAccountController.class})
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("운영진 로그인·로그아웃·내 프로필·탈퇴 API 명세")
class StaffLoginApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LogInStaffUseCase logInStaffUseCase;

    @MockitoBean
    private GetMyStaffProfileUseCase getMyStaffProfileUseCase;

    @MockitoBean
    private ChangeStaffProfileUseCase changeStaffProfileUseCase;

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
    @DisplayName("내 프로필 조회는 GET으로 200에 MyStaffProfileResponse를 돌려준다 — 로그인 주체는 파라미터로 싣지 않는다")
    void getMyProfileOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/organizer/account/profile'].get.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/MyStaffProfileResponse"))
                .andExpect(jsonPath("$.paths['/api/organizer/account/profile'].get.parameters").doesNotExist());
    }

    @Test
    @DisplayName("내 프로필 편집은 PUT으로 nickname(필수) · introduction(선택)을 받아 200에 MyStaffProfileResponse를 돌려준다")
    void changeProfileOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/organizer/account/profile'].put.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/MyStaffProfileResponse"))
                .andExpect(jsonPath("$.paths['/api/organizer/account/profile'].put.parameters").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ChangeStaffProfileRequest.required", containsInAnyOrder("nickname")))
                .andExpect(jsonPath("$.components.schemas.ChangeStaffProfileRequest.properties.introduction").exists());
    }

    @Test
    @DisplayName("내 프로필 응답은 staffAccountId · nickname · avatar가 필수이고 introduction은 비어 있을 수 있다 — 이름 · 이메일 칸은 없다")
    void myProfileResponseSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.MyStaffProfileResponse.required",
                        containsInAnyOrder("staffAccountId", "nickname", "avatar")))
                .andExpect(jsonPath("$.components.schemas.MyStaffProfileResponse.properties.introduction").exists())
                .andExpect(jsonPath("$.components.schemas.MyStaffProfileResponse.properties.name").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.MyStaffProfileResponse.properties.email").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.InitialAvatarResponse.required",
                        containsInAnyOrder("initial", "paletteIndex")));
    }

    @Test
    @DisplayName("닉네임만 바꾸던 경로(PUT /api/organizer/account/nickname)는 내 프로필 편집에 합쳐져 명세에 없다")
    void nicknameOnlyPathIsMerged() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/organizer/account/nickname']").doesNotExist());
    }
}
