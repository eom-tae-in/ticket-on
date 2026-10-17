package com.ticketon.api.event;

import com.ticketon.application.event.port.in.EventPosterFileResult;
import com.ticketon.application.event.port.in.GetEventPosterFileQuery;
import com.ticketon.application.event.port.in.GetEventPosterFileUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행사 포스터 파일 — 공개 영역(/api/public)이라 로그인 없이 누구나 연다(api/security). 관객 화면과 카톡 링크 미리보기가 이 주소로 포스터를
 * 가져간다. 포스터의 공개 주소가 이 서버를 가리킬 때(파일 업체를 정하기 전 로컬 저장, docs/16) 저장소의 파일을 내준다. 포스터 열쇠 하나로만 찾고,
 * 목록이나 폴더를 보여 주지 않는다 — 열쇠의 꼴이 아니면 저장소에 닿지 않고 404다.
 * <p>
 * 경로 {@code /api/public/files/event-posters/{열쇠}}는 로컬 저장 어댑터가 만드는 공개 주소({@code 공개 기준 주소/event-posters/열쇠})와 맞춘다.
 */
@RestController
@RequestMapping("/api/public/files/event-posters")
@Tag(name = "파일", description = "로그인 없이 여는 공개 파일 — 주최자 로고 · 행사 포스터 (docs/01 \"공개 페이지 — 로고\", docs/03 \"기본 정보\")")
public class EventPosterFileController {

    private final GetEventPosterFileUseCase getEventPosterFileUseCase;

    public EventPosterFileController(GetEventPosterFileUseCase getEventPosterFileUseCase) {
        this.getEventPosterFileUseCase = getEventPosterFileUseCase;
    }

    @Operation(summary = "행사 포스터 파일", description = "포스터의 공개 주소(posterUrl)로 여는 그림 파일. 로그인 없이 누구나 연다. 응답 형식은 "
            + "저장한 그림 형식(image/png 또는 image/jpeg)이다. 바꾼 포스터, 포스터 열쇠의 꼴이 아닌 값은 404 EVENT_POSTER_NOT_FOUND.",
            responses = @ApiResponse(responseCode = "200", description = "포스터 그림", content = {
                    @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = MediaType.IMAGE_JPEG_VALUE, schema = @Schema(type = "string", format = "binary"))}))
    @GetMapping("/{fileKey}")
    public ResponseEntity<byte[]> poster(@PathVariable String fileKey) {
        EventPosterFileResult file = getEventPosterFileUseCase.get(new GetEventPosterFileQuery(fileKey));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .body(file.content());
    }
}
