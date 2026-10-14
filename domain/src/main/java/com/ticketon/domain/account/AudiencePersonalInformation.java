package com.ticketon.domain.account;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 관객 가입 정보 — 이름·생년월일·이메일·전화번호 넷.
 * <p>
 * <b>전부 필수이고, 이 넷 외에는 받지 않는다</b>(docs/01 "수집 정보 — 사용자", docs/99 B "가입 시 선택 항목").
 * 그래서 칸을 늘리지 않고, 비워 둘 수 있는 칸도 두지 않는다. 성별·닉네임은 받지 않는다(docs/99 B "성별 수집", docs/01).
 * 마케팅 수신 동의는 정보가 아니라 동의 항목이라 여기 없다({@link ConsentItem}).
 * <p>
 * 본인확인을 하지 않으므로 넷 다 관객이 직접 적은 값이고 검증되지 않는다(docs/01 "본인확인은 하지 않습니다").
 * 각 정보를 어디에 쓰는지는 {@link PersonalInformationItem}에 있다.
 *
 * @param name        이름. 앞뒤 공백을 걷는다.
 * @param birthDate   생년월일
 * @param email       이메일
 * @param phoneNumber 전화번호
 */
public record AudiencePersonalInformation(String name, LocalDate birthDate, EmailAddress email, PhoneNumber phoneNumber) {

    public AudiencePersonalInformation {
        Objects.requireNonNull(name, "name");
        name = name.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("이름이 비어 있습니다");
        }
        Objects.requireNonNull(birthDate, "birthDate");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(phoneNumber, "phoneNumber");
    }

    /**
     * 관객이 적은 값으로 만든다. 이메일은 앞뒤 공백을 걷고, 전화번호는 숫자만 남긴다.
     *
     * @throws IllegalArgumentException 이름·이메일·전화번호 중 하나라도 비었거나 형식이 맞지 않으면
     * @throws NullPointerException     넷 중 하나라도 없으면
     */
    public static AudiencePersonalInformation of(String name, LocalDate birthDate, String email, String phoneNumber) {
        return new AudiencePersonalInformation(name, birthDate, EmailAddress.of(email), PhoneNumber.of(phoneNumber));
    }
}
