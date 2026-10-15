package com.ticketon.api.organizer;

import com.ticketon.application.organizer.port.in.GetOrganizerLogoFileQuery;
import com.ticketon.application.organizer.port.in.GetOrganizerLogoFileUseCase;
import com.ticketon.application.organizer.port.in.OrganizerLogoFileResult;
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
 * 주최자 로고 파일 — 공개 영역(/api/public)이라 로그인 없이 누구나 연다(api/security). 로고의 공개 주소가 이 서버를 가리킬 때(파일 업체를 정하기
 * 전 로컬 저장, docs/16) 저장소의 파일을 내준다. 로고 열쇠 하나로만 찾고, 목록이나 폴더를 보여 주지 않는다 — 열쇠의 꼴이 아니면 저장소에 닿지
 * 않고 404다.
 * <p>
 * 경로 {@code /api/public/files/organizer-logos/{열쇠}}는 로컬 저장 어댑터가 만드는 공개 주소({@code 공개 기준 주소/organizer-logos/열쇠})와 맞춘다.
 */
@RestController
@RequestMapping("/api/public/files/organizer-logos")
@Tag(name = "파일", description = "로그인 없이 여는 공개 파일 — 주최자 로고 (docs/01 \"공개 페이지 — 로고\")")
public class OrganizerLogoFileController {

    private final GetOrganizerLogoFileUseCase getOrganizerLogoFileUseCase;

    public OrganizerLogoFileController(GetOrganizerLogoFileUseCase getOrganizerLogoFileUseCase) {
        this.getOrganizerLogoFileUseCase = getOrganizerLogoFileUseCase;
    }

    @Operation(summary = "주최자 로고 파일", description = "로고의 공개 주소(logoUrl)로 여는 그림 파일. 로그인 없이 누구나 연다. 응답 형식은 "
            + "저장한 그림 형식(image/png 또는 image/jpeg)이다. 바꾸거나 지운 로고, 로고 열쇠의 꼴이 아닌 값은 404 ORGANIZER_LOGO_NOT_FOUND.",
            responses = @ApiResponse(responseCode = "200", description = "로고 그림", content = {
                    @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = MediaType.IMAGE_JPEG_VALUE, schema = @Schema(type = "string", format = "binary"))}))
    @GetMapping("/{fileKey}")
    public ResponseEntity<byte[]> logo(@PathVariable String fileKey) {
        OrganizerLogoFileResult file = getOrganizerLogoFileUseCase.get(new GetOrganizerLogoFileQuery(fileKey));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .body(file.content());
    }
}
