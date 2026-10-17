package com.ticketon.api.event;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.CreateEventUseCase;
import com.ticketon.application.event.port.in.GetEventWizardQuery;
import com.ticketon.application.event.port.in.GetEventWizardUseCase;
import com.ticketon.application.event.port.in.SaveEventBasicInformationCommand;
import com.ticketon.application.event.port.in.SaveEventBasicInformationUseCase;
import com.ticketon.application.event.port.in.SaveEventBookingSettingsUseCase;
import com.ticketon.application.event.port.in.UploadEventPosterCommand;
import com.ticketon.application.event.port.in.UploadEventPosterUseCase;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.EventPosterImage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 행사 만들기 위저드 — 주최 관리 경로 규약({@code /api/organizer/organizers/{organizerId}/**}, {@link CurrentStaff})을 따른다. 행사 만들기 ·
 * 수정은 운영진 누구나 하고(docs/01 "권한 두 가지"), 소속 · 주최자 상태 · 행사가 그 주최자의 것인지는 유스케이스가 본다.
 * <p>
 * <b>임시저장은 자동이다</b>(docs/03 "임시저장은 자동으로") — 화면은 단계를 넘길 때마다 그 단계를 저장하고, 나갔다 들어오면 조회해 이어서 한다.
 * 1단계를 넘기면 행사가 처음 만들어진다(빈 초안 없음). 같은 단계를 여러 번 저장해도 안전하다. 1단계의 포스터는 행사를 만든 뒤 따로 올린다.
 */
@RestController
@RequestMapping("/api/organizer/organizers/{organizerId}/events")
@Tag(name = "행사 만들기 위저드", description = "행사 만들기와 위저드 단계 저장 · 조회 — 운영진 누구나, 단계를 넘길 때마다 자동 저장 (docs/03 "
        + "\"행사 만들기 위저드\", docs/13 \"행사 만들기\")")
public class OrganizerEventController {

    private static final String INVALID_ID_MESSAGE = "형식이 올바르지 않습니다.";

    private static final String REJECTIONS = "그 주최자에 지금 소속된 운영진이 아니면(없는 주최자 · 승인 전인 주최자도) 403 NOT_ORGANIZER_STAFF, "
            + "활동 중인 주최자가 아니면 403 ORGANIZER_NOT_ACTIVE.";

    private static final String EVENT_NOT_FOUND = "없거나 지운 행사, 다른 주최자의 행사면 404 EVENT_NOT_FOUND.";

    private final CreateEventUseCase createEventUseCase;
    private final GetEventWizardUseCase getEventWizardUseCase;
    private final SaveEventBasicInformationUseCase saveEventBasicInformationUseCase;
    private final SaveEventBookingSettingsUseCase saveEventBookingSettingsUseCase;
    private final UploadEventPosterUseCase uploadEventPosterUseCase;

    public OrganizerEventController(CreateEventUseCase createEventUseCase, GetEventWizardUseCase getEventWizardUseCase,
            SaveEventBasicInformationUseCase saveEventBasicInformationUseCase,
            SaveEventBookingSettingsUseCase saveEventBookingSettingsUseCase, UploadEventPosterUseCase uploadEventPosterUseCase) {
        this.createEventUseCase = createEventUseCase;
        this.getEventWizardUseCase = getEventWizardUseCase;
        this.saveEventBasicInformationUseCase = saveEventBasicInformationUseCase;
        this.saveEventBookingSettingsUseCase = saveEventBookingSettingsUseCase;
        this.uploadEventPosterUseCase = uploadEventPosterUseCase;
    }

    @Operation(summary = "행사 만들기 (1단계를 넘길 때)", description = "1단계 기본 정보와 함께 행사를 처음 저장하고 201에 위저드 값을 돌려준다 — 빈 "
            + "초안은 없다. 새 행사는 작성 중이고, 추측할 수 없는 공개 주소를 받으며, 5단계는 처음 값(공개, 예매 종료 · 검표 시작 60분 전, 1회 4석, "
            + "기준표 취소 규정 등)으로 시작한다. 진행 단계는 1단계. 운영진 누구나 만든다. 비었거나 너무 긴 칸, http(s)가 아닌 지도 링크는 400. 고른 "
            + "유형의 목록에 없는 카테고리면 409 EVENT_CATEGORY_NOT_IN_TYPE, '기타'인데 장르명이 없으면 409 EVENT_GENRE_NAME_REQUIRED, 정식 "
            + "카테고리인데 장르명을 적었으면 409 EVENT_GENRE_NAME_ONLY_FOR_OTHER. " + REJECTIONS + " 운영진 조작 기록에 남는다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventWizardResponse create(@CurrentStaff ActingStaff actingStaff,
            @Valid @RequestBody EventBasicInformationRequest request) {
        return EventWizardResponse.from(createEventUseCase.create(new CreateEventCommand(actingStaff, request.toItems())));
    }

    @Operation(summary = "위저드 값 보기 (이어서 하기)", description = "저장된 단계의 값과 작성 진행 단계를 돌려준다 — 나갔다 들어오면 이것으로 이어서 "
            + "한다. 5단계를 건너뛰었으면 처음 값이 담긴다. 운영진 누구나 본다. " + REJECTIONS + " " + EVENT_NOT_FOUND)
    @GetMapping("/{eventId}")
    public EventWizardResponse get(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId) {
        return EventWizardResponse.from(getEventWizardUseCase.get(new GetEventWizardQuery(actingStaff, eventId)));
    }

    @Operation(summary = "1단계 저장 (기본 정보)", description = "단계를 넘길 때마다 부르는 자동 임시저장. 보낸 칸으로 기본 정보를 바꾸고 위저드 값을 "
            + "돌려준다. 지금과 같은 값이면 아무것도 바뀌지 않는다(조작 기록 · 마지막 수정 시각도 그대로). 두 사람이 거의 동시에 저장하면 차례로 "
            + "처리된다. 운영진 누구나 저장한다. 입력 오류(400)와 카테고리 오류(409)는 행사 만들기와 같다. 바뀐 항목을 지금 상태에서 고칠 수 없으면 "
            + "409(예매가 열린 뒤 장소 VENUE_CHANGE_NEEDS_ADMIN_APPROVAL 등). " + REJECTIONS + " " + EVENT_NOT_FOUND
            + " 바뀌었으면 운영진 조작 기록에 남는다.")
    @PutMapping("/{eventId}/basic-information")
    public EventWizardResponse saveBasicInformation(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @Valid @RequestBody EventBasicInformationRequest request) {
        return EventWizardResponse.from(saveEventBasicInformationUseCase.save(
                new SaveEventBasicInformationCommand(actingStaff, eventId, request.toItems())));
    }

    @Operation(summary = "1단계 포스터 올리기", description = "화면의 크롭 도구로 3:4 세로로 자른 포스터를 multipart/form-data의 poster 칸으로 "
            + "올리고 위저드 값을 돌려준다(posterUrl에 공개 주소). 처음 올리기와 바꾸기가 같고, 바꾸기 전 포스터의 파일은 지워진다. 파일 이름은 쓰지 "
            + "않고 추측할 수 없는 열쇠로 저장한다. 운영진 누구나 올린다. 빈 파일 · 용량 상한(정책값, 확인 필요) 초과 · PNG · JPEG가 아닌 파일(올린 "
            + "쪽이 적은 형식과 실제 파일 내용을 함께 본다) · 3:4 세로가 아닌 그림(허용 오차 정책값, 확인 필요)은 400(fieldErrors의 poster 칸에 까닭), "
            + "poster 칸이 없으면 400, 파일 올리기 요청의 바깥 울타리를 넘으면 400 INVALID_INPUT. 판매가 끝난 행사는 409 "
            + "EDIT_LOCKED_AFTER_SALES_CLOSED, 지난 행사는 409 EDIT_LOCKED_FOR_PAST_EVENT. " + REJECTIONS + " " + EVENT_NOT_FOUND
            + " 운영진 조작 기록에 남고, 마지막 수정 시각이 바뀐다.")
    @PostMapping(path = "/{eventId}/poster", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EventWizardResponse uploadPoster(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @Parameter(description = "포스터 그림 — 3:4 세로 PNG 또는 JPEG") @RequestPart("poster") @EventPosterFile MultipartFile poster)
            throws IOException {
        EventPosterImage image = EventPosterImage.of(poster.getContentType(), poster.getBytes());
        return EventWizardResponse.from(uploadEventPosterUseCase.upload(new UploadEventPosterCommand(actingStaff, eventId, image)));
    }

    @Operation(summary = "5단계 저장 (공개 수준 · 예매 설정 · 취소 규정)", description = "단계를 넘길 때마다 부르는 자동 임시저장. 5단계는 전부 처음 "
            + "값이 있어 건너뛸 수 있다 — 건너뛰면 부르지 않는다. 보낸 칸으로 공개 수준 · 예매 설정 · 취소 규정을 바꾸고 위저드 값을 돌려준다. 지금과 "
            + "같은 값이면 아무것도 바뀌지 않는다 — 걸려 있던 암호를 다시 적어도 같다. 운영진 누구나 저장한다. 빠졌거나 범위를 벗어난 칸, 관람일 "
            + "당일 구간이 없거나 같은 날 구간이 둘인 취소 규정, 암호 보호가 아닌데 적은 암호는 400. 기준보다 관객에게 불리한 취소 규정은 409 "
            + "CANCELLATION_POLICY_STRICTER_THAN_STANDARD, 암호가 걸린 적 없이 암호 보호로 두면서 암호를 비우면 409 EVENT_PASSWORD_REQUIRED, "
            + "바뀐 항목을 지금 상태에서 고칠 수 없으면 409(예매가 열린 뒤 취소 규정 EDIT_LOCKED_AFTER_BOOKING_OPENED 등). " + REJECTIONS + " "
            + EVENT_NOT_FOUND + " 바뀌었으면 운영진 조작 기록에 남는다.")
    @PutMapping("/{eventId}/booking-settings")
    public EventWizardResponse saveBookingSettings(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @Valid @RequestBody EventBookingSettingsRequest request) {
        return EventWizardResponse.from(saveEventBookingSettingsUseCase.save(request.toCommand(actingStaff, eventId)));
    }
}
