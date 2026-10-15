package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.account.AudienceOAuthSession.PendingSignUp;
import com.ticketon.application.account.port.in.SignUpWithOAuthCommand;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.OAuthIdentity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

/**
 * 카카오·구글 가입의 추가 정보 입력 — 제공자가 주지 않는 가입 정보와 동의 항목 넷(docs/01 "가입 경로 — OAuth", docs/13 "추가 정보 입력").
 * <p>
 * 이름·생년월일·전화번호는 늘 받는다(전부 필수 — docs/01 "수집 정보 — 사용자"). 이메일은 제공자가 이메일을 주지 않았거나 확인되지 않은
 * 이메일일 때만 받는다(결과의 pendingSignUp.emailRequired). 제공자가 확인한 이메일이 있으면 이 칸은 보지 않고 그 이메일로 가입한다.
 * 비밀번호는 받지 않는다 — 나중에 비밀번호 찾기로 정할 수 있다.
 */
@Schema(description = "카카오·구글 가입의 추가 정보 입력")
public record OAuthSignUpRequest(
        @Schema(description = "이름", example = "한서윤", requiredMode = REQUIRED)
        @NotBlank(message = "이름을 입력해 주세요.")
        String name,

        @Schema(description = "생년월일", example = "1998-04-02", requiredMode = REQUIRED)
        @NotNull(message = "생년월일을 입력해 주세요.")
        @PastOrPresent(message = "생년월일을 확인해 주세요.")
        LocalDate birthDate,

        @Schema(description = "이메일 — 결과의 pendingSignUp.emailRequired가 true일 때만 필수이고 인증 메일을 이 주소로 보낸다. "
                + "false면 보지 않는다", example = "seoyun@example.com", requiredMode = NOT_REQUIRED)
        @EmailAddressFormat
        String email,

        @Schema(description = "휴대폰 번호. 하이픈·공백은 있어도 된다", example = "010-1234-5678", requiredMode = REQUIRED)
        @NotBlank(message = "전화번호를 입력해 주세요.")
        @PhoneNumberFormat
        String phoneNumber,

        @Schema(description = "서비스 이용약관 동의 (필수)", requiredMode = REQUIRED)
        boolean termsOfServiceAgreed,

        @Schema(description = "개인정보 수집·이용 동의 (필수)", requiredMode = REQUIRED)
        boolean personalInformationCollectionAgreed,

        @Schema(description = "만 14세 이상 확인 (필수)", requiredMode = REQUIRED)
        boolean minimumAgeConfirmed,

        @Schema(description = "마케팅 수신 동의 (선택)", requiredMode = REQUIRED)
        boolean marketingAgreed) {

    static final String EMAIL_REQUIRED_MESSAGE = "이메일을 입력해 주세요.";

    /**
     * 제공자가 확인한 이메일이 없는데 이메일 칸이 비었으면 입력 오류(400)로 막는다 — 다른 칸의 입력 검증과 같은 모양으로.
     *
     * @throws BindException 이메일을 받아야 하는데 비었으면
     */
    void requireEmailIfNeeded(PendingSignUp pendingSignUp) throws BindException {
        if (!pendingSignUp.emailVerified() && (email == null || email.isBlank())) {
            BeanPropertyBindingResult errors = new BeanPropertyBindingResult(this, "oAuthSignUpRequest");
            errors.addError(new FieldError(errors.getObjectName(), "email", EMAIL_REQUIRED_MESSAGE));
            throw new BindException(errors);
        }
    }

    /** 추가 정보 입력 대기의 제공자 계정과 함께 가입 입력으로 바꾼다. 제공자가 확인한 이메일이 있으면 그 이메일로 가입한다. */
    SignUpWithOAuthCommand toCommand(PendingSignUp pendingSignUp) {
        String signUpEmail = pendingSignUp.emailVerified() ? pendingSignUp.email() : email;
        return new SignUpWithOAuthCommand(
                new OAuthIdentity(pendingSignUp.provider().toProvider(), pendingSignUp.providerUserId()),
                AudiencePersonalInformation.of(name, birthDate, signUpEmail, phoneNumber), pendingSignUp.emailVerified(),
                SignupConsentItems.choicesOf(termsOfServiceAgreed, personalInformationCollectionAgreed, minimumAgeConfirmed,
                        marketingAgreed));
    }
}
