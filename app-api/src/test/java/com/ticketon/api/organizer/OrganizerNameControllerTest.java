package com.ticketon.api.organizer;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.RenameOrganizerCommand;
import com.ticketon.application.organizer.port.in.RenameOrganizerResult;
import com.ticketon.application.organizer.port.in.RenameOrganizerUseCase;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 주최자명 변경 API — 경로의 주최자 id와 로그인한 운영진을 그대로 유스케이스에 넘기는지, 이름 입력 검증(400)과 응답 모양, 유스케이스가 거절할 때의
 * 상태 코드를 본다. 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = OrganizerNameController.class)
@Import(SecurityConfiguration.class)
@DisplayName("주최자명 변경 API")
class OrganizerNameControllerTest {

    private static final String PATH = "/api/organizer/organizers/12/name";

    private static final AccountRef JUNGHOON = AccountRef.of(AccountKind.STAFF, 1);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RenameOrganizerUseCase renameOrganizerUseCase;

    @Test
    @DisplayName("바꾸면 경로의 주최자 · 로그인한 운영진 · 앞뒤 공백을 걷은 이름을 넘기고 바꾼 이름을 돌려준다")
    void renames() throws Exception {
        given(renameOrganizerUseCase.rename(new RenameOrganizerCommand(new ActingStaff(JUNGHOON, 12),
                OrganizerName.of("열두달 프로젝트"))))
                .willReturn(new RenameOrganizerResult(12, "열두달 프로젝트"));

        mockMvc.perform(rename(JUNGHOON, "{\"name\": \" 열두달 프로젝트 \"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"organizerId": 12, "name": "열두달 프로젝트"}""", JsonCompareMode.STRICT));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{\"name\": null}", "{\"name\": \"\"}", "{\"name\": \"   \"}", "{\"name\": \"　\"}"})
    @DisplayName("이름이 없거나 공백뿐이면(전각 공백 포함) 400이고 name 칸에 까닭을 담는다 — 유스케이스를 부르지 않는다")
    void rejectsBlankName(String body) throws Exception {
        mockMvc.perform(rename(JUNGHOON, body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("주최자명을 입력해 주세요."));

        verifyNoInteractions(renameOrganizerUseCase);
    }

    @Test
    @DisplayName("운영자라 유스케이스가 거절하면 403 OWNER_ONLY_ACTION, 바깥 사람이면 403 NOT_ORGANIZER_STAFF다")
    void rejections() throws Exception {
        AccountRef planner = AccountRef.of(AccountKind.STAFF, 2);
        willThrow(new PermissionDeniedException(OrganizerErrorCode.OWNER_ONLY_ACTION))
                .given(renameOrganizerUseCase).rename(any());
        mockMvc.perform(rename(planner, "{\"name\": \"열두달 프로젝트\"}"))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("OWNER_ONLY_ACTION", "소유자만 할 수 있는 일입니다."));

        willThrow(new PermissionDeniedException(OrganizerErrorCode.NOT_ORGANIZER_STAFF))
                .given(renameOrganizerUseCase).rename(any());
        mockMvc.perform(rename(planner, "{\"name\": \"열두달 프로젝트\"}"))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("NOT_ORGANIZER_STAFF", "이 주최자의 운영진이 아닙니다."));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AccountKind.class, names = "STAFF", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("운영진이 아닌 계정은 주최 관리 영역이라 403이고, 로그인하지 않으면 401이다")
    void onlyStaffArea(AccountKind kind) throws Exception {
        mockMvc.perform(rename(AccountRef.of(kind, 1), "{\"name\": \"열두달 프로젝트\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"열두달 프로젝트\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(renameOrganizerUseCase);
    }

    private MockHttpServletRequestBuilder rename(AccountRef account, String body) {
        return put(PATH).with(csrf()).with(loggedInAs(account)).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
