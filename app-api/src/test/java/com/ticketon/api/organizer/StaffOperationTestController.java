package com.ticketon.api.organizer;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.organizer.StaffAction;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시험 전용 컨트롤러 — 주최 관리 기능이 쓸 공통 방식 그대로 운영진 · 주최자를 받아({@link CurrentStaff}) 시험 조작
 * ({@link StaffOperationTestService})을 부르고, 확인한 역할을 돌려준다. 아직 운영진 조작을 하는 실제 기능이 없어(운영자 초대 #25 등) 이것으로
 * 권한 확인과 조작 기록을 끝까지 돌린다.
 * <p>
 * {@link TestComponent}라 컴포넌트 스캔에 잡히지 않는다. 쓰는 테스트가 {@code @Import}로 넣는다.
 */
@TestComponent
@RestController
class StaffOperationTestController {

    private final StaffOperationTestService operationService;

    StaffOperationTestController(StaffOperationTestService operationService) {
        this.operationService = operationService;
    }

    /**
     * @param failAfterRecording 기록을 남긴 뒤 조작이 실패하게 한다 — 기록도 함께 되돌려지는지 본다
     */
    @PostMapping("/api/organizer/organizers/{organizerId}/test-operations/{action}")
    String operate(@CurrentStaff ActingStaff actingStaff, @PathVariable StaffAction action,
            @RequestParam(defaultValue = "false") boolean failAfterRecording) {
        return operationService.operate(actingStaff, action, failAfterRecording).name();
    }
}
