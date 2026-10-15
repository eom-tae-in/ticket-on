package com.ticketon.api.admin;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static com.ticketon.api.security.TestLogin.loggedInAsAdmin;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.admin.port.in.AdminWorkQueueResult;
import com.ticketon.application.admin.port.in.GetAdminWorkQueueUseCase;
import com.ticketon.application.admin.query.AdminWorkKind;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AdminGrade;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 관리자 처리 목록 API — 결과 → 응답 변환(종류 이름, 한국 시각, 강조)과 관리자만 열리는지를 본다. 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = AdminWorkQueueController.class)
@Import(SecurityConfiguration.class)
@DisplayName("관리자 처리 목록 API — 관리자만")
class AdminWorkQueueControllerTest {

    private static final String WORK_QUEUE_PATH = "/api/admin/work-queue";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetAdminWorkQueueUseCase getAdminWorkQueueUseCase;

    @Test
    @DisplayName("요청을 접수 순서 그대로 종류 · 대상 · 제목 · 접수 일시(한국 시각) · 강조로 돌려준다")
    void returnsItems() throws Exception {
        given(getAdminWorkQueueUseCase.getWorkQueue()).willReturn(new AdminWorkQueueResult(List.of(
                new AdminWorkQueueResult.Item(AdminWorkKind.ORGANIZER_APPLICATION, 3, "극단 열두달",
                        KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0)), true),
                new AdminWorkQueueResult.Item(AdminWorkKind.ORGANIZER_APPLICATION, 4, "늦여름",
                        KoreanTime.toInstant(LocalDateTime.of(2026, 10, 16, 18, 0)), false))));

        mockMvc.perform(get(WORK_QUEUE_PATH).with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"items": [
                          {"kind": "ORGANIZER_APPLICATION", "targetId": 3, "title": "극단 열두달",
                           "receivedAt": "2026-10-15T10:00:00+09:00", "highlighted": true},
                          {"kind": "ORGANIZER_APPLICATION", "targetId": 4, "title": "늦여름",
                           "receivedAt": "2026-10-16T18:00:00+09:00", "highlighted": false}
                        ]}""", JsonCompareMode.STRICT));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(AdminWorkKind.class)
    @DisplayName("처리 목록의 종류는 모두 같은 이름의 API 이름으로 나간다 — 종류를 더하면 API 이름도 함께 더한다")
    void everyKindHasApiName(AdminWorkKind kind) throws Exception {
        given(getAdminWorkQueueUseCase.getWorkQueue()).willReturn(new AdminWorkQueueResult(List.of(
                new AdminWorkQueueResult.Item(kind, 5, "극단 열두달 → 열두달 프로젝트",
                        KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0)), false))));

        mockMvc.perform(get(WORK_QUEUE_PATH).with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].kind").value(kind.name()));
    }

    @Test
    @DisplayName("처리할 요청이 없으면 빈 목록이다")
    void emptyQueue() throws Exception {
        given(getAdminWorkQueueUseCase.getWorkQueue()).willReturn(new AdminWorkQueueResult(List.of()));

        mockMvc.perform(get(WORK_QUEUE_PATH).with(loggedInAsAdmin(1, AdminGrade.SUPER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"items\": []}", JsonCompareMode.STRICT));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AccountKind.class, names = "ADMIN", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("관리자가 아닌 주체는 403이고 유스케이스를 부르지 않는다")
    void nonAdminIsForbidden(AccountKind kind) throws Exception {
        mockMvc.perform(get(WORK_QUEUE_PATH).with(loggedInAs(kind, 7)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));

        verify(getAdminWorkQueueUseCase, never()).getWorkQueue();
    }

    @Test
    @DisplayName("로그인하지 않았으면 401이다")
    void requiresLogin() throws Exception {
        mockMvc.perform(get(WORK_QUEUE_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
    }
}
