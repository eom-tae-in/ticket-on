package com.ticketon.api.organizer;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.GetStaffListQuery;
import com.ticketon.application.organizer.port.in.GetStaffListUseCase;
import com.ticketon.application.organizer.port.in.InviteOperatorUseCase;
import com.ticketon.application.organizer.port.in.LeaveOrganizerCommand;
import com.ticketon.application.organizer.port.in.LeaveOrganizerUseCase;
import com.ticketon.application.organizer.port.in.RemoveOperatorCommand;
import com.ticketon.application.organizer.port.in.RemoveOperatorUseCase;
import com.ticketon.application.organizer.port.in.SubmitOwnershipClaimUseCase;
import com.ticketon.application.organizer.port.in.TransferOwnershipCommand;
import com.ticketon.application.organizer.port.in.TransferOwnershipUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영진 관리 — 주최 관리 경로 규약({@code /api/organizer/organizers/{organizerId}/**}, {@link CurrentStaff})을 따른다. 소속 · 역할 ·
 * 주최자 상태는 유스케이스가 운영진 권한 확인으로 본다(docs/13 "운영진 관리").
 */
@RestController
@RequestMapping("/api/organizer/organizers/{organizerId}")
@Tag(name = "운영진 관리", description = "운영진 목록 · 운영자 초대 · 내보내기 · 소유자 넘기기 · 소유자 이전 신청 · 이 주최자에서 나가기 "
        + "(docs/01 \"운영자 초대\", \"권한 두 가지\", \"소유자 넘기기\", docs/13 \"운영진 관리\")")
public class StaffManagementController {

    private static final String INVALID_ID_MESSAGE = "형식이 올바르지 않습니다.";

    private final GetStaffListUseCase getStaffListUseCase;
    private final InviteOperatorUseCase inviteOperatorUseCase;
    private final RemoveOperatorUseCase removeOperatorUseCase;
    private final LeaveOrganizerUseCase leaveOrganizerUseCase;
    private final TransferOwnershipUseCase transferOwnershipUseCase;
    private final SubmitOwnershipClaimUseCase submitOwnershipClaimUseCase;

    public StaffManagementController(GetStaffListUseCase getStaffListUseCase, InviteOperatorUseCase inviteOperatorUseCase,
            RemoveOperatorUseCase removeOperatorUseCase, LeaveOrganizerUseCase leaveOrganizerUseCase,
            TransferOwnershipUseCase transferOwnershipUseCase, SubmitOwnershipClaimUseCase submitOwnershipClaimUseCase) {
        this.getStaffListUseCase = getStaffListUseCase;
        this.inviteOperatorUseCase = inviteOperatorUseCase;
        this.removeOperatorUseCase = removeOperatorUseCase;
        this.leaveOrganizerUseCase = leaveOrganizerUseCase;
        this.transferOwnershipUseCase = transferOwnershipUseCase;
        this.submitOwnershipClaimUseCase = submitOwnershipClaimUseCase;
    }

    @Operation(summary = "운영진 목록", description = "그 주최자의 지금 운영진(소유자 · 운영자)을 닉네임 · 역할 · 소속 일시와 함께 돌려준다. "
            + "운영진 누구나 본다. 그 주최자에 지금 소속된 운영진이 아니면(나갔거나 내보내졌으면, 없는 주최자도) 403 NOT_ORGANIZER_STAFF, "
            + "활동 중인 주최자가 아니면 403 ORGANIZER_NOT_ACTIVE.")
    @GetMapping("/staff")
    public StaffListResponse staff(@CurrentStaff ActingStaff actingStaff) {
        return StaffListResponse.from(getStaffListUseCase.getStaffList(new GetStaffListQuery(actingStaff)));
    }

    @Operation(summary = "운영자 초대", description = "소유자가 이메일로 운영자를 초대하고, 그 이메일로 초대 메일([주최])을 보낸다. 초대는 유효 "
            + "기간(정책값, 확인 필요) 동안 수락할 수 있고, 끝나는 일시는 응답의 expiresAt이다. 같은 이메일로 보낸 초대가 유효 기간이 지났으면 그 초대를 다시 열어 보낸다. 운영자가 초대하면 "
            + "403 OWNER_ONLY_ACTION, 그 주최자의 운영진이 아니면 403 NOT_ORGANIZER_STAFF, 그 이메일의 사람이 이미 그 주최자의 운영진이면 "
            + "409 ALREADY_ORGANIZER_STAFF, 같은 이메일로 보낸 초대가 아직 유효하면 409 STAFF_INVITATION_ALREADY_PENDING, 이메일이 "
            + "비었거나 형식이 틀리면 400. 운영진 조작 기록에 남는다.")
    @PostMapping("/staff-invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffInvitationResponse invite(@CurrentStaff ActingStaff actingStaff,
            @Valid @RequestBody InviteOperatorRequest request) {
        return StaffInvitationResponse.from(inviteOperatorUseCase.invite(request.toCommand(actingStaff)));
    }

    @Operation(summary = "운영자 내보내기", description = "소유자가 운영자를 내보낸다. 그 사람의 소속이 끝나고(소속 이력은 남는다) 그 사람에게 "
            + "메일([주최])이 간다. 내보내진 사람은 그 주최자의 주최 관리에 더는 들어가지 못한다. 운영자가 내보내려 하면 403 "
            + "OWNER_ONLY_ACTION, 내보낼 사람이 그 주최자의 운영자가 아니면(소유자 자신 포함) 409 NOT_AN_OPERATOR, 그 사이 그 사람이 먼저 "
            + "나갔으면 409 MEMBERSHIP_ALREADY_ENDED. 운영진 조작 기록에 남는다.")
    @PostMapping("/staff/{staffAccountId}/removal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long staffAccountId) {
        removeOperatorUseCase.remove(new RemoveOperatorCommand(actingStaff, staffAccountId));
    }

    @Operation(summary = "소유자 넘기기", description = "소유자가 그 주최자의 운영자에게 소유자를 넘긴다. 관리자를 거치지 않는다. 받은 사람이 "
            + "소유자가 되고 넘긴 사람은 운영자로 남는다(소유자는 늘 1명). 넘긴 뒤의 운영진 전원에게 '소유자 변경' 메일([주최])이 가고, 관리자 처리 "
            + "목록에 '소유자 변경'(사후 확인)이 쌓인다. 운영자가 넘기려 하면 403 OWNER_ONLY_ACTION, 그 주최자의 운영진이 아니면 403 "
            + "NOT_ORGANIZER_STAFF, 받을 사람이 그 주최자의 운영자가 아니면(소유자 자신 · 바깥 사람 · 나간 사람) 409 "
            + "OWNERSHIP_RECEIVER_NOT_OPERATOR, 그 사이 같은 소유자가 먼저 넘겼으면 409 OWNERSHIP_ALREADY_TRANSFERRED, 받을 사람이 그 사이 "
            + "나갔으면 409 MEMBERSHIP_ALREADY_ENDED. 소유자 변경 이력과 운영진 조작 기록에 남는다.")
    @PostMapping("/staff/{staffAccountId}/ownership-transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transferOwnership(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long staffAccountId) {
        transferOwnershipUseCase.transfer(new TransferOwnershipCommand(actingStaff, staffAccountId));
    }

    @Operation(summary = "소유자 이전 신청", description = "소유자와 연락이 끊겨 넘겨줄 사람이 없을 때 운영자가 관리자에게 소유자 이전을 "
            + "신청한다. 신청은 그때의 소유자를 가리키고 관리자 처리 목록에 '소유자 이전 신청'으로 쌓인다. 관리자가 주최자 신청 때 받은 연락처 · "
            + "SNS 등으로 확인한 뒤 승인하면 신청한 운영자가 소유자가 되고(옛 소유자는 운영자로 남음), 반려하면 사유와 함께 운영진 전원에게 "
            + "메일([주최])이 간다. 소유자면 409 OWNER_CANNOT_CLAIM_OWNERSHIP(직접 넘긴다), 그 주최자의 운영진이 아니면 403 "
            + "NOT_ORGANIZER_STAFF, 활동 중인 주최자가 아니면 403 ORGANIZER_NOT_ACTIVE, 그 주최자에 심사 중인 신청을 이미 냈으면 409 "
            + "OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW, 신청하는 사이 소유자가 다른 사람에게 넘어갔으면 409 OWNERSHIP_ALREADY_TRANSFERRED(다시 "
            + "신청하면 새 소유자를 가리킨다), 사유가 비면 400. 운영진 조작 기록에 남는다.")
    @PostMapping("/ownership-claims")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmitOwnershipClaimResponse submitOwnershipClaim(@CurrentStaff ActingStaff actingStaff,
            @Valid @RequestBody SubmitOwnershipClaimRequest request) {
        return SubmitOwnershipClaimResponse.from(submitOwnershipClaimUseCase.submit(request.toCommand(actingStaff)));
    }

    @Operation(summary = "이 주최자에서 나가기", description = "운영자가 그 주최자에서 나간다. 소속은 끝나도 이력으로 남고, 다른 주최자의 소속은 "
            + "그대로다. 소유자면 409 OWNER_MUST_HAND_OVER_FIRST(먼저 소유자를 넘기거나 주최자를 닫아야 한다), 그 주최자의 운영진이 아니면 "
            + "403 NOT_ORGANIZER_STAFF, 그 사이 소유자가 먼저 내보냈으면 409 MEMBERSHIP_ALREADY_ENDED. 운영진 조작 기록에 남는다.")
    @PostMapping("/departure")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@CurrentStaff ActingStaff actingStaff) {
        leaveOrganizerUseCase.leave(new LeaveOrganizerCommand(actingStaff));
    }
}
