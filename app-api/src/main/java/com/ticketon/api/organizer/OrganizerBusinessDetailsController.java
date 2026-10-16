package com.ticketon.api.organizer;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.GetOrganizerBusinessDetailsQuery;
import com.ticketon.application.organizer.port.in.GetOrganizerBusinessDetailsUseCase;
import com.ticketon.application.organizer.port.in.RequestOrganizerChangeUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사업자 정보 · 정산 계좌 — 주최 관리 경로 규약({@code /api/organizer/organizers/{organizerId}/**}, {@link CurrentStaff})을 따른다. 소유자만
 * 보고 요청하며, 바꾸는 것은 관리자가 확인한 뒤에야 적용된다(docs/01 "가로채기를 막는 장치", "판매자 정보", docs/11 "정산 계좌 변경", docs/13
 * "주최자 정보 · 사업자 정보 · 정산 계좌 ※ 소유자만"). 소속 · 역할 · 주최자 상태는 유스케이스가 운영진 권한 확인으로 본다.
 */
@RestController
@RequestMapping("/api/organizer/organizers/{organizerId}")
@Tag(name = "사업자 정보 · 정산 계좌", description = "사업자 정보 · 정산 계좌 보기와 변경 요청 — 소유자만, 관리자 확인 뒤 적용 (docs/01 "
        + "\"가로채기를 막는 장치\", \"판매자 정보\", docs/11 \"정산 계좌 변경\")")
public class OrganizerBusinessDetailsController {

    private static final String PERMISSION_ERRORS = "운영자면 403 OWNER_ONLY_ACTION, 그 주최자에 지금 소속된 운영진이 아니면(없는 주최자도) "
            + "403 NOT_ORGANIZER_STAFF, 활동 중인 주최자가 아니면 403 ORGANIZER_NOT_ACTIVE.";

    private final GetOrganizerBusinessDetailsUseCase getBusinessDetailsUseCase;
    private final RequestOrganizerChangeUseCase requestOrganizerChangeUseCase;

    public OrganizerBusinessDetailsController(GetOrganizerBusinessDetailsUseCase getBusinessDetailsUseCase,
            RequestOrganizerChangeUseCase requestOrganizerChangeUseCase) {
        this.getBusinessDetailsUseCase = getBusinessDetailsUseCase;
        this.requestOrganizerChangeUseCase = requestOrganizerChangeUseCase;
    }

    @Operation(summary = "사업자 정보 · 정산 계좌 보기", description = "지금 사업자 여부 · 사업자 정보 · 정산 계좌와, 관리자가 확인 중인 변경 요청을 "
            + "함께 돌려준다. 확인 중에는 지금 값이 그대로 쓰인다. 정산 계좌번호는 마지막 숫자 네 개만 보인다. 소유자만 본다. " + PERMISSION_ERRORS)
    @GetMapping("/business-details")
    public OrganizerBusinessDetailsResponse businessDetails(@CurrentStaff ActingStaff actingStaff) {
        return OrganizerBusinessDetailsResponse.from(
                getBusinessDetailsUseCase.get(new GetOrganizerBusinessDetailsQuery(actingStaff)));
    }

    @Operation(summary = "정산 계좌 변경 요청", description = "새 정산 계좌로 바꿔 달라고 요청한다. 관리자 처리 목록에 '정산 계좌 · 사업자 정보 "
            + "변경 확인'으로 쌓이고, 관리자가 승인한 뒤에야 바뀐다 — 그때까지 지금 계좌가 쓰인다. 결과는 소유자에게 메일([주최])로 간다. 운영진 조작 "
            + "기록에 남는다(계좌번호는 남기지 않는다). 소유자만 요청한다. 빈 칸이 있으면 400. 확인 중인 정산 계좌 변경이 있으면 409 "
            + "ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW, 지금 계좌와 같으면 409 ORGANIZER_CHANGE_SAME_AS_CURRENT. " + PERMISSION_ERRORS)
    @PostMapping("/settlement-account-change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizerChangeRequestResponse requestSettlementAccountChange(@CurrentStaff ActingStaff actingStaff,
            @Valid @RequestBody ChangeSettlementAccountRequest request) {
        return OrganizerChangeRequestResponse.from(requestOrganizerChangeUseCase.request(request.toCommand(actingStaff)));
    }

    @Operation(summary = "사업자 정보 변경 요청", description = "새 사업자 정보(다섯 가지 모두)로 바꿔 달라고 요청한다. 관리자 처리 목록에 '정산 "
            + "계좌 · 사업자 정보 변경 확인'으로 쌓이고, 관리자가 승인한 뒤에야 바뀐다 — 그때까지 지금 사업자 정보가 판매자 정보에 나간다. 결과는 "
            + "소유자에게 메일([주최])로 간다. 운영진 조작 기록에 남는다. 사업자 주최자의 소유자만 요청한다. 빈 칸이 있거나 사업자등록번호가 숫자 "
            + "10자리가 아니면 400. 비사업자 주최자면 409 NON_BUSINESS_ORGANIZER_HAS_NO_BUSINESS_INFORMATION(사업자 여부를 바꾸는 기능은 없다), "
            + "확인 중인 사업자 정보 변경이 있으면 409 ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW, 지금 사업자 정보와 같으면 409 "
            + "ORGANIZER_CHANGE_SAME_AS_CURRENT. " + PERMISSION_ERRORS)
    @PostMapping("/business-information-change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizerChangeRequestResponse requestBusinessInformationChange(@CurrentStaff ActingStaff actingStaff,
            @Valid @RequestBody ChangeBusinessInformationRequest request) {
        return OrganizerChangeRequestResponse.from(requestOrganizerChangeUseCase.request(request.toCommand(actingStaff)));
    }
}
