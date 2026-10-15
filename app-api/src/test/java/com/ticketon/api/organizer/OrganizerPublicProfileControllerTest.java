package com.ticketon.api.organizer;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.ChangeOrganizerPublicProfileCommand;
import com.ticketon.application.organizer.port.in.ChangeOrganizerPublicProfileUseCase;
import com.ticketon.application.organizer.port.in.GetOrganizerPublicProfileQuery;
import com.ticketon.application.organizer.port.in.GetOrganizerPublicProfileUseCase;
import com.ticketon.application.organizer.port.in.OrganizerPublicProfileResult;
import com.ticketon.application.organizer.port.in.RemoveOrganizerLogoCommand;
import com.ticketon.application.organizer.port.in.RemoveOrganizerLogoUseCase;
import com.ticketon.application.organizer.port.in.UploadOrganizerLogoCommand;
import com.ticketon.application.organizer.port.in.UploadOrganizerLogoUseCase;
import com.ticketon.common.TestImages;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerLogoImage;
import com.ticketon.domain.policy.ProposedPolicy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 주최자 공개 정보 API — 경로의 주최자 id와 로그인한 운영진을 그대로 유스케이스에 넘기는지, 소개 · 링크 · 로고 파일의 입력 검증(400)과 응답 모양,
 * 유스케이스가 거절할 때의 상태 코드를 본다. 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = OrganizerPublicProfileController.class)
@Import(SecurityConfiguration.class)
@DisplayName("주최자 공개 정보 API")
class OrganizerPublicProfileControllerTest {

    private static final String PATH = "/api/organizer/organizers/12/public-profile";

    private static final AccountRef PLANNER = AccountRef.of(AccountKind.STAFF, 2);

    private static final ActingStaff PLANNER_IN_TWELVE_MONTHS = new ActingStaff(PLANNER, 12);

    private static final String LOGO_URL =
            "http://localhost:8080/api/public/files/organizer-logos/Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jK.png";

    private static final OrganizerPublicProfileResult WITH_LOGO = new OrganizerPublicProfileResult(12,
            "2019년부터 소극장 정기공연을 해마다 두 번 올립니다.", List.of("https://www.instagram.com/twelve_months_theater"),
            LOGO_URL, "극", 4);

    private static final OrganizerPublicProfileResult WITHOUT_LOGO = new OrganizerPublicProfileResult(12, null, List.of(),
            null, "극", 4);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetOrganizerPublicProfileUseCase getOrganizerPublicProfileUseCase;

    @MockitoBean
    private ChangeOrganizerPublicProfileUseCase changeOrganizerPublicProfileUseCase;

    @MockitoBean
    private UploadOrganizerLogoUseCase uploadOrganizerLogoUseCase;

    @MockitoBean
    private RemoveOrganizerLogoUseCase removeOrganizerLogoUseCase;

