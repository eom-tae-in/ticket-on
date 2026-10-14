package com.ticketon.api.security;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.domain.account.AccountKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 컨트롤러 안에서 난 Spring Security 예외(메서드 보안 등)가 500으로 새지 않고, 보안 필터가 막은 요청과 같은 401/403이 되는지 본다.
 */
@WebMvcTest(controllers = SecurityExceptionTestController.class)
@Import({SecurityConfiguration.class, SecurityExceptionTestController.class})
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("컨트롤러 안의 Spring Security 예외 — 401/403 ErrorResponse")
class SecurityExceptionResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("로그인한 주체의 권한 없음은 403 ACCESS_DENIED다")
    void accessDeniedForLoggedInAccountIs403() throws Exception {
        mockMvc.perform(get("/api/audience/test/access-denied").with(loggedInAs(AccountKind.AUDIENCE, 7)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));
    }

    @Test
    @DisplayName("로그인하지 않은 요청의 권한 없음은 401 UNAUTHENTICATED다 — 로그인하면 될 수도 있으므로")
    void accessDeniedForAnonymousIs401() throws Exception {
        mockMvc.perform(get("/api/public/test/access-denied"))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
    }

    @Test
    @DisplayName("인증 예외는 401 UNAUTHENTICATED다")
    void authenticationExceptionIs401() throws Exception {
        mockMvc.perform(get("/api/public/test/authentication-required").with(loggedInAs(AccountKind.AUDIENCE, 7)))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
    }

    @Test
    @DisplayName("처리하지 못한 예외(500)로 기록되지 않는다")
    void isNotLoggedAsUnexpectedError(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/audience/test/access-denied").with(loggedInAs(AccountKind.AUDIENCE, 7)));

        assertThat(output).doesNotContain("처리하지 못한 예외").doesNotContain("Failure in @ExceptionHandler");
    }
}
