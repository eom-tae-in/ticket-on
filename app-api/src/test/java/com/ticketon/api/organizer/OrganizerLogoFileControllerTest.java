package com.ticketon.api.organizer;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.organizer.port.in.GetOrganizerLogoFileQuery;
import com.ticketon.application.organizer.port.in.GetOrganizerLogoFileUseCase;
import com.ticketon.application.organizer.port.in.OrganizerLogoFileResult;
import com.ticketon.common.TestImages;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 주최자 로고 파일 API — 로그인 없이 열리고, 저장한 그림 형식으로 내주고, 없는 로고는 404인지 본다. 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = OrganizerLogoFileController.class)
@Import(SecurityConfiguration.class)
@DisplayName("주최자 로고 파일 API")
class OrganizerLogoFileControllerTest {

    private static final String FILE_KEY = "Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jK.png";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetOrganizerLogoFileUseCase getOrganizerLogoFileUseCase;

    @Test
    @DisplayName("로그인 없이 로고 그림을 저장한 형식으로 내준다 — 브라우저가 형식을 짐작하지 않게 nosniff가 붙는다")
    void servesLogoWithoutLogin() throws Exception {
        byte[] png = TestImages.png(64, 64);
        given(getOrganizerLogoFileUseCase.get(new GetOrganizerLogoFileQuery(FILE_KEY)))
                .willReturn(new OrganizerLogoFileResult(png, "image/png"));

        mockMvc.perform(get("/api/public/files/organizer-logos/" + FILE_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(png))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("그림만 받는 브라우저 요청에도 JPEG 로고를 내준다")
    void servesJpegToImageRequest() throws Exception {
        byte[] jpeg = TestImages.jpeg(64, 64);
        given(getOrganizerLogoFileUseCase.get(new GetOrganizerLogoFileQuery("Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jK.jpg")))
                .willReturn(new OrganizerLogoFileResult(jpeg, "image/jpeg"));

        mockMvc.perform(get("/api/public/files/organizer-logos/Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jK.jpg")
                        .header(HttpHeaders.ACCEPT, "image/avif,image/webp,image/*,*/*;q=0.8"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    @DisplayName("없거나 지운 로고는 404 ORGANIZER_LOGO_NOT_FOUND다")
    void missingLogoIsNotFound() throws Exception {
        given(getOrganizerLogoFileUseCase.get(new GetOrganizerLogoFileQuery(FILE_KEY)))
                .willThrow(new NotFoundException(OrganizerErrorCode.ORGANIZER_LOGO_NOT_FOUND));

        mockMvc.perform(get("/api/public/files/organizer-logos/" + FILE_KEY))
                .andExpect(status().isNotFound())
                .andExpect(errorBody("ORGANIZER_LOGO_NOT_FOUND", "로고를 찾을 수 없습니다."));
    }
}