    @Test
    @DisplayName("편집 화면은 소개 · 링크 · 로고 주소와 첫 글자 표시를 돌려준다")
    void getsProfile() throws Exception {
        given(getOrganizerPublicProfileUseCase.get(new GetOrganizerPublicProfileQuery(PLANNER_IN_TWELVE_MONTHS)))
                .willReturn(WITH_LOGO);

        mockMvc.perform(get(PATH).with(loggedInAs(PLANNER)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"organizerId": 12, "introduction": "2019년부터 소극장 정기공연을 해마다 두 번 올립니다.",
                         "snsLinks": ["https://www.instagram.com/twelve_months_theater"],
                         "logoUrl": "%s",
                         "avatar": {"initial": "극", "paletteIndex": 4}}""".formatted(LOGO_URL), JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("소개 · 링크를 고치면 보낸 값(링크는 앞뒤 공백을 걷어)을 넘기고 고친 공개 정보를 돌려준다")
    void changesProfile() throws Exception {
        given(changeOrganizerPublicProfileUseCase.change(new ChangeOrganizerPublicProfileCommand(PLANNER_IN_TWELVE_MONTHS,
                " 2019년부터 소극장 정기공연을 해마다 두 번 올립니다. ",
                List.of(ExternalLink.of("https://www.instagram.com/twelve_months_theater")))))
                .willReturn(WITH_LOGO);

        mockMvc.perform(change("""
                        {"introduction": " 2019년부터 소극장 정기공연을 해마다 두 번 올립니다. ",
                         "snsLinks": [" https://www.instagram.com/twelve_months_theater "]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.introduction").value("2019년부터 소극장 정기공연을 해마다 두 번 올립니다."))
                .andExpect(jsonPath("$.logoUrl").value(LOGO_URL));
    }

    @Test
    @DisplayName("소개 · 링크를 보내지 않으면 둘 다 지운다")
    void emptyBodyClearsProfile() throws Exception {
        given(changeOrganizerPublicProfileUseCase.change(
                new ChangeOrganizerPublicProfileCommand(PLANNER_IN_TWELVE_MONTHS, null, List.of())))
                .willReturn(WITHOUT_LOGO);

        mockMvc.perform(change("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.introduction").isEmpty())
                .andExpect(jsonPath("$.snsLinks").isEmpty())
                .andExpect(jsonPath("$.logoUrl").isEmpty());
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"javascript:alert(1)", "www.instagram.com/twelve", "", "ftp://files.example.com"})
    @DisplayName("http(s) 주소가 아닌 링크는 400이고 유스케이스를 부르지 않는다")
    void rejectsNonWebLink(String link) throws Exception {
        mockMvc.perform(change("{\"snsLinks\": [\"https://ok.example.com\", \"" + link + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("snsLinks[1]"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("http:// 또는 https://로 시작하는 주소를 입력해 주세요."));

        verifyNoInteractions(changeOrganizerPublicProfileUseCase);
    }

    @Test
    @DisplayName("정사각형 로고를 올리면 그 그림을 넘기고 로고 주소를 돌려준다")
    void uploadsLogo() throws Exception {
        byte[] png = TestImages.png(256, 256);
        given(uploadOrganizerLogoUseCase.upload(new UploadOrganizerLogoCommand(PLANNER_IN_TWELVE_MONTHS,
                OrganizerLogoImage.of("image/png", png))))
                .willReturn(WITH_LOGO);

        mockMvc.perform(uploadLogo(new MockMultipartFile("logo", "../../my logo.png", "image/png", png)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").value(LOGO_URL));
    }

    @Test
    @DisplayName("정사각형이 아닌 그림은 400이고 logo 칸에 까닭을 담는다")
    void rejectsNonSquareLogo() throws Exception {
        expectLogoRejected(new MockMultipartFile("logo", "logo.png", "image/png", TestImages.png(400, 300)),
                "로고는 정사각형으로 잘라 올려 주세요.");
    }

    @Test
    @DisplayName("용량 상한(제안 2MB)을 넘는 파일은 400이고 상한을 알려 준다")
    void rejectsOverSizeLogo() throws Exception {
        byte[] overLimit = Arrays.copyOf(TestImages.png(64, 64),
                (int) ProposedPolicy.ORGANIZER_LOGO_MAXIMUM_SIZE.bytes() + 1);

        expectLogoRejected(new MockMultipartFile("logo", "logo.png", "image/png", overLimit),
                "로고는 2MB 이하로 올려 주세요.");
    }

    @Test
    @DisplayName("PNG · JPEG가 아닌 그림, 그림인 척한 글자, 적은 형식과 실제 내용이 다른 파일은 400이다 — 확장자는 보지 않는다")
    void rejectsUnsupportedFormat() throws Exception {
        String message = "로고는 PNG 또는 JPEG 그림만 올릴 수 있습니다.";
        expectLogoRejected(new MockMultipartFile("logo", "logo.gif", "image/gif", TestImages.of("gif", 64, 64)), message);
        expectLogoRejected(new MockMultipartFile("logo", "logo.png", "image/png",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)), message);
        expectLogoRejected(new MockMultipartFile("logo", "logo.png", "image/png", TestImages.jpeg(64, 64)), message);
        expectLogoRejected(new MockMultipartFile("logo", "logo.png", "application/octet-stream", TestImages.png(64, 64)),
                message);
    }

    @Test
    @DisplayName("빈 파일은 400이다")
    void rejectsEmptyLogo() throws Exception {
        expectLogoRejected(new MockMultipartFile("logo", "logo.png", "image/png", new byte[0]), "로고 파일을 골라 주세요.");
    }

    @Test
    @DisplayName("logo 칸 없이 올리면 400이고 logo 칸이 빠졌다고 알려 준다")
    void rejectsMissingLogoPart() throws Exception {
        mockMvc.perform(uploadLogo(new MockMultipartFile("image", "logo.png", "image/png", TestImages.png(64, 64))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("logo"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("필수 항목입니다."));

        verifyNoInteractions(uploadOrganizerLogoUseCase);
    }

    @Test
    @DisplayName("로고를 지우면 로고 주소가 비고 첫 글자 표시를 돌려준다")
    void removesLogo() throws Exception {
        given(removeOrganizerLogoUseCase.remove(new RemoveOrganizerLogoCommand(PLANNER_IN_TWELVE_MONTHS)))
                .willReturn(WITHOUT_LOGO);

        mockMvc.perform(delete(PATH + "/logo").with(csrf()).with(loggedInAs(PLANNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").isEmpty())
                .andExpect(jsonPath("$.avatar.initial").value("극"))
                .andExpect(jsonPath("$.avatar.paletteIndex").value(4));
    }

    @Test
    @DisplayName("그 주최자의 운영진이 아니라 유스케이스가 거절하면 403 NOT_ORGANIZER_STAFF다")
    void outsiderIsForbidden() throws Exception {
        given(uploadOrganizerLogoUseCase.upload(any()))
                .willThrow(new PermissionDeniedException(OrganizerErrorCode.NOT_ORGANIZER_STAFF));

        mockMvc.perform(uploadLogo(new MockMultipartFile("logo", "logo.png", "image/png", TestImages.png(64, 64))))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("NOT_ORGANIZER_STAFF", "이 주최자의 운영진이 아닙니다."));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AccountKind.class, names = "STAFF", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("운영진이 아닌 계정은 주최 관리 영역이라 403이고, 로그인하지 않으면 401이다")
    void onlyStaffArea(AccountKind kind) throws Exception {
        mockMvc.perform(get(PATH).with(loggedInAs(AccountRef.of(kind, 2))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(getOrganizerPublicProfileUseCase);
    }

    private MockHttpServletRequestBuilder change(String body) {
        return put(PATH).with(csrf()).with(loggedInAs(PLANNER)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private RequestBuilder uploadLogo(MockMultipartFile file) {
        return multipart(PATH + "/logo").file(file).with(csrf()).with(loggedInAs(PLANNER));
    }

    private void expectLogoRejected(MockMultipartFile file, String message) throws Exception {
        mockMvc.perform(uploadLogo(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("logo"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value(message));

        verifyNoInteractions(uploadOrganizerLogoUseCase);
    }
}
