package com.ticketon.api.admin;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static com.ticketon.api.security.TestLogin.loggedInAsAdmin;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.admin.port.in.ConfirmOwnershipChangeCommand;
import com.ticketon.application.admin.port.in.ConfirmOwnershipChangeUseCase;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 소유자 변경 사후 확인 API — 경로의 이력 id와 로그인한 관리자를 그대로 유스케이스에 넘기는지, 유스케이스가 거절할 때의 상태 코드, 관리자만
 * 열리는지를 본다. 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = OwnershipChangeController.class)
@Import(SecurityConfiguration.class)
@DisplayName("소유자 변경 사후 확인 API — 관리자만")
class OwnershipChangeControllerTest {

    private static final String CONFIRMATION_PATH = "/api/admin/ownership-changes/9/confirmation";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfirmOwnershipChangeUseCase confirmOwnershipChangeUseCase;

    @Test
    @DisplayName("확인하면 204이고 경로의 이력 id와 로그인한 관리자를 넘긴다")
    void confirms() throws Exception {
        mockMvc.perform(post(CONFIRMATION_PATH).with(csrf()).with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                .andExpect(status().isNoContent());

        verify(confirmOwnershipChangeUseCase).confirm(
                new ConfirmOwnershipChangeCommand(AccountRef.of(AccountKind.ADMIN, 4), 9));
    }

    @Test
    @DisplayName("이미 확인했으면 409 OWNERSHIP_CHANGE_ALREADY_CONFIRMED, 없는 이력이면 404 OWNERSHIP_CHANGE_NOT_FOUND다")
    void rejections() throws Exception {
        willThrow(new ConflictException(OrganizerErrorCode.OWNERSHIP_CHANGE_ALREADY_CONFIRMED))
                .given(confirmOwnershipChangeUseCase).confirm(new ConfirmOwnershipChangeCommand(
                        AccountRef.of(AccountKind.ADMIN, 4), 9));
        mockMvc.perform(post(CONFIRMATION_PATH).with(csrf()).with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(errorBody("OWNERSHIP_CHANGE_ALREADY_CONFIRMED", "이미 확인한 소유자 변경입니다."));

        willThrow(new NotFoundException(OrganizerErrorCode.OWNERSHIP_CHANGE_NOT_FOUND))
                .given(confirmOwnershipChangeUseCase).confirm(new ConfirmOwnershipChangeCommand(
                        AccountRef.of(AccountKind.ADMIN, 4), 10));
        mockMvc.perform(post("/api/admin/ownership-changes/10/confirmation").with(csrf())
                        .with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(errorBody("OWNERSHIP_CHANGE_NOT_FOUND", "소유자 변경 기록을 찾을 수 없습니다."));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"0", "-1", "nine"})
    @DisplayName("이력 id가 1 이상의 정수가 아니면 400 INVALID_INPUT이다")
    void rejectsInvalidId(String ownershipChangeId) throws Exception {
        mockMvc.perform(post("/api/admin/ownership-changes/" + ownershipChangeId + "/confirmation").with(csrf())
                        .with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(confirmOwnershipChangeUseCase);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AccountKind.class, names = {"AUDIENCE", "STAFF", "CHECKIN"})
    @DisplayName("관리자가 아닌 계정은 403이다")
    void adminOnly(AccountKind kind) throws Exception {
        mockMvc.perform(post(CONFIRMATION_PATH).with(csrf()).with(loggedInAs(kind, 7)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));

        verifyNoInteractions(confirmOwnershipChangeUseCase);
    }

    @Test
    @DisplayName("로그인하지 않았으면 401이다")
    void requiresLogin() throws Exception {
        mockMvc.perform(post(CONFIRMATION_PATH).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));

        verifyNoInteractions(confirmOwnershipChangeUseCase);
    }
}
