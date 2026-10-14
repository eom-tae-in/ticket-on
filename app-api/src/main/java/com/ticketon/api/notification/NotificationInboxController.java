package com.ticketon.api.notification;

import com.ticketon.api.security.CurrentAccount;
import com.ticketon.application.notification.port.in.OpenNotificationInboxCommand;
import com.ticketon.application.notification.port.in.OpenNotificationInboxUseCase;
import com.ticketon.domain.account.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 알림함 — 관객 영역(/api/audience)이라 관객으로 로그인해야 열린다(api/security).
 * 보낸 알림을 한 벌 더 쌓아 둔 기록 보관 장소다(docs/12 "전달 수단과 알림함").
 */
@RestController
@RequestMapping("/api/audience/notification-inbox")
@Tag(name = "알림함", description = "마이페이지 알림함 (docs/12 \"전달 수단과 알림함\")")
public class NotificationInboxController {

    private final OpenNotificationInboxUseCase openNotificationInboxUseCase;

    public NotificationInboxController(OpenNotificationInboxUseCase openNotificationInboxUseCase) {
        this.openNotificationInboxUseCase = openNotificationInboxUseCase;
    }

    @Operation(summary = "내 알림함 열기", description = "이메일·문자로 보낸 알림을 최신순으로 돌려준다. isNew는 지난번에 알림함을 연 뒤 새로 온 것인지다. "
            + "이 요청으로 처음 본 시각이 채워져, 다음 조회에서는 같은 항목의 isNew가 false다. 안 읽은 개수는 돌려주지 않는다(탭 배지 없음). "
            + "이용 정지 중에도 열린다.")
    @GetMapping
    public NotificationInboxResponse openInbox(@CurrentAccount AccountRef account) {
        return NotificationInboxResponse.from(openNotificationInboxUseCase.open(new OpenNotificationInboxCommand(account)));
    }
}
