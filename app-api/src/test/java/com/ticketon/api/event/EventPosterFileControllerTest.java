package com.ticketon.api.event;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.event.port.in.EventPosterFileResult;
import com.ticketon.application.event.port.in.GetEventPosterFileQuery;
import com.ticketon.application.event.port.in.GetEventPosterFileUseCase;
import com.ticketon.common.TestImages;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.event.EventErrorCode;
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
 * 행사 포스터 파일 API — 로그인 없이 열리고(카톡 링크 미리보기도 로그인 없이 가져간다), 저장한 그림 형식으로 내주고, 없는 포스터는 404인지 본다.
 * 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = EventPosterFileController.class)
@Import(SecurityConfiguration.class)
@DisplayName("행사 포스터 파일 API")
class EventPosterFileControllerTest {

    private static final String FILE_KEY = "Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.jpg";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetEventPosterFileUseCase getEventPosterFileUseCase;

    @Test
    @DisplayName("로그인 없이 포스터 그림을 저장한 형식으로 내준다 — 브라우저가 형식을 짐작하지 않게 nosniff가 붙는다")
    void servesPosterWithoutLogin() throws Exception {
        byte[] jpeg = TestImages.jpeg(30, 40);
        given(getEventPosterFileUseCase.get(new GetEventPosterFileQuery(FILE_KEY)))
                .willReturn(new EventPosterFileResult(jpeg, "image/jpeg"));

        mockMvc.perform(get("/api/public/files/event-posters/" + FILE_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(jpeg))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("그림만 받는 브라우저 요청에도 PNG 포스터를 내준다")
    void servesPngToImageRequest() throws Exception {
        byte[] png = TestImages.png(30, 40);
        given(getEventPosterFileUseCase.get(new GetEventPosterFileQuery("Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.png")))
                .willReturn(new EventPosterFileResult(png, "image/png"));

        mockMvc.perform(get("/api/public/files/event-posters/Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.png")
                        .header(HttpHeaders.ACCEPT, "image/avif,image/webp,image/*,*/*;q=0.8"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    @DisplayName("없거나 바꾼 포스터는 404 EVENT_POSTER_NOT_FOUND다")
    void missingPosterIsNotFound() throws Exception {
        given(getEventPosterFileUseCase.get(new GetEventPosterFileQuery(FILE_KEY)))
                .willThrow(new NotFoundException(EventErrorCode.EVENT_POSTER_NOT_FOUND));

        mockMvc.perform(get("/api/public/files/event-posters/" + FILE_KEY))
                .andExpect(status().isNotFound())
                .andExpect(errorBody("EVENT_POSTER_NOT_FOUND", "포스터를 찾을 수 없습니다."));
    }
}
