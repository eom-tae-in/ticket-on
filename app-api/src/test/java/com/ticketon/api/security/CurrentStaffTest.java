package com.ticketon.api.security;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 주최 관리 컨트롤러가 운영진 · 주최자를 받는 공통 방식({@link CurrentStaff}) — 로그인한 운영진과 경로의 주최자 id로 {@link ActingStaff}를
 * 만들고, 권한 확인 유스케이스가 거절하면 403으로 응답하는지 본다. 보안 필터를 건 채로 돌고 DB는 없다. 권한 판정 자체는 도메인·유스케이스
 * 테스트와 통합 테스트가 본다.
 */
@WebMvcTest(controllers = StaffAreaTestController.class)
@Import({SecurityConfiguration.class, StaffAreaTestController.class})
@DisplayName("주최 관리의 운영진 · 주최자 인자 (@CurrentStaff)")
class CurrentStaffTest {

    private static final String UNAUTHENTICATED_MESSAGE = "로그인이 필요합니다.";

    private static final String ACCESS_DENIED_MESSAGE = "이 요청을 처리할 권한이 없습니다.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckStaffPermissionUseCase checkStaffPermissionUseCase;

    @Test
    @DisplayName("로그인한 운영진과 경로의 주최자 id를 함께 받는다 — 주최자 전환으로 고른 주최자")
    void receivesStaffAndOrganizerFromPath() throws Exception {
        mockMvc.perform(get("/api/organizer/organizers/12/test").with(loggedInAs(AccountKind.STAFF, 2)))
                .andExpect(status().isOk())
                .andExpect(content().string("STAFF:2 → 12"));
    }

    @Test
    @DisplayName("경로에 주최자 안의 자원 id가 함께 와도 주최자 id는 주최자 자리에서 읽는다")
    void readsOrganizerIdBesideOtherPathVariables() throws Exception {
        mockMvc.perform(get("/api/organizer/organizers/12/test/events/99").with(loggedInAs(AccountKind.STAFF, 2)))
                .andExpect(status().isOk())
                .andExpect(content().string("STAFF:2 → 12 / 99"));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"0", "-1", "twelve", "9223372036854775808"})
    @DisplayName("주최자 id가 1 이상의 정수가 아니면 400 INVALID_INPUT이고, 칸은 organizerId다")
    void rejectsInvalidOrganizerId(String organizerId) throws Exception {
        mockMvc.perform(get("/api/organizer/organizers/" + organizerId + "/test").with(loggedInAs(AccountKind.STAFF, 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("organizerId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("로그인하지 않았으면 401이다")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/organizer/organizers/12/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", UNAUTHENTICATED_MESSAGE));
    }

    @Test
    @DisplayName("검표 영역처럼 다른 계정도 들어오는 경로라도 운영진이 아니면 403이다 — 검표 계정은 주최자의 일을 하지 않는다")
    void rejectsNonStaffAccount() throws Exception {
        mockMvc.perform(get("/api/checkin/organizers/12/test").with(loggedInAs(AccountKind.CHECKIN, 3)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", ACCESS_DENIED_MESSAGE));
        mockMvc.perform(get("/api/checkin/organizers/12/test").with(loggedInAs(AccountKind.STAFF, 2)))
                .andExpect(status().isOk())
                .andExpect(content().string("STAFF:2 → 12"));
    }

    @Test
    @DisplayName("받은 운영진 · 주최자와 하려는 일을 그대로 권한 확인에 넘기고, 확인한 역할로 일한다")
    void passesActingStaffToPermissionCheck() throws Exception {
        given(checkStaffPermissionUseCase.check(any())).willReturn(
                new StaffPermissionResult(12, 1, StaffRole.OWNER, StaffAction.CANCEL_SESSION));

        mockMvc.perform(get("/api/organizer/organizers/12/test/actions/CANCEL_SESSION")
                        .with(loggedInAs(AccountKind.STAFF, 1)))
                .andExpect(status().isOk())
                .andExpect(content().string("OWNER"));

        ArgumentCaptor<CheckStaffPermissionQuery> query = ArgumentCaptor.forClass(CheckStaffPermissionQuery.class);
        verify(checkStaffPermissionUseCase).check(query.capture());
        assertThat(query.getValue()).isEqualTo(new CheckStaffPermissionQuery(
                new ActingStaff(AccountRef.of(AccountKind.STAFF, 1), 12), StaffAction.CANCEL_SESSION));
    }

    @Test
    @DisplayName("운영자가 소유자만 하는 일을 하려 해 권한 확인이 거절하면 403 OWNER_ONLY_ACTION이다")
    void ownerOnlyActionByOperatorIsForbidden() throws Exception {
        given(checkStaffPermissionUseCase.check(any()))
                .willThrow(new PermissionDeniedException(OrganizerErrorCode.OWNER_ONLY_ACTION));

        mockMvc.perform(get("/api/organizer/organizers/12/test/actions/CANCEL_SESSION")
                        .with(loggedInAs(AccountKind.STAFF, 2)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("OWNER_ONLY_ACTION", "소유자만 할 수 있는 일입니다."));
    }

    @Test
    @DisplayName("다른 주최자의 운영진이 주최자 id를 바꿔 오면 403 NOT_ORGANIZER_STAFF다")
    void otherOrganizerStaffIsForbidden() throws Exception {
        given(checkStaffPermissionUseCase.check(any()))
                .willThrow(new PermissionDeniedException(OrganizerErrorCode.NOT_ORGANIZER_STAFF));

        mockMvc.perform(get("/api/organizer/organizers/20/test/actions/VIEW_STATUS")
                        .with(loggedInAs(AccountKind.STAFF, 2)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("NOT_ORGANIZER_STAFF", "이 주최자의 운영진이 아닙니다."));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AccountKind.class, names = {"AUDIENCE", "ADMIN", "CHECKIN"})
    @DisplayName("주최 관리 영역은 운영진이 아니면 보안 필터가 먼저 403으로 막는다")
    void organizerAreaIsStaffOnly(AccountKind kind) throws Exception {
        mockMvc.perform(get("/api/organizer/organizers/12/test").with(loggedInAs(kind, 7)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", ACCESS_DENIED_MESSAGE));
    }

    @Test
    @DisplayName("경로에 {organizerId}가 없는 주소에 붙이면 조용히 넘기지 않고 500이다")
    void withoutOrganizerIdInPathFails() throws Exception {
        mockMvc.perform(get("/api/organizer/test/without-organizer-id").with(loggedInAs(AccountKind.STAFF, 2)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("ActingStaff가 아닌 타입에 붙이면 조용히 넘기지 않고 500이다")
    void unsupportedTypeFails() throws Exception {
        mockMvc.perform(get("/api/organizer/organizers/12/test/wrong-type").with(loggedInAs(AccountKind.STAFF, 2)))
                .andExpect(status().isInternalServerError());
    }
}
