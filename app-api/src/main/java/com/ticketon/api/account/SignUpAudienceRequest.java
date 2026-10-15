package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.account.port.in.SignUpAudienceCommand;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.NewPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

/**
 * 관객 직접 가입 요청 — 가입 정보 넷(전부 필수, docs/01 "수집 정보 — 사용자"), 비밀번호, 동의 항목 넷(docs/01 "약관과 동의 — 가입 시").
 * <p>
 * 동의는 항목마다 따로 받는다. 화면의 "필수 항목 모두 동의" 체크박스는 필수 셋만 한 번에 체크하고 마케팅 수신은 묶지 않는다.
 * 필수 동의가 빠졌는지는 가입 규칙이 본다(409 SIGNUP_REQUIRED_CONSENT_MISSING).
 */
@Schema(description = "관객 직접 가입 요청")
public record SignUpAudienceRequest(
        @Schema(description = "이름", example = "한서윤", requiredMode = REQUIRED)
        @NotBlank(message = "이름을 입력해 주세요.")
        String name,

        @Schema(description = "생년월일", example = "1998-04-02", requiredMode = REQUIRED)
        @NotNull(message = "생년월일을 입력해 주세요.")
        @PastOrPresent(message = "생년월일을 확인해 주세요.")
        LocalDate birthDate,

        @Schema(description = "이메일 — 로그인 아이디. 인증 링크를 이 주소로 보낸다", example = "seoyun@example.com",
                requiredMode = REQUIRED)
        @NotBlank(message = "이메일을 입력해 주세요.")
        @EmailAddressFormat
        String email,

        @Schema(description = "휴대폰 번호. 하이픈·공백은 있어도 된다", example = "010-1234-5678", requiredMode = REQUIRED)
        @NotBlank(message = "전화번호를 입력해 주세요.")
        @PhoneNumberFormat
        String phoneNumber,

        @Schema(description = "비밀번호", requiredMode = REQUIRED)
        @NotNull(message = "비밀번호를 입력해 주세요.")
        @NewPasswordRule
        String password,

        @Schema(description = "서비스 이용약관 동의 (필수)", requiredMode = REQUIRED)
        boolean termsOfServiceAgreed,

        @Schema(description = "개인정보 수집·이용 동의 (필수)", requiredMode = REQUIRED)
        boolean personalInformationCollectionAgreed,

        @Schema(description = "만 14세 이상 확인 (필수)", requiredMode = REQUIRED)
        boolean minimumAgeConfirmed,

        @Schema(description = "마케팅 수신 동의 (선택)", requiredMode = REQUIRED)
        boolean marketingAgreed) {

    SignUpAudienceCommand toCommand() {
        return new SignUpAudienceCommand(AudiencePersonalInformation.of(name, birthDate, email, phoneNumber),
                new NewPassword(password), SignupConsentItems.choicesOf(termsOfServiceAgreed,
                        personalInformationCollectionAgreed, minimumAgeConfirmed, marketingAgreed));
    }

    @Override
    public String toString() {
        return "SignUpAudienceRequest[email=" + email + ", password=****]";
    }
}
