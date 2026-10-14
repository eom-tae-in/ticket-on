package com.ticketon.api.account;

import com.ticketon.api.config.DetachedExecution;
import com.ticketon.application.account.port.in.RequestPasswordResetCommand;
import com.ticketon.application.account.port.in.RequestPasswordResetUseCase;
import com.ticketon.application.account.port.in.ResetPasswordUseCase;
import com.ticketon.domain.account.AccountKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비밀번호 찾기·재설정 — 로그인 없이 여는 /api/auth 아래에 둔다(api/security). 관객/주최자 중 어느 쪽인지는 경로로 고른다
 * (docs/13 "비밀번호 찾기 ※ 관객 / 주최자 중 고름").
 * <p>
 * <b>비밀번호 찾기는 가입 여부와 상관없이 같은 응답이다</b>(docs/01 "같은 이메일로 양쪽 가입", docs/99 D "비밀번호 찾기 안내").
 * 상태 코드·본문이 같을 뿐 아니라 걸리는 시간도 같게, 유스케이스를 응답과 떼어 부른다({@link DetachedExecution}) — 계정이 있을 때만
 * 링크 저장과 메일 발송을 하므로 요청 스레드에서 부르면 시간으로 드러난다. 유스케이스가 실패해도(메일 발송 실패 등) 응답은 같고 로그에만 남는다.
 * 입력 형식이 틀린 경우(400)만 응답이 다르다 — 적은 값만 보고 정하므로 가입 여부와 상관없다.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "비밀번호 찾기", description = "관객/주최자 중 골라 비밀번호 찾기·재설정 — 가입 여부와 상관없이 같은 안내 (docs/01 \"같은 이메일로 양쪽 가입\")")
public class PasswordResetController {

    private static final String SAME_ANSWER = "가입 여부·계정 상태와 상관없이 언제나 본문 없는 202다(같은 안내 — 화면은 \"입력한 이메일로 안내를 보냈습니다\"를 "
            + "보여 준다, docs/01). 그 이메일로 가입한 로그인할 수 있는 계정이 있을 때만 재설정 링크 메일이 간다. ";

    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final DetachedExecution detachedExecution;

    public PasswordResetController(RequestPasswordResetUseCase requestPasswordResetUseCase,
            ResetPasswordUseCase resetPasswordUseCase, DetachedExecution detachedExecution) {
        this.requestPasswordResetUseCase = requestPasswordResetUseCase;
        this.resetPasswordUseCase = resetPasswordUseCase;
        this.detachedExecution = detachedExecution;
    }

    @Operation(summary = "관객 비밀번호 찾기", description = SAME_ANSWER
            + "관객 계정만 본다 — 같은 이메일의 운영진 로그인이 있어도 그쪽으로는 보내지 않는다. 링크는 관객 화면의 /reset-password를 연다.")
    @PostMapping("/audience/password-reset-request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestAudiencePasswordReset(@Valid @RequestBody RequestPasswordResetRequest request) {
        requestDetached(request.toCommand(AccountKind.AUDIENCE));
    }

    @Operation(summary = "주최자 비밀번호 찾기", description = SAME_ANSWER
            + "운영진 로그인만 본다 — 같은 이메일의 관객 계정이 있어도 그쪽으로는 보내지 않는다. 메일 제목에 [주최]가 붙고, 링크는 주최 화면의 "
            + "/organizer/reset-password를 연다.")
    @PostMapping("/staff/password-reset-request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestStaffPasswordReset(@Valid @RequestBody RequestPasswordResetRequest request) {
        requestDetached(request.toCommand(AccountKind.STAFF));
    }

    @Operation(summary = "관객 비밀번호 재설정", description = "관객 재설정 메일 링크의 token과 새 비밀번호로 비밀번호를 바꾼다. 링크는 한 번만 쓴다. "
            + "발급한 적 없는 링크(주최자 쪽 링크 포함)는 404 PASSWORD_RESET_LINK_NOT_FOUND, 이미 쓴 링크·새 링크로 대체된 링크·유효 시간이 "
            + "지난 링크는 409. 로그인은 되지 않는다 — 새 비밀번호로 로그인한다.")
    @PostMapping("/audience/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetAudiencePassword(@Valid @RequestBody ResetPasswordRequest request) {
        resetPasswordUseCase.resetPassword(request.toCommand(AccountKind.AUDIENCE));
    }

    @Operation(summary = "주최자 비밀번호 재설정", description = "주최자 재설정 메일 링크의 token과 새 비밀번호로 운영진 로그인의 비밀번호를 바꾼다. "
            + "링크는 한 번만 쓴다. 발급한 적 없는 링크(관객 쪽 링크 포함)는 404 PASSWORD_RESET_LINK_NOT_FOUND, 이미 쓴 링크·새 링크로 대체된 "
            + "링크·유효 시간이 지난 링크는 409.")
    @PostMapping("/staff/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetStaffPassword(@Valid @RequestBody ResetPasswordRequest request) {
        resetPasswordUseCase.resetPassword(request.toCommand(AccountKind.STAFF));
    }

    /** 비밀번호 찾기를 응답과 떼어 부른다. 로그에 남을 일 이름에는 이메일을 넣지 않는다. */
    private void requestDetached(RequestPasswordResetCommand command) {
        detachedExecution.run("비밀번호 찾기 — " + command.accountKind(),
                () -> requestPasswordResetUseCase.requestReset(command));
    }
}
