package com.ticketon.api.security;

import com.ticketon.domain.account.AccountRef;
import java.util.Optional;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시험 전용 컨트롤러 — 영역마다 주소 하나를 두고, 들어온 주체의 이름을 그대로 돌려준다.
 * <p>
 * {@link TestComponent}라 컴포넌트 스캔에 잡히지 않는다. 쓰는 테스트가 {@code @Import}로 넣는다.
 */
@TestComponent
@RestController
class AreaTestController {

    static final String ANONYMOUS = "anonymous";

    @GetMapping({"/api/public/test", "/api/auth/test"})
    String publicArea(@CurrentAccount Optional<AccountRef> account) {
        return account.map(AccountRef::principalName).orElse(ANONYMOUS);
    }

    /** 공개 경로라도 로그인 주체를 꼭 받는 인자가 있으면 로그인해야 한다. */
    @GetMapping("/api/public/test/login-required")
    String loginRequiredOnPublicPath(@CurrentAccount AccountRef account) {
        return account.principalName();
    }

    @GetMapping("/api/audience/test")
    String audienceArea(@CurrentAccount AccountRef account) {
        return account.principalName();
    }

    @PostMapping("/api/audience/test")
    String audienceAreaChange(@CurrentAccount AccountRef account) {
        return account.principalName();
    }

    @GetMapping("/api/organizer/test")
    String organizerArea(@CurrentAccount AccountRef account) {
        return account.principalName();
    }

    @GetMapping("/api/admin/test")
    String adminArea(@CurrentAccount AccountRef account) {
        return account.principalName();
    }

    @GetMapping("/api/checkin/test")
    String checkinArea(@CurrentAccount AccountRef account) {
        return account.principalName();
    }

    /** 영역 접두사 밖의 주소 — 새 API를 접두사 없이 만든 실수. */
    @GetMapping("/api/unclassified/test")
    String unclassified() {
        return "unreachable";
    }

    /** {@code @CurrentAccount}를 지원하지 않는 타입에 붙인 실수. */
    @GetMapping("/api/public/test/wrong-type")
    String wrongType(@CurrentAccount Long accountId) {
        return String.valueOf(accountId);
    }
}
