package com.ticketon.api.organizer;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.organizer.port.in.ChangeOrganizerPublicProfileUseCase;
import com.ticketon.application.organizer.port.in.GetOrganizerLogoFileUseCase;
import com.ticketon.application.organizer.port.in.GetOrganizerPublicProfileUseCase;
import com.ticketon.application.organizer.port.in.RemoveOrganizerLogoUseCase;
import com.ticketon.application.organizer.port.in.UploadOrganizerLogoUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 주최자 공개 정보 편집 · 로고 올리기 · 로고 파일의 요청 · 응답 모양이 명세에 그대로
 * 실리는지 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = {OrganizerPublicProfileController.class, OrganizerLogoFileController.class})
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("주최자 공개 정보 API 명세")
class OrganizerPublicProfileApiDocsTest {

    private static final String PROFILE_PATH = "$.paths['/api/organizer/organizers/{organizerId}/public-profile']";

    private static final String LOGO_PATH = "$.paths['/api/organizer/organizers/{organizerId}/public-profile/logo']";

    private static final String LOGO_FILE_PATH = "$.paths['/api/public/files/organizer-logos/{fileKey}']";

    private static final String PROFILE_RESPONSE = "#/components/schemas/OrganizerPublicProfileResponse";

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

    @MockitoBean
    private GetOrganizerLogoFileUseCase getOrganizerLogoFileUseCase;

    @Test
    @DisplayName("공개 정보 보기 · 고치기 · 로고 올리기 · 지우기는 모두 OrganizerPublicProfileResponse를 돌려주고, 경로 파라미터는 organizerId 하나다")
    void profileOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(PROFILE_PATH + ".get.responses['200'].content['application/json'].schema['$ref']")
                        .value(PROFILE_RESPONSE))
                .andExpect(jsonPath(PROFILE_PATH + ".get.parameters[*].name", containsInAnyOrder("organizerId")))
                .andExpect(jsonPath(PROFILE_PATH + ".put.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ChangeOrganizerPublicProfileRequest"))
                .andExpect(jsonPath(PROFILE_PATH + ".put.responses['200'].content['application/json'].schema['$ref']")
                        .value(PROFILE_RESPONSE))
                .andExpect(jsonPath(LOGO_PATH + ".post.responses['200'].content['application/json'].schema['$ref']")
                        .value(PROFILE_RESPONSE))
                .andExpect(jsonPath(LOGO_PATH + ".delete.responses['200'].content['application/json'].schema['$ref']")
                        .value(PROFILE_RESPONSE));
    }

    @Test
    @DisplayName("로고 올리기는 multipart/form-data의 logo 칸(파일 하나, 필수)으로 받는다")
    void logoUploadIsMultipart() throws Exception {
        String body = LOGO_PATH + ".post.requestBody.content['multipart/form-data'].schema";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(body + ".properties.logo.type").value("string"))
                .andExpect(jsonPath(body + ".properties.logo.format").value("binary"))
                .andExpect(jsonPath(body + ".required", containsInAnyOrder("logo")));
    }

    @Test
    @DisplayName("응답은 organizerId · snsLinks · avatar가 필수이고, 소개와 로고 주소는 비어 있을 수 있다")
    void responseSchema() throws Exception {
        String schema = "$.components.schemas.OrganizerPublicProfileResponse";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(schema + ".required", containsInAnyOrder("organizerId", "snsLinks", "avatar")))
                .andExpect(jsonPath(schema + ".properties.introduction").exists())
                .andExpect(jsonPath(schema + ".properties.logoUrl").exists())
                .andExpect(jsonPath(schema + ".properties.avatar['$ref']")
                        .value("#/components/schemas/InitialAvatarResponse"));
    }

    @Test
    @DisplayName("로고 파일은 로고 열쇠 경로 하나로 열고, PNG · JPEG 그림을 돌려준다")
    void logoFileOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(LOGO_FILE_PATH + ".get.parameters[*].name", containsInAnyOrder("fileKey")))
                .andExpect(jsonPath(LOGO_FILE_PATH + ".get.responses['200'].content['image/png'].schema.format")
                        .value("binary"))
                .andExpect(jsonPath(LOGO_FILE_PATH + ".get.responses['200'].content['image/jpeg'].schema.format")
                        .value("binary"));
    }
}
