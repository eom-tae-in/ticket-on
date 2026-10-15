package com.ticketon.api.organizer;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.ChangeOrganizerPublicProfileUseCase;
import com.ticketon.application.organizer.port.in.GetOrganizerPublicProfileQuery;
import com.ticketon.application.organizer.port.in.GetOrganizerPublicProfileUseCase;
import com.ticketon.application.organizer.port.in.RemoveOrganizerLogoCommand;
import com.ticketon.application.organizer.port.in.RemoveOrganizerLogoUseCase;
import com.ticketon.application.organizer.port.in.UploadOrganizerLogoCommand;
import com.ticketon.application.organizer.port.in.UploadOrganizerLogoUseCase;
import com.ticketon.domain.organizer.OrganizerLogoImage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 주최자 공개 정보 — 주최 관리 경로 규약({@code /api/organizer/organizers/{organizerId}/**}, {@link CurrentStaff})을 따른다. 소개 · SNS 링크 ·
 * 로고는 운영진 누구나 고치고(docs/01 "권한 두 가지", "공개 페이지 — 편집"), 소속 · 주최자 상태는 유스케이스가 운영진 권한 확인으로 본다.
 * 로그인 없이 보는 주최자 페이지는 따로다.
 */
@RestController
@RequestMapping("/api/organizer/organizers/{organizerId}/public-profile")
@Tag(name = "주최자 공개 정보", description = "주최자 페이지의 소개 · SNS 링크 · 로고 편집 — 운영진 누구나 (docs/01 \"공개 페이지 — 주최자 페이지\", "
        + "docs/13 \"주최자 공개 정보\")")
public class OrganizerPublicProfileController {

    private final GetOrganizerPublicProfileUseCase getOrganizerPublicProfileUseCase;
    private final ChangeOrganizerPublicProfileUseCase changeOrganizerPublicProfileUseCase;
    private final UploadOrganizerLogoUseCase uploadOrganizerLogoUseCase;
    private final RemoveOrganizerLogoUseCase removeOrganizerLogoUseCase;

    public OrganizerPublicProfileController(GetOrganizerPublicProfileUseCase getOrganizerPublicProfileUseCase,
            ChangeOrganizerPublicProfileUseCase changeOrganizerPublicProfileUseCase,
            UploadOrganizerLogoUseCase uploadOrganizerLogoUseCase, RemoveOrganizerLogoUseCase removeOrganizerLogoUseCase) {
        this.getOrganizerPublicProfileUseCase = getOrganizerPublicProfileUseCase;
        this.changeOrganizerPublicProfileUseCase = changeOrganizerPublicProfileUseCase;
        this.uploadOrganizerLogoUseCase = uploadOrganizerLogoUseCase;
        this.removeOrganizerLogoUseCase = removeOrganizerLogoUseCase;
    }

    @Operation(summary = "주최자 공개 정보 보기 (편집 화면)", description = "편집 칸을 채울 지금 공개 정보(소개 · SNS 링크 · 로고 주소)와 로고가 없을 때 "
            + "보이는 첫 글자 표시를 돌려준다. 운영진 누구나 본다. 그 주최자에 지금 소속된 운영진이 아니면(없는 주최자도) 403 NOT_ORGANIZER_STAFF, "
            + "활동 중인 주최자가 아니면 403 ORGANIZER_NOT_ACTIVE.")
    @GetMapping
    public OrganizerPublicProfileResponse get(@CurrentStaff ActingStaff actingStaff) {
        return OrganizerPublicProfileResponse.from(
                getOrganizerPublicProfileUseCase.get(new GetOrganizerPublicProfileQuery(actingStaff)));
    }

    @Operation(summary = "소개 · SNS 링크 고치기", description = "보낸 값으로 소개와 SNS 링크를 함께 바꾼다(둘 다 선택 — 비우면 지운다). 로고는 "
            + "그대로다. 운영진 누구나 고친다. 링크가 http(s) 주소가 아니면 400. 그 주최자의 운영진이 아니면 403 NOT_ORGANIZER_STAFF, 활동 중인 "
            + "주최자가 아니면 403 ORGANIZER_NOT_ACTIVE. 바뀌었으면 운영진 조작 기록에 남는다.")
    @PutMapping
    public OrganizerPublicProfileResponse change(@CurrentStaff ActingStaff actingStaff,
            @Valid @RequestBody ChangeOrganizerPublicProfileRequest request) {
        return OrganizerPublicProfileResponse.from(changeOrganizerPublicProfileUseCase.change(request.toCommand(actingStaff)));
    }

    @Operation(summary = "로고 올리기", description = "화면의 크롭 도구로 정사각형으로 자른 로고를 multipart/form-data의 logo 칸으로 올린다. 처음 "
            + "올리기와 바꾸기가 같고, 바꾸기 전 로고는 지워진다. 파일 이름은 쓰지 않고 추측할 수 없는 열쇠로 저장한다. 운영진 누구나 올린다. 빈 파일 · "
            + "용량 상한(정책값, 확인 필요) 초과 · PNG · JPEG가 아닌 파일(올린 쪽이 적은 형식과 실제 파일 내용을 함께 본다) · 정사각형이 아닌 그림은 "
            + "400(fieldErrors의 logo 칸에 까닭), logo 칸이 없으면 400. 그 주최자의 운영진이 아니면 403 NOT_ORGANIZER_STAFF, 활동 중인 주최자가 "
            + "아니면 403 ORGANIZER_NOT_ACTIVE. 운영진 조작 기록에 남는다.")
    @PostMapping(path = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OrganizerPublicProfileResponse uploadLogo(@CurrentStaff ActingStaff actingStaff,
            @Parameter(description = "로고 그림 — 정사각형 PNG 또는 JPEG") @RequestPart("logo") @OrganizerLogoFile MultipartFile logo)
            throws IOException {
        OrganizerLogoImage image = OrganizerLogoImage.of(logo.getContentType(), logo.getBytes());
        return OrganizerPublicProfileResponse.from(
                uploadOrganizerLogoUseCase.upload(new UploadOrganizerLogoCommand(actingStaff, image)));
    }

    @Operation(summary = "로고 지우기", description = "로고를 지우고, 로고 자리에 다시 이름 첫 글자 + 주최자마다 정해진 색(avatar)이 보인다. 로고 "
            + "파일도 지워져 공개 주소로 열리지 않는다. 운영진 누구나 지운다. 로고가 없으면 아무것도 바꾸지 않고 지금 공개 정보를 돌려준다. 그 주최자의 "
            + "운영진이 아니면 403 NOT_ORGANIZER_STAFF, 활동 중인 주최자가 아니면 403 ORGANIZER_NOT_ACTIVE. 지웠으면 운영진 조작 기록에 남는다.")
    @DeleteMapping("/logo")
    public OrganizerPublicProfileResponse removeLogo(@CurrentStaff ActingStaff actingStaff) {
        return OrganizerPublicProfileResponse.from(
                removeOrganizerLogoUseCase.remove(new RemoveOrganizerLogoCommand(actingStaff)));
    }
}
