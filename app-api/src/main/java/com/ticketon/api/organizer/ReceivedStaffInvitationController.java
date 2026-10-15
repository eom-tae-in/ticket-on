package com.ticketon.api.organizer;

import com.ticketon.api.security.CurrentAccount;
import com.ticketon.application.organizer.port.in.AcceptStaffInvitationUseCase;
import com.ticketon.application.organizer.port.in.AnswerStaffInvitationCommand;
import com.ticketon.application.organizer.port.in.DeclineStaffInvitationUseCase;
import com.ticketon.domain.account.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 받은 운영자 초대에 답하기 — 운영진 영역(/api/organizer)이라 운영진으로 로그인해야 열린다(api/security). 받는 사람은 아직 그 주최자의
 * 운영진이 아니므로 주최 관리 경로({@code /api/organizer/organizers/{organizerId}/**})가 아니다. 받은 초대 목록은 내 주최 홈이 돌려준다.
 */
@RestController
@RequestMapping("/api/organizer/staff-invitations/{invitationId}")
@Tag(name = "받은 운영자 초대", description = "받은 운영자 초대의 수락 · 거절 (docs/01 \"운영자 초대\")")
public class ReceivedStaffInvitationController {

    private static final String INVALID_ID_MESSAGE = "형식이 올바르지 않습니다.";

    private final AcceptStaffInvitationUseCase acceptStaffInvitationUseCase;
    private final DeclineStaffInvitationUseCase declineStaffInvitationUseCase;

    public ReceivedStaffInvitationController(AcceptStaffInvitationUseCase acceptStaffInvitationUseCase,
            DeclineStaffInvitationUseCase declineStaffInvitationUseCase) {
        this.acceptStaffInvitationUseCase = acceptStaffInvitationUseCase;
        this.declineStaffInvitationUseCase = declineStaffInvitationUseCase;
    }

    @Operation(summary = "초대 수락", description = "초대받은 이메일의 운영진 로그인으로 수락하면 바로 그 주최자의 운영자가 된다(심사 없음). "
            + "없는 초대이거나 로그인한 운영진의 이메일로 온 초대가 아니면 404 STAFF_INVITATION_NOT_FOUND, 이미 답한 초대면(같은 초대를 "
            + "동시에 두 번 수락한 늦은 쪽 포함) 409 STAFF_INVITATION_ALREADY_ANSWERED, 유효 기간이 지났으면 409 STAFF_INVITATION_EXPIRED, "
            + "초대한 주최자가 활동 중이 아니면 409 ORGANIZER_NOT_ACTIVE, 이미 그 주최자의 운영진이면 409 ALREADY_ORGANIZER_STAFF. "
            + "운영진 조작 기록에 운영자로서 남는다.")
    @PostMapping("/acceptance")
    public AcceptStaffInvitationResponse accept(@CurrentAccount AccountRef account,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long invitationId) {
        return AcceptStaffInvitationResponse.from(
                acceptStaffInvitationUseCase.accept(new AnswerStaffInvitationCommand(account, invitationId)));
    }

    @Operation(summary = "초대 거절", description = "받은 초대를 거절한다. 그 주최자에는 아무것도 바뀌지 않고 받은 초대에서 빠진다. 없는 초대이거나 "
            + "로그인한 운영진의 이메일로 온 초대가 아니면 404 STAFF_INVITATION_NOT_FOUND, 이미 답한 초대면 409 "
            + "STAFF_INVITATION_ALREADY_ANSWERED, 유효 기간이 지났으면 409 STAFF_INVITATION_EXPIRED.")
    @PostMapping("/declination")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@CurrentAccount AccountRef account,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long invitationId) {
        declineStaffInvitationUseCase.decline(new AnswerStaffInvitationCommand(account, invitationId));
    }
}
