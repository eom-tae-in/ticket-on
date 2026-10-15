package com.ticketon.api.organizer;

import com.ticketon.application.organizer.port.in.GetStaffPublicProfileQuery;
import com.ticketon.application.organizer.port.in.GetStaffPublicProfileUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영진 프로필 — 공개 영역(/api/public)이라 로그인 없이 누구나 본다(api/security, docs/13 "1. 공개 영역 — 운영진 프로필"). 주최자 페이지의
 * 운영진 닉네임을 누르면 이 프로필로 온다. 운영진 계정 id로 가리킨다 — 닉네임은 바뀔 수 있어 주소로 쓰지 않는다.
 */
@RestController
@RequestMapping("/api/public/staff-profiles/{staffAccountId}")
@Tag(name = "운영진 프로필", description = "운영진의 공개 페이지 — 닉네임 · 한 줄 소개 · 참여한 주최자 (docs/01 \"공개 페이지 — 운영진 프로필\")")
public class StaffPublicProfileController {

    private static final String INVALID_ID_MESSAGE = "형식이 올바르지 않습니다.";

    private final GetStaffPublicProfileUseCase getStaffPublicProfileUseCase;

    public StaffPublicProfileController(GetStaffPublicProfileUseCase getStaffPublicProfileUseCase) {
        this.getStaffPublicProfileUseCase = getStaffPublicProfileUseCase;
    }

    @Operation(summary = "운영진 프로필 조회", description = "로그인 없이 누구나 본다. 닉네임 · 한 줄 소개 · 첫 글자 표시 · 참여한 주최자(주최자명 · "
            + "역할 · 기간, 나왔거나 닫힌 주최자는 활동 종료)를 돌려준다. 실명 · 이메일 · 관객으로서 예매한 내역은 담지 않는다. 영구 정지된 주최자는 "
            + "참여한 주최자에서 빠진다. 운영진(소유자 · 운영자)이 되면 자동으로 생기므로, 없는 계정 · 주최자에 소속된 적이 없는 운영진 로그인 · "
            + "탈퇴한 운영진 로그인은 404 STAFF_PROFILE_NOT_FOUND. 관객 · 관리자 · 검표 계정에는 프로필이 없다.")
    @GetMapping
    public StaffPublicProfileResponse getProfile(
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long staffAccountId) {
        return StaffPublicProfileResponse.from(
                getStaffPublicProfileUseCase.getProfile(new GetStaffPublicProfileQuery(staffAccountId)));
    }
}
