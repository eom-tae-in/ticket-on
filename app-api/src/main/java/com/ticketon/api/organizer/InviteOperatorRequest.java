package com.ticketon.api.organizer;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.account.EmailAddressFormat;
import com.ticketon.api.validation.FilledText;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.InviteOperatorCommand;
import com.ticketon.domain.account.EmailAddress;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영자 초대 요청 — 초대받을 이메일 하나(docs/01 "운영자 초대 — 소유자가 이메일로 초대").
 * <p>
 * 이메일이 비었는지는 도메인 {@link EmailAddress}와 같이 앞뒤 공백을 {@link String#strip()}으로 걷어 본다({@link FilledText}).
 *
 * @param email 초대받을 이메일
 */
@Schema(description = "운영자 초대 요청")
public record InviteOperatorRequest(
        @Schema(description = "초대받을 이메일 — 받은 사람은 이 이메일의 주최자 쪽 로그인으로 수락한다. 아직 주최자 쪽 로그인이 없어도 된다",
                example = "dohyun@example.com", requiredMode = REQUIRED)
        @FilledText(message = "이메일을 입력해 주세요.")
        @EmailAddressFormat
        String email) {

    InviteOperatorCommand toCommand(ActingStaff actingStaff) {
        return new InviteOperatorCommand(actingStaff, EmailAddress.of(email));
    }
}
