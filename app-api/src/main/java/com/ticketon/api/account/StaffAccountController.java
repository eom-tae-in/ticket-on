package com.ticketon.api.account;

import com.ticketon.api.security.CurrentAccount;
import com.ticketon.api.security.SessionLogin;
import com.ticketon.application.account.port.in.ChangeStaffProfileUseCase;
import com.ticketon.application.account.port.in.GetMyStaffProfileQuery;
import com.ticketon.application.account.port.in.GetMyStaffProfileUseCase;
import com.ticketon.application.account.port.in.WithdrawStaffAccountCommand;
import com.ticketon.application.account.port.in.WithdrawStaffAccountUseCase;
import com.ticketon.domain.account.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 운영진 로그인 계정 — 운영진 영역(/api/organizer)이라 운영진으로 로그인해야 열린다(api/security).
 * <p>
 * 내 프로필(닉네임 · 한 줄 소개)은 한 화면에서 함께 고치므로(docs/13 "내 프로필") 조회와 편집을 한 경로로 둔다. 닉네임만 바꾸던 경로
 * ({@code PUT /api/organizer/account/nickname}, #15)는 이 편집에 합쳤다.
 * <p>
 * 탈퇴하면 유스케이스가 그 계정의 세션을 저장소에서 모두 끊고, 여기서 지금 세션도 로그아웃처럼 끝낸다(세션 쿠키 만료) — 끊긴 세션이 요청 끝에
 * 다시 저장되지 않게.
 */
@RestController
@RequestMapping("/api/organizer/account")
@Tag(name = "운영진 계정", description = "내 프로필(닉네임 · 한 줄 소개) 조회 · 편집, 운영진 로그인 탈퇴 (docs/13 \"내 프로필\", "
        + "docs/99 C \"운영진 닉네임 — 변경 가능\", docs/01 \"운영진 프로필\", \"탈퇴와 주최자 닫기\")")
public class StaffAccountController {

    private final GetMyStaffProfileUseCase getMyStaffProfileUseCase;
    private final ChangeStaffProfileUseCase changeStaffProfileUseCase;
    private final WithdrawStaffAccountUseCase withdrawStaffAccountUseCase;
    private final SessionLogin sessionLogin;

    public StaffAccountController(GetMyStaffProfileUseCase getMyStaffProfileUseCase,
            ChangeStaffProfileUseCase changeStaffProfileUseCase, WithdrawStaffAccountUseCase withdrawStaffAccountUseCase,
            SessionLogin sessionLogin) {
        this.getMyStaffProfileUseCase = getMyStaffProfileUseCase;
        this.changeStaffProfileUseCase = changeStaffProfileUseCase;
        this.withdrawStaffAccountUseCase = withdrawStaffAccountUseCase;
        this.sessionLogin = sessionLogin;
    }

    @Operation(summary = "내 프로필 조회", description = "로그인한 운영진의 닉네임 · 한 줄 소개 · 첫 글자 표시를 돌려준다. 내 프로필 편집 칸을 채울 때 쓴다. "
            + "이름 · 이메일은 담지 않는다. 로그인하지 않았거나 세션이 끝났으면 401, 운영진이 아닌 계정이면 403이다.")
    @GetMapping("/profile")
    public MyStaffProfileResponse getMyProfile(@CurrentAccount AccountRef account) {
        return MyStaffProfileResponse.from(getMyStaffProfileUseCase.getMyProfile(new GetMyStaffProfileQuery(account)));
    }

    @Operation(summary = "내 프로필 편집", description = "로그인한 운영진의 닉네임과 한 줄 소개를 함께 바꾼다. 두 값은 운영진 프로필에 공개된다. 소개를 "
            + "비우거나 보내지 않으면 소개를 지운다. 참여 이력은 그대로 따라간다. 둘 다 지금과 같으면 그대로 돌려준다. 다른 운영진이 쓰는 "
            + "닉네임이면 409 STAFF_NICKNAME_ALREADY_TAKEN이고 소개도 바뀌지 않는다. 닉네임이 비었거나 20자, 소개가 50자를 넘으면 400. "
            + "로그인하지 않았거나 세션이 끝났으면 401, 운영진이 아닌 계정이면 403이다.")
    @PutMapping("/profile")
    public MyStaffProfileResponse changeProfile(@CurrentAccount AccountRef account,
            @Valid @RequestBody ChangeStaffProfileRequest request) {
        return MyStaffProfileResponse.from(changeStaffProfileUseCase.changeProfile(request.toCommand(account)));
    }

    @Operation(summary = "운영진 로그인 탈퇴", description = "로그인한 운영진이 탈퇴한다. 운영자로 소속된 주최자에서는 모두 빠지고(소속 이력은 남는다), "
            + "이름 · 이메일 · 비밀번호는 바로 파기되며 닉네임은 남는다. 그 계정의 로그인 세션은 다른 기기까지 모두 바로 끊긴다. 같은 이메일로 다시 "
            + "가입할 수 있다. 소유자로 남은 주최자가 있으면 409 OWNER_MUST_HAND_OVER_FIRST(먼저 소유자를 넘기거나 주최자를 닫아야 한다), "
            + "이미 탈퇴했으면 409 STAFF_WITHDRAWN, 거의 동시에 소속이 먼저 끝나 저장에서 지면 409 MEMBERSHIP_ALREADY_ENDED. 로그인하지 않았거나 "
            + "세션이 끝났으면 401, 운영진이 아닌 계정이면 403이다. 주최자마다 운영진 조작 기록에 남는다.")
    @PostMapping("/withdrawal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@CurrentAccount AccountRef account, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        withdrawStaffAccountUseCase.withdraw(new WithdrawStaffAccountCommand(account));
        sessionLogin.logOut(httpRequest, httpResponse);
    }
}
