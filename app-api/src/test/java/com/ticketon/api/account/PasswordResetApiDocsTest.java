package com.ticketon.api.account;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.config.DetachedExecutionConfiguration;
import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.RequestPasswordResetUseCase;
import com.ticketon.application.account.port.in.ResetPasswordUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 비밀번호 찾기·재설정의 요청·응답 모양이 명세에 그대로 실리는지 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = PasswordResetController.class)
@Import({SecurityConfiguration.class, DetachedExecutionConfiguration.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("비밀번호 찾기·재설정 API 명세")
class PasswordResetApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestPasswordResetUseCase requestPasswordResetUseCase;

    @MockitoBean
    private ResetPasswordUseCase resetPasswordUseCase;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"audience", "staff"})
    @DisplayName("비밀번호 찾기는 email 하나를 받아 본문 없는 202로 끝난다 — 가입 여부를 알려 주는 칸이 없다")
    void requestResetOperation(String side) throws Exception {
        String path = "$.paths['/api/auth/" + side + "/password-reset-request'].post";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/RequestPasswordResetRequest"))
                .andExpect(jsonPath(path + ".responses['202']").exists())
                .andExpect(jsonPath(path + ".responses['202'].content").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.RequestPasswordResetRequest.required", containsInAnyOrder("email")));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"audience", "staff"})
    @DisplayName("비밀번호 재설정은 token·newPassword를 받아 본문 없는 204로 끝난다")
    void resetOperation(String side) throws Exception {
        String path = "$.paths['/api/auth/" + side + "/password-reset'].post";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ResetPasswordRequest"))
                .andExpect(jsonPath(path + ".responses['204']").exists())
                .andExpect(jsonPath("$.components.schemas.ResetPasswordRequest.required",
                        containsInAnyOrder("token", "newPassword")));
    }
}
