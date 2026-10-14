package com.ticketon.api.account;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.GetRandomNicknameUseCase;
import com.ticketon.application.account.port.in.SignUpStaffUseCase;
import com.ticketon.application.account.port.in.VerifyStaffEmailUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 운영진 가입·인증·랜덤 닉네임 API의 요청·응답 모양이 명세에 그대로 실리는지 본다.
 * DB 없이 돈다.
 */
@WebMvcTest(controllers = StaffSignUpController.class)
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("운영진 가입·이메일 인증·랜덤 닉네임 API 명세")
class StaffSignUpApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignUpStaffUseCase signUpStaffUseCase;

    @MockitoBean
    private VerifyStaffEmailUseCase verifyStaffEmailUseCase;

    @MockitoBean
    private GetRandomNicknameUseCase getRandomNicknameUseCase;

    @Test
    @DisplayName("가입은 201에 SignUpStaffResponse(email)를 돌려주고, 요청 칸 넷(이름·닉네임·이메일·비밀번호)이 모두 필수다")
    void signUpOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/staff/signup'].post.responses['201']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/SignUpStaffResponse"))
                .andExpect(jsonPath("$.components.schemas.SignUpStaffRequest.required",
                        containsInAnyOrder("name", "nickname", "email", "password")))
                .andExpect(jsonPath("$.components.schemas.SignUpStaffResponse.required", containsInAnyOrder("email")));
    }

    @Test
    @DisplayName("이메일 인증 확인은 token 하나를 받고 204로 끝난다")
    void emailVerificationOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/staff/email-verification'].post.responses['204']").exists())
                .andExpect(jsonPath("$.components.schemas.VerifyStaffEmailRequest.required", containsInAnyOrder("token")));
    }

    @Test
    @DisplayName("랜덤 닉네임은 파라미터 없는 GET이고 200에 RandomNicknameResponse(nickname)를 돌려준다")
    void randomNicknameOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/staff/random-nickname'].get.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/RandomNicknameResponse"))
                .andExpect(jsonPath("$.paths['/api/auth/staff/random-nickname'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.RandomNicknameResponse.required", containsInAnyOrder("nickname")));
    }
}
