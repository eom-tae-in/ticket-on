package com.ticketon.api.admin;

import com.ticketon.application.admin.port.in.GetAdminWorkQueueUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 처리 목록 — 관리자 영역(/api/admin)이라 관리자·최고 관리자로 로그인해야 열린다(api/security). 관리자는 알림 대신 이 목록으로 일을
 * 받는다(docs/12 "관리자 대상").
 */
@RestController
@Tag(name = "관리자 처리 목록", description = "처리할 요청이 한곳에 쌓이는 목록 (docs/13 \"5. 플랫폼 관리자 — 처리 목록\")")
public class AdminWorkQueueController {

    private final GetAdminWorkQueueUseCase getAdminWorkQueueUseCase;

    public AdminWorkQueueController(GetAdminWorkQueueUseCase getAdminWorkQueueUseCase) {
        this.getAdminWorkQueueUseCase = getAdminWorkQueueUseCase;
    }

    @Operation(summary = "처리 목록", description = "아직 처리하지 않은 요청을 접수 순서로 돌려준다. 지금 쌓이는 종류는 주최자 신청(심사 중)뿐이고, "
            + "심사 목표일이 다가온 신청은 highlighted가 true다. 승인·반려한 신청은 목록에서 빠진다. "
            + "로그인하지 않았으면 401, 관리자가 아닌 계정이면 403이다.")
    @GetMapping("/api/admin/work-queue")
    public AdminWorkQueueResponse workQueue() {
        return AdminWorkQueueResponse.from(getAdminWorkQueueUseCase.getWorkQueue());
    }
}
